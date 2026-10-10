package com.doublemoon1119.mahjongcraft.flow.persistence.format.history

import kotlinx.serialization.Serializable

/**
 * 活牌牆兩端的移除張數。
 *
 * @property removedFront 從摸牌端移除的張數。
 * @property removedBack 從另一端移除的張數。
 */
@Serializable
data class HistoryWallChangePersistenceDto(
    val removedFront: Int,
    val removedBack: Int,
)
