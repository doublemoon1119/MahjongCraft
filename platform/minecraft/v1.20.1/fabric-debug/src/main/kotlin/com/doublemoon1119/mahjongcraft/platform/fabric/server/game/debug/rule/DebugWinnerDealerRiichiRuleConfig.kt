package com.doublemoon1119.mahjongcraft.platform.fabric.server.game.debug.rule

import com.doublemoon1119.mahjongcraft.logic.rules.riichi.RiichiFamilyRuleConfig
import com.doublemoon1119.mahjongcraft.logic.rules.riichi.RiichiRuleConfig

/**
 * [DebugWinnerDealerRiichiRuleModule] 的規則配置；所有設定都沿用 [base]，只是改由開發用規則模組處理。
 *
 * @property base 四人日麻的完整設定。
 */
internal data class DebugWinnerDealerRiichiRuleConfig(
    val base: RiichiRuleConfig = RiichiRuleConfig(),
) : RiichiFamilyRuleConfig by base
