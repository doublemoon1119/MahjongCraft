package com.doublemoon1119.mahjongcraft.logic.rules.riichi.structure

/**
 * 定義和牌時的完成形式。
 * 此介面用於明確標記 WinningTile 在 [HandStructure.Standard] 中填補的位置。
 */
sealed interface CompletionType {
    /** 單騎，聽雀頭 (如 5 聽 5) */
    data object Tanki : CompletionType

    /** 嵌張，聽順子中間 (如 24 聽 3) */
    data object Kanchan : CompletionType

    /** 邊張，聽 12 聽 3 或 89 聽 7 */
    data object Penchan : CompletionType

    /** 兩面，聽順子兩頭 (如 23 聽 1, 4) */
    data object Ryanmen : CompletionType

    /** 雙碰，雙碰聽牌 (兩個對子聽其中之一組成刻子) */
    data object Shanpon : CompletionType
}
