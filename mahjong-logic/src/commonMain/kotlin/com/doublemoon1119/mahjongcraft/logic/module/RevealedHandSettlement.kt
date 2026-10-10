package com.doublemoon1119.mahjongcraft.logic.module

import com.doublemoon1119.mahjongcraft.logic.base.Tile
import kotlin.uuid.Uuid

/**
 * 流局結算後由規則正式公開的一副手牌及其等待牌。
 *
 * [waitingTiles] 為空仍可要求公開手牌，例如九種九牌需要攤牌證明，但不是聽牌展示。
 *
 * @property playerId 手牌所屬玩家 Uuid。
 * @property waitingTiles 規則判定的等待牌；沒有等待牌資訊時為空集合。
 */
data class RevealedHandSettlement(
    val playerId: Uuid,
    val waitingTiles: Set<Tile>,
)
