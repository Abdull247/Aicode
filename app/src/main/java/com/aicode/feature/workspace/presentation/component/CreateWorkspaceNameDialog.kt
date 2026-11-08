package com.aicode.feature.workspace.presentation.component

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.aicode.R
import com.aicode.core.theme.Spacing
import com.aicode.core.ui.AppTextField
import com.aicode.core.ui.dialogTextFieldColors

/**
 * 新建(空 / Git)工作区时的名称输入弹窗。
 * 校验非空且不重名,确认后把清洗过的名称回传调用方。
 */
@Composable
internal fun CreateWorkspaceNameDialog(
    existingNames: List<String>,
    onConfirm: (String) -> Unit,
    onDismiss: () -> Unit
) {
    var name by remember { mutableStateOf("") }
    val trimmed = name.trim()
    val duplicate = existingNames.any { it.equals(trimmed, ignoreCase = true) }
    val canConfirm = trimmed.isNotEmpty() && !duplicate

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.workspace_new_workspace)) },
        text = {
            Column {
                AppTextField(
                    value = name,
                    onValueChange = { name = it },
                    singleLine = true,
                    label = stringResource(R.string.common_name),
                    placeholder = stringResource(R.string.workspace_name_hint),
                    isError = duplicate,
                    colors = dialogTextFieldColors()
                )
                if (duplicate) {
                    Spacer(Modifier.height(Spacing.xs))
                    Text(
                        stringResource(R.string.workspace_name_exists),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error
                    )
                }
            }
        },
        confirmButton = {
            TextButton(enabled = canConfirm, onClick = { onConfirm(trimmed) }) {
                Text(stringResource(R.string.common_create))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.common_cancel)) }
        }
    )
}
