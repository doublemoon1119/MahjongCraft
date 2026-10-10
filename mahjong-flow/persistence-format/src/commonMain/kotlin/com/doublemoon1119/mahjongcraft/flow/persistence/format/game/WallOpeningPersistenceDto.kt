package com.doublemoon1119.mahjongcraft.flow.persistence.format.game

import kotlinx.serialization.Serializable

/**
 * [WallOpening] 的完整 persistence DTO。
 *
 * @property wallSideOffsetFromDealer 相對莊家位置的牌牆側邊偏移。
 * @property stacksFromRight 從右側計算的牌山列數。
 */
@Serializable
data class WallOpeningPersistenceDto(
    val wallSideOffsetFromDealer: Int,
    val stacksFromRight: Int,
)
