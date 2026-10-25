# Available Tools

The "Settings → Available Tools" page lists every built-in tool the AI can use in the
current version: what each tool does, its parameters, and how it is authorized. It is a
display-only page — you can only look here, not change behavior; actual tool authorization
is controlled by [Tool Permissions](/guide/permissions) and the session mode
(Build / Plan / Ask).

## Page content

Tools are grouped into five categories. Tap a group title to collapse or expand it:

| Group | Includes (partial) |
| --- | --- |
| Files | Read / write / edit files, list directory, search code, send file, view image |
| Commands & Terminal | Run command (Bash), background terminal, Shizuku host actions |
| Network | Web search, fetch web page, GitHub read, GitHub write |
| AI Collaboration | Plan mode, todo list, memory, load skill, generate image, ask user, subagents, etc. |
| Extensions | MCP management, built-in browser |

Tap any tool to open its detail page, which shows:

- **Tool name and summary**: the identifier the AI uses in chat (e.g. `githubWrite`) and what it does.
- **Permission mode**: "Auto" means tool calls run without confirmation; "Ask" means the app
  asks for consent before every call in Build mode. Write-type actions are always rejected in
  Plan / Ask mode.
- **Parameters**: each parameter's name, type, whether it is required, and its meaning.

## GitHub integration

GitHub Read (`github`) and GitHub Write (`githubWrite`) let the AI operate on GitHub directly:

**Read actions** (auto-approved): list and view issues, issue comments, list and view pull
requests, fetch a PR's unified diff, and view CI check results for a branch / commit.

**Write actions** (confirmation required): create / update / comment on issues, create pull
requests, submit PR reviews (approve / request changes / comment), and merge PRs.

### First-time setup

1. In **Credentials** (Git pane → "Credentials & Identity"), add a credential for
   `github.com`: put your GitHub username in the username field and a GitHub Personal
   Access Token in the token field.
2. Required token scopes: read actions need `repo` (for private repositories) or the
   matching public-repo read access; write actions (issues / PRs / reviews / merges) need
   the full `repo` scope.
3. When the current workspace's Git remote points at a GitHub repository, the AI detects
   `owner/repo` automatically; for other repositories the AI can also be told the repo
   explicitly (`owner/repo`).

The token is stored only in the app's private directory (shared with Git push/pull
credentials) and never appears in conversation content.

### Common failures and fixes

| Message | Cause and fix |
| --- | --- |
| `NO_TOKEN` | No `github.com` credential saved; add one with the steps above |
| `GITHUB_HTTP_401` | Token invalid or expired; generate a new one and update the credential |
| `GITHUB_HTTP_403` | Insufficient token scope or rate-limited; check the scope and retry later |
| `GITHUB_HTTP_404` | Wrong repo name, or the token cannot access this repository |
| `GITHUB_HTTP_422` | GitHub rejected the request payload (e.g. a missing required field); fix as suggested |
| `MISSING_REPO` | The workspace has no GitHub remote; have the AI pass `repo="owner/repo"` explicitly |
