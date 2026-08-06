package com.doublemoon1119.mahjongcraft.logic.module

import com.doublemoon1119.mahjongcraft.logic.config.DynamicRuleState
import com.doublemoon1119.mahjongcraft.logic.table.MahjongPlayer

/**
 * [MahjongRuleModule.declareRiichi] 套用立直宣告後的結果。
 *
 * 立直宣告本身不是任何單一地區規則專屬的概念——只是恰好以「立直」命名，
 * 因此定義在 `:mahjong-logic` 各地區規則共用的 `module` package，而非某個地區規則自己的 package 底下。
 *
 * @property player 已套用立直宣告狀態變化的新玩家實例。
 * @property dynamicRuleState 已套用立直宣告狀態變化的新動態規則狀態。
 */
data class RiichiDeclarationResult(
    val player: MahjongPlayer,
    val dynamicRuleState: DynamicRuleState,
)
