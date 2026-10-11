package com.doublemoon1119.mahjongcraft.buildlogic.detekt

import dev.detekt.api.Config
import dev.detekt.api.RuleName
import dev.detekt.api.RuleSet
import dev.detekt.api.RuleSetId
import dev.detekt.api.RuleSetProvider

/** 提供 MahjongCraft 專用的 KDoc 檢查規則集合。 */
class MahjongCraftRuleSetProvider : RuleSetProvider {
    /** 規則集合在 detekt 中使用的識別碼。 */
    override val ruleSetId: RuleSetId = RuleSetId("mahjongcraft")

    /** 建立此規則集合的規則實例。 */
    override fun instance(): RuleSet = RuleSet(
        ruleSetId,
        mapOf(
            RuleName("KDocOpeningLine") to { config: Config -> KDocOpeningLineRule(config) },
            RuleName("KDocTagSpacing") to { config: Config -> KDocTagSpacingRule(config) },
        ),
    )
}
