package com.doublemoon1119.mahjongcraft.platform.fabric.entity

/**
 * 和牌副露的同步快照。
 *
 * @property assetKeys 依結算順序排列的牌面資源識別碼。
 * @property faceDownIndices 顯示牌背的牌索引。
 */
data class WinSettlementMeldSnapshot(
    val assetKeys: List<String>,
    val faceDownIndices: Set<Int>,
)
