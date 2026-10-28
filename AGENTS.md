# AI Collaboration Guide

## Core Principles

- Follow the "discuss first, modify later, wait for instruction, then act" workflow.
- Do not proceed with subsequent steps unless explicitly told to "go ahead".

## Output Format

- Provide **complete file contents** with no omissions (e.g., no `// ... existing code ...`).
- Prefix every code block with the file path it belongs to.

## Delegation Policy

- Work independently by default. Do not use subagents for searches, implementation, verification or review
  unless the user explicitly requests delegation for the current task.
- Requests such as "start implementation" or "review this change" do not authorize subagents. Available
  tools and an expected time or context saving do not count as authorization either.
- Authorization applies only to the specified task and scope. Do not carry it into later tasks or expand a
  review request into permission to edit files or run builds.
- A subagent asked to review works read-only. It must not modify files, run builds or execute tests unless
  the user explicitly authorizes those additional actions.
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
  same worktree, and do not run Gradle while the game client is open.

## Project Conventions

Start with [CONTRIBUTING.md](CONTRIBUTING.md). It covers verification before committing and Git commit
guidelines, and lists which guide in [`docs/contributing/`](docs/contributing/) to read for each kind of
change, such as Kotlin conventions, registries, project architecture, dependency rules, builds and
persistence. Read every guide that applies before making the change.
