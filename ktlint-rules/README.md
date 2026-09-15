# Ktlint Rules

## Purpose

`ktlint-rules` supplies the MahjongCraft custom ktlint rule set that keeps project conventions enforced by the build instead of by review alone.

## Responsibilities

- Reject avoidable fully qualified project references in Kotlin code, so declarations are reached through a file-level import and a short name.
- Reject private primary-constructor properties that the class never reads, which the Kotlin compiler does not warn about.
- Register both rules through `RuleSetProviderV3` so they run inside the existing `ktlintCheck` tasks.

## Boundaries

The rules report violations and never rewrite source. Inserting an import automatically would have to resolve name clashes, nested declarations and aliases, which is not a formatter's decision. Package and import directives, string literals, comments and KDoc are not treated as code references, so component-scanning contracts and comments that name an outer layer across a dependency boundary stay valid. Intentional exceptions are suppressed at the declaration with a stated reason.

## Dependencies

The rules compile against `ktlint-rule-engine-core` and `ktlint-cli-ruleset-core`, both `compileOnly` because ktlint provides them when it loads the rule set. Every other Kotlin module consumes this module through the `ktlintRuleset` configuration applied by the convention plugins in [build-logic](../build-logic/README.md).

## Testing

Tests drive `KtLintRuleEngine` directly over code snippets and assert with `kotlin.test`; ktlint's own `KtLintAssertThat` helper is not used because it requires JUnit, which [CONTRIBUTING.md](../CONTRIBUTING.md) forbids. Each rule is covered by both violations and the legitimate exceptions it must not flag.
