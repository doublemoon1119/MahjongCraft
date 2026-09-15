package com.doublemoon1119.mahjongcraft.ktlint

import com.pinterest.ktlint.rule.engine.api.Code
import com.pinterest.ktlint.rule.engine.api.KtLintRuleEngine
import com.pinterest.ktlint.rule.engine.api.LintError
import com.pinterest.ktlint.rule.engine.core.api.Rule
import com.pinterest.ktlint.rule.engine.core.api.RuleProvider

/**
 * 以 [KtLintRuleEngine] 直接對程式碼片段執行單一規則並收集違規。
 *
 * 不使用 ktlint 官方的 `ktlint-test` 斷言工具——它建立在 AssertJ 與 JUnit 之上，而本專案只允許
 * `kotlin.test`（見 CONTRIBUTING.md）。直接驅動 rule engine 可以得到同樣的結果，並用 `kotlin.test` 斷言。
 */
internal fun lint(code: String, rule: () -> Rule): List<LintError> {
    val errors = mutableListOf<LintError>()
    KtLintRuleEngine(ruleProviders = setOf(RuleProvider(rule)))
        .lint(Code.fromSnippet(code)) { errors += it }
    return errors
}

/** 取得違規訊息清單，供斷言使用。 */
internal fun List<LintError>.messages(): List<String> = map { it.detail }
