package com.doublemoon1119.mahjongcraft.platform.minecraft.tile

import com.doublemoon1119.mahjongcraft.platform.minecraft.table.MahjongTableSeatLayout

/**
 * 牌牆在這一局剩下的時間裡可能佔到的桌面範圍，供手牌、副露等牌列讓開牌牆。
 *
 * 由 [MahjongTileTableLayout.wallClearance] 依規則回報的牌牆位置建立，以不含桌子世界朝向的桌面座標保存。
 *
 * @property stacksPerSide 牌牆每面的墩數。
 */
class MahjongTableWallClearance internal constructor(
    val stacksPerSide: Int,
    internal val footprints: List<TableWallFootprint>,
) {
    /**
     * 從指定座位看出去的讓位範圍。
     *
     * @param seatLayout 座位與桌邊的對應。
     * @param seatIndex 座位 index。
     * @return 以該座位局部座標表示的範圍。
     */
    fun forSeat(seatLayout: MahjongTableSeatLayout, seatIndex: Int): MahjongSeatWallClearance = MahjongTileTableLayout.seatWallClearance(this, seatLayout.sideOf(seatIndex))
}

/**
 * 一張牌牆牌在桌面座標中的中心與朝向。
 *
 * @property x 桌面局部 X。
 * @property z 桌面局部 Z。
 * @property yaw 不含桌子世界朝向的 yaw。
 */
internal data class TableWallFootprint(val x: Double, val z: Double, val yaw: Float)
