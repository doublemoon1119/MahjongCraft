package com.doublemoon1119.mahjongcraft.domain.rules.riichi

import com.doublemoon1119.mahjongcraft.domain.config.DynamicRuleState

/**
 * 日本麻將特有的動態桌況狀態。
 *
 * @property riichiStickCount 場上存留的立直棒數量。
 */
data class RiichiDynamicState(
    var riichiStickCount: Int = 0
) : DynamicRuleState