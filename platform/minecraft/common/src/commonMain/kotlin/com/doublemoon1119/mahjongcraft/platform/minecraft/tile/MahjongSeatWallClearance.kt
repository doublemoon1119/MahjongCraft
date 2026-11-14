package com.doublemoon1119.mahjongcraft.platform.minecraft.tile

/**
 * 從一個座位看出去，牌牆在這一局剩下的時間裡可能佔到的範圍；由 [MahjongTableWallClearance.forSeat] 建立。
 *
 * @property stacksPerSide 牌牆每面的墩數。
 */
class MahjongSeatWallClearance internal constructor(
    val stacksPerSide: Int,
    internal val footprints: List<SeatWallFootprint>,
)

/**
 * 一張牌牆牌在座位局部座標中佔用的軸對齊範圍。
 *
 * @property x 中心 X，往玩家右手邊為正。
 * @property z 中心 Z，往玩家那一側為正。
 * @property halfX X 方向的一半寬度。
 * @property halfZ Z 方向的一半深度。
 */
internal data class SeatWallFootprint(val x: Double, val z: Double, val halfX: Double, val halfZ: Double)
