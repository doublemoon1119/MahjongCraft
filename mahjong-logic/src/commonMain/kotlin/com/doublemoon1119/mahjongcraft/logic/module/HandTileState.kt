package com.doublemoon1119.mahjongcraft.logic.module

import kotlin.uuid.Uuid

/**
 * 一張手牌對所有觀察者採用的擺放姿態。
 *
 * @property tileId 目前仍在手牌中的牌張識別碼，用來區分牌面相同的不同牌張。
 * @property pose 這張牌的擺放姿態，不隨觀察者改變。
 */
data class HandTileState(
    val tileId: Uuid,
    val pose: HandTilePose,
)
