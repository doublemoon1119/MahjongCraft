# AI Collaboration Guide

## Core Principles

- Discuss first, then change. Make changes only after the user has approved them.
- When a message contains a question, answer it before doing anything else, even if the same message seems
  to authorize more work.
- Agreeing to a plan's overall direction does not approve its details. List the concrete items of a batch and
  get approval before carrying them out.
- Approval covers only the task it was given for. Each commit, push and merge needs its own approval;
  an earlier approval does not carry over.

## Communication

- Reply in the same language the user writes in, and explain in plain terms before using identifiers.
- When editing files directly, report what changed instead of repeating the file contents.
- When providing code for the user to paste, give the complete file contents with no omissions (e.g., no
  `// ... existing code ...`) and prefix every code block with the file path it belongs to.

## Delegation Policy

- Work independently by default. Use subagents only when the user explicitly requests delegation for the
  current task. Requests such as "start implementation" or "review this change", available tools, and an
  expected time or context saving do not count as authorization.
- Authorization applies only to the specified task and scope. A subagent asked to review works read-only and
  must not edit files, run builds or execute tests unless the user explicitly authorizes those actions.
- When delegation is authorized, give each agent an explicit absolute workspace path and a bounded scope.
  Avoid concurrent edits to the same files and keep build execution under one coordinator.
- The primary agent remains responsible for inspecting actual changes, checking reported results and
  verifying the final integrated result. A subagent's completion report alone is not proof of correctness.

## Workspace and Build Discipline

These rules apply to the primary agent and any explicitly authorized subagent.

- Confirm the actual working directory and intended worktree before executing commands or editing files.
  Use explicit absolute paths when multiple worktrees exist. Do not modify a different checkout.
- Use the system user's Gradle user home. Do not create a project-local `.gradle-user-home` or redirect
  `GRADLE_USER_HOME` or `--gradle-user-home` to a project-local directory.
- Coordinate builds and tests through a single executor. Do not start concurrent Gradle invocations in the
  same worktree, and do not run Gradle while a game client launched from that worktree is open. Game
  clients started from other installations do not block builds.

## Git Safety

- Do not commit, push or merge on your own initiative; see Core Principles for approval.
- Do not skip commit signing or hooks unless the user explicitly allows it for that commit.
- Do not add AI attribution, such as `Co-Authored-By` trailers, to commit messages or pull requests.
- Worktrees share one stash stack. Never pop or drop a stash you did not create; prefer a temporary commit
  to set work aside.

## Planning Documents

- Planning and progress notes under `docs/temp/` are shared with other agents. Write them objectively, and
  do not use names or shorthand that exist only in a chat conversation.

## Project Conventions

Start with [CONTRIBUTING.md](CONTRIBUTING.md). It covers verification before committing and Git commit
guidelines, and lists which guide in [`docs/contributing/`](docs/contributing/) to read for each kind of
change, such as Kotlin conventions, registries, project architecture, dependency rules, builds and
persistence. Read every guide that applies before making the change.
