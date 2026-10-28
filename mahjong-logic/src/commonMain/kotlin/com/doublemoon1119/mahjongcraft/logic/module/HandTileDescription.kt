package com.doublemoon1119.mahjongcraft.logic.module

import kotlin.uuid.Uuid

/**
 * 一張目前仍在玩家手中的牌所具有的額外可見權限與共同姿態。
 *
 * @property tileId 立牌或摸牌位的牌張識別碼；不是牌面種類。
 * @property visibility 額外開放的牌面讀取權。
 * @property pose 這張牌的擺放姿態，不隨觀察者改變。
 */
data class HandTileDescription(
    val tileId: Uuid,
    val visibility: HandTileVisibility,
    val pose: HandTilePose,
)
