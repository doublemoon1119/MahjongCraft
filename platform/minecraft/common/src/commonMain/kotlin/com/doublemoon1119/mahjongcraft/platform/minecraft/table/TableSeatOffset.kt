package com.doublemoon1119.mahjongcraft.platform.minecraft.table

/**
 * 相對於座位錨點的偏移，以「坐在桌子南側的玩家」為基準的本地座標（單位：方塊）。
 *
 * 實際座標由通用 layout 依座位所在的桌子側面與桌子朝向旋轉換算，描述端不需要處理旋轉。
 *
 * @property x 沿著這個座位牌河與手牌排列的方向；正向朝副露角落。
 * @property y 往上。
 * @property z 垂直於排列方向；正向朝玩家自己，負向朝桌子中心。
 */
data class TableSeatOffset(
    val x: Double = 0.0,
    val y: Double = 0.0,
    val z: Double = 0.0,
)
