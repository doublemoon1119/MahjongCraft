package com.doublemoon1119.mahjongcraft.model.riichi

import com.doublemoon1119.mahjongcraft.model.RuleExtraState

/**
 * 日本麻將特有的桌況額外狀態。
 *
 * @property riichiStickCount 場上存留的立直棒數量（供託）。
 */
data class RiichiExtraState(
    var riichiStickCount: Int = 0
) : RuleExtraState