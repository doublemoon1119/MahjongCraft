package com.doublemoon1119.mahjongcraft.flow.network.dto.snapshot

import kotlinx.serialization.Serializable

/**
 * 傳給觀察者的逐張姿態資料，不包含規則原始授權名單。
 *
 * @property tileId 手牌的牌張識別碼字串。
 * @property pose 這張牌對所有觀察者採用的擺放姿態。
 */
@Serializable
data class HandTileStateDto(
    val tileId: String,
    val pose: HandTilePoseDto,
)
