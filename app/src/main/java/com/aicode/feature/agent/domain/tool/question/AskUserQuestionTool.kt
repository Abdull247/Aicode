package com.aicode.feature.agent.domain.tool.question

import com.aicode.core.util.FileLogger
import com.aicode.feature.agent.domain.tool.AgentTool
import com.aicode.feature.agent.domain.tool.ParameterType
import com.aicode.feature.agent.domain.tool.ToolParameter
import com.aicode.feature.agent.domain.tool.ToolCapability
import com.aicode.feature.agent.domain.tool.ToolResult
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.boolean
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.util.UUID
import javax.inject.Inject

/**
 * 让 AI 向用户提出结构化的选择题。
 *
 * 当 AI 遇到需要用户决策的场景（选库、选方案、确认环境安装等）时，调用本工具可暂停执行、
 * 在 UI 弹出一个问题面板、等待用户做出选择，然后拿到用户的回答继续工作。
 *
 * 每次调用可同时问 1-4 个问题，每个问题 2-4 个预设选项，支持单选/多选。
 * 用户侧每个问题自动附带一个「其他」选项，选中后可输入自定义文本。
 */
class AskUserQuestionTool @Inject constructor(
    private val questionManager: AskUserQuestionManager
) : AgentTool() {

    private companion object {
        const val TAG = "AskUserQuestionTool"
        const val MAX_QUESTIONS = 4
        const val MIN_OPTIONS = 2
        const val MAX_OPTIONS = 4
    }

    override val name = "askUserQuestion"
    override val capabilities = setOf(ToolCapability.USER_INTERACTION)

    override val description =
        "Ask the user structured multiple-choice questions and wait for answers. Use when a user decision is needed. 1–4 questions per call, 2–4 options each (UI appends an Other option)."

    /** 单个选项的 JSON Schema */
    private val optionItemSchema: Map<String, Any> = mapOf(
        "type" to "object",
        "properties" to mapOf(
            "label" to mapOf(
                "type" to "string",
                "description" to "Option label (short, 1–5 words), e.g. \"OkHttp (Recommended)\"."
            ),
            "description" to mapOf(
                "type" to "string",
                "description" to "Option explanation: what choosing it means, and any trade-offs."
            )
        ),
        "required" to listOf("label", "description")
    )

    /** 单个问题的 JSON Schema */
    private val questionItemSchema: Map<String, Any> = mapOf(
        "type" to "object",
        "properties" to mapOf(
            "question" to mapOf(
                "type" to "string",
                "description" to "Full question text; clear, specific, ending with a question mark."
            ),
            "header" to mapOf(
                "type" to "string",
                "description" to "Short header (≤12 chars), shown as a tab label, e.g. \"Approach\", \"Library\", \"Tool\"."
            ),
            "options" to mapOf(
                "type" to "array",
                "description" to "2–4 preset options. The UI appends an Other option for free text. " +
                    "If you have a recommendation, put it first and append \" (Recommended)\" to the label.",
                "items" to optionItemSchema
            ),
            "multiSelect" to mapOf(
                "type" to "boolean",
                "description" to "Allow multiple selections. Default false (single choice). Word the question accordingly if true."
            )
        ),
        "required" to listOf("question", "header", "options")
    )

    override val parameters = mapOf(
        "questions" to ToolParameter(
            name = "questions",
            type = ParameterType.ARRAY,
            description = "Questions to ask (1–4). Each has question, header, options, and optional multiSelect.",
            required = true,
            itemsSchema = questionItemSchema
        )
    )

    override suspend fun execute(args: Map<String, JsonElement>): ToolResult {
        val questionsJson = args["questions"]?.jsonArray
        if (questionsJson == null || questionsJson.isEmpty()) {
            return ToolResult.Error("Missing required parameter: questions (at least one question)", "MISSING_QUESTIONS")
        }

        if (questionsJson.size > MAX_QUESTIONS) {
            return ToolResult.Error(
                "At most $MAX_QUESTIONS questions per call, got ${questionsJson.size}",
                "TOO_MANY_QUESTIONS"
            )
        }

        // 解析每个问题
        val questions = mutableListOf<QuestionItem>()
        for ((idx, qElement) in questionsJson.withIndex()) {
            val qObj = qElement.jsonObject

            val questionText = qObj["question"]?.jsonPrimitive?.contentOrNull?.trim()
            if (questionText.isNullOrEmpty()) {
                return ToolResult.Error("Question ${idx + 1} is missing the question field", "INVALID_QUESTION")
            }

            val header = qObj["header"]?.jsonPrimitive?.contentOrNull?.trim() ?: ""

            val optionsJson = qObj["options"]?.jsonArray
            if (optionsJson == null || optionsJson.size < MIN_OPTIONS) {
                return ToolResult.Error(
                    "Question ${idx + 1} needs at least $MIN_OPTIONS options",
                    "TOO_FEW_OPTIONS"
                )
            }
            if (optionsJson.size > MAX_OPTIONS) {
                return ToolResult.Error(
                    "Question ${idx + 1} allows at most $MAX_OPTIONS options, got ${optionsJson.size}",
                    "TOO_MANY_OPTIONS"
                )
            }

            val options = optionsJson.map { optElement ->
                val optObj = optElement.jsonObject
                QuestionOption(
                    label = optObj["label"]?.jsonPrimitive?.contentOrNull?.trim() ?: "",
                    description = optObj["description"]?.jsonPrimitive?.contentOrNull?.trim() ?: ""
                )
            }.filter { it.label.isNotEmpty() }

            if (options.size < MIN_OPTIONS) {
                return ToolResult.Error(
                    "Question ${idx + 1} has fewer than $MIN_OPTIONS valid options",
                    "INVALID_OPTIONS"
                )
            }

            val multiSelect = qObj["multiSelect"]?.jsonPrimitive?.let {
                runCatching { it.boolean }.getOrNull()
            } ?: false

            questions.add(
                QuestionItem(
                    question = questionText,
                    header = header,
                    options = options,
                    multiSelect = multiSelect
                )
            )
        }

        val requestId = UUID.randomUUID().toString()
        val pending = PendingUserQuestion(id = requestId, questions = questions)

        FileLogger.d(TAG, "ask_user_question 发起: ${questions.size} 个问题，等待用户回答...")

        // 挂起，等待用户在 UI 做出选择
        val answer = questionManager.awaitAnswer(pending)

        FileLogger.d(TAG, "ask_user_question 用户已回答: ${answer.answers.size} 条")

        // 将用户回答序列化为文本，喂回给模型
        val resultText = buildString {
            if (answer.answers.isEmpty()) {
                append("The user did not pick a preset option and will add more detail. Continue from their follow-up.")
            } else {
                for (a in answer.answers) {
                    append("\"${a.question}\" = ")
                    val parts = mutableListOf<String>()
                    if (a.selected.isNotEmpty()) {
                        parts.addAll(a.selected)
                    }
                    if (a.customText != null) {
                        parts.add("Other: ${a.customText}")
                    }
                    if (parts.isEmpty()) {
                        append("(none selected)")
                    } else {
                        append(parts.joinToString(", "))
                    }
                    append("\n")
                }
            }
        }.trim()

        return ToolResult.Success(JsonPrimitive(resultText))
    }
}
