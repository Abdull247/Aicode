# 可用工具总览

「设置 → 可用工具」页面列出当前版本 AI 可以使用的全部内置工具：每个工具的用途说明、参数含义与授权方式。它是纯展示页——在这里只能查看，不改变工具行为；工具的实际授权由[工具授权](/guide/permissions)与会话模式（Build / Plan / Ask）控制。

## 页面内容

工具按用途分为五组，点击分组标题可以折叠/展开：

| 分组 | 包含工具（部分） |
| --- | --- |
| 文件 | 读取 / 写入 / 编辑文件、列出目录、搜索代码、发送文件、查看图片 |
| 命令与终端 | 执行命令（Bash）、后台终端、Shizuku 宿主操作 |
| 网络 | 联网搜索、抓取网页、GitHub 读取、GitHub 写入 |
| AI 协作 | 计划模式、任务清单、记忆、加载技能、生成图片、向用户提问、子代理等 |
| 扩展 | MCP 管理、内置浏览器 |

点击任一工具进入详情页，可以看到：

- **工具名与说明**：工具在对话中的标识名（如 `githubWrite`）以及它做什么。
- **授权方式**：「自动放行」表示工具调用不需要确认；「需要确认」表示 Build 模式下每次调用前会弹窗征求同意。Plan / Ask 模式下写入类操作一律被拒绝。
- **参数**：每个参数的名称、类型、是否必填与含义。

## GitHub 集成

「GitHub 读取」（`github`）与「GitHub 写入」（`githubWrite`）两个工具让 AI 直接操作 GitHub：

**读操作**（自动放行）：列出与查看 issue、issue 评论、列出与查看 Pull Request、获取 PR 的统一 diff、查看某个分支 / 提交的 CI 检查结果。

**写操作**（需要确认）：创建 / 更新 / 评论 issue、创建 Pull Request、提交 PR 审查（通过 / 请求修改 / 评论）、合并 PR。

### 首次使用前的准备

1. 在**凭据管理**（Git 面板 →「凭据与署名」）为 `github.com` 保存一条凭据：用户名填你的 GitHub 用户名，Token 填 GitHub Personal Access Token。
2. Token 需要的权限范围（scope）：读操作需要 `repo`（私有仓库）或对应公共仓库的读取权限；写操作（建 issue / 建 PR / 审查 / 合并）需要完整 `repo` scope。
3. 当前工作区的 Git remote 指向 GitHub 仓库时，AI 会自动识别 owner/repo；操作其它仓库时 AI 也可以显式指定仓库名（`owner/repo`）。

Token 只保存在应用私有目录中（与 Git 推拉共用一份凭据），不会出现在对话内容里。

### 常见失败与排查

| 提示 | 原因与处理 |
| --- | --- |
| `NO_TOKEN` | 没有保存 github.com 凭据，按上面步骤添加 |
| `GITHUB_HTTP_401` | Token 无效或已过期，重新生成并更新凭据 |
| `GITHUB_HTTP_403` | Token 权限不足或触发限流；检查 scope，稍后重试 |
| `GITHUB_HTTP_404` | 仓库名不对，或该 Token 无权访问此仓库 |
| `GITHUB_HTTP_422` | 请求内容未通过 GitHub 校验（如缺少必填项），按提示修正 |
| `MISSING_REPO` | 当前工作区没有 GitHub remote，让 AI 显式传入 `repo="owner/repo"` |
