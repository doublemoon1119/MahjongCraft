package com.doublemoon1119.mahjongcraft.ktlint

import com.pinterest.ktlint.cli.ruleset.core.api.RuleSetProviderV3
import com.pinterest.ktlint.rule.engine.core.api.RuleProvider
import com.pinterest.ktlint.rule.engine.core.api.RuleSetId

/** 本專案自訂 ktlint rule set 的識別字，同時是抑制註解 `ktlint:<id>:<rule>` 的前綴。 */
internal const val RULE_SET_ID: String = "mahjongcraft"

/** 各規則共用的維護者標示。 */
internal const val MAINTAINER: String = "MahjongCraft"

/**
 * 向 ktlint 註冊 MahjongCraft 專案自訂規則。
 *
 * 由 `META-INF/services` 的 `RuleSetProviderV3` 宣告載入；各 Kotlin module 透過 `ktlintRuleset` 依賴取得本
 * 模組的 jar，因此規則會隨既有的 `ktlintCheck` 與完整 build 一起執行，不需要另外的驗證 task。
 */
public class MahjongCraftRuleSetProvider : RuleSetProviderV3(RuleSetId(RULE_SET_ID)) {
    /** 提供本專案的全部自訂規則。 */
    override fun getRuleProviders(): Set<RuleProvider> = setOf(
        RuleProvider { NoFullyQualifiedProjectReferenceRule() },
        RuleProvider { NoUnusedConstructorPropertyRule() },
    )
}
