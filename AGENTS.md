# AI Collaboration Guide

## Core Principles

- Follow the "discuss first, modify later, wait for instruction, then act" workflow.
- Do not proceed with subsequent steps unless explicitly told to "go ahead".

## Output Format

- Provide **complete file contents** with no omissions (e.g., no `// ... existing code ...`).
- Prefix every code block with the file path it belongs to.

## Delegation Policy

These guidelines are capability-based and do not require any specific AI provider, model, or delegation mechanism.

When agent delegation is available and materially useful:

- Delegate codebase search, file discovery, dependency tracing, and other bounded exploratory work to an appropriately scoped agent.
- Delegate test execution, build verification, and concise error summarization when appropriate.
- Delegate mechanical or low-risk refactors when the requested change is well-defined and the affected files are clearly scoped.
- Keep architecture decisions, ambiguous changes, high-risk modifications, integration decisions, and final review with the primary agent.
- Prefer delegation when it reduces unnecessary context usage or latency while preserving correctness.
- Do not delegate trivial work when coordination would cost more than completing it directly.
- Give each editing agent a clearly separated file or responsibility scope; avoid concurrent edits to the same files.
- Agents should return concise findings, relevant file locations, and actionable conclusions rather than duplicating large amounts of repository context.
- The primary agent remains responsible for reviewing delegated work, resolving conflicts, and verifying the final integrated result.

## Project Conventions

For Kotlin development conventions, Git commit guidelines, project architecture, and dependency rules, see [CONTRIBUTING.md](CONTRIBUTING.md).
