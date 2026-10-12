package com.doublemoon1119.mahjongcraft.logic.table.layout

import com.doublemoon1119.mahjongcraft.logic.table.opening.WallOpening

/**
 * 固定於開門面的連續保留牌軌道。
 *
 * 保留牌一律落在單一面牆上，不跨面；軌道由頭端往回數，[stackIndices] 因此是同一面內的連續遞減墩序號。
 *
 * @property side 與 [WallOpening.wallSideOffsetFromDealer] 相同座標系的開門面。
 * @property stackIndices 由開門缺口一側往保留牌尾端排列的連續墩序號。
 */
data class SingleSideReservedWallTrack(
    val side: Int,
    val stackIndices: List<Int>,
) {
    init {
        require(side >= 0) { "Reserved wall track side must not be negative" }
        require(stackIndices.isNotEmpty()) { "Reserved wall track must contain at least one stack" }
        require(stackIndices.all { it >= 0 }) { "Reserved wall track stacks must not be negative" }
        require(stackIndices.zipWithNext().all { (left, right) -> right == left - 1 }) {
            "Reserved wall track stacks must be contiguous in descending order"
        }
    }

    /**
     * 取得軌道上第 [index] 墩指定 [layer] 的基本牌牆位置。
     *
     * @param index 軌道墩序列中的零基底索引。
     * @param layer 墩內層數，只接受 0 或 1。
     * @return 對應的基本牌牆位置。
     * @throws IllegalArgumentException 當 layer 不在 0..1 時拋出。
     * @throws IndexOutOfBoundsException 當 index 超出 stackIndices 索引範圍時拋出。
     */
    fun position(index: Int, layer: Int): TileWallPosition {
        require(layer in 0..1) { "Reserved wall track layer must be 0 or 1" }
        return TileWallPosition(side, stackIndices[index], layer)
    }
}
