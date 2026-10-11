package com.doublemoon1119.mahjongcraft.platform.fabric.client.render

/**
 * 一張牌經過牌組排列後的中心位置。
 *
 * @property assetKey 牌面資產。
 * @property centerX 水平中心。
 * @property centerY 垂直中心。
 * @property claimed 是否要在這張牌上方畫鳴牌指標。
 */
data class TileGroupPreviewPlacement(
    val assetKey: String,
    val centerX: Float,
    val centerY: Float,
    val claimed: Boolean,
)
