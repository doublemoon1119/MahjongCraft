package com.doublemoon1119.mahjongcraft.platform.fabric.entity

/**
 * 單張 showcase 視覺牌的同步快照。
 *
 * @property wingIndex 展示翼索引。
 * @property order 翼內的牌排列順序。
 * @property assetKey 牌面資源識別碼。
 * @property startOffsetX 起飛前相對舞台的 X 位移。
 * @property startOffsetY 起飛前相對舞台的 Y 位移。
 * @property startOffsetZ 起飛前相對舞台的 Z 位移。
 * @property startYaw 起飛前的水平朝向。
 * @property group 牌所屬的組：`0` 為手牌，之後依序為各組副露；編隊時組與組之間留空隙。
 * @property standYaw 起飛前站起來後的朝向；橫放的鳴牌在站起來時轉到這個朝向。
 * @property faceDown 是否一路露出牌背，例如暗槓兩端。
 */
data class ShowcaseCardSnapshot(
    val wingIndex: Int,
    val order: Int,
    val assetKey: String,
    val startOffsetX: Double,
    val startOffsetY: Double,
    val startOffsetZ: Double,
    val startYaw: Float,
    val group: Int = 0,
    val standYaw: Float = startYaw,
    val faceDown: Boolean = false,
)
