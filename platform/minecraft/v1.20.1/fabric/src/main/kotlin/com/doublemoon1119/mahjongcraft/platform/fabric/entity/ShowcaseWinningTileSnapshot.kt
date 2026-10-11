package com.doublemoon1119.mahjongcraft.platform.fabric.entity

/**
 * 共享權威胡牌張的牌面與起飛前世界相對位置快照。
 *
 * @property assetKey 牌面資源識別碼。
 * @property startOffsetX 起飛前相對舞台的 X 位移。
 * @property startOffsetY 起飛前相對舞台的 Y 位移。
 * @property startOffsetZ 起飛前相對舞台的 Z 位移。
 * @property startYaw 起飛前的水平朝向。
 */
data class ShowcaseWinningTileSnapshot(
    val assetKey: String,
    val startOffsetX: Double,
    val startOffsetY: Double,
    val startOffsetZ: Double,
    val startYaw: Float,
)
