package com.doublemoon1119.mahjongcraft.logic.table.layout

import com.doublemoon1119.mahjongcraft.logic.table.opening.WallOpening

/**
 * 固定於開門面的連續保留牌軌道。
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

    /** 取得軌道上第 [index] 墩指定 [layer] 的基本牌牆位置。 */
    fun position(index: Int, layer: Int): TileWallPosition {
        require(layer in 0..1) { "Reserved wall track layer must be 0 or 1" }
        return TileWallPosition(side, stackIndices[index], layer)
    }
}

/** 為四面牌牆規劃固定於開門面的連續保留牌軌道。 */
object SingleSideReservedWallTrackPlanner {
    /**
     * 以最接近原開門格位的位置建立 [stackCount] 墩軌道，並避開 [occupiedPositions]。
     *
     * [extraStackAfterHead] 會在軌道頭端之外多保留一格，例如日麻第一張嶺上牌的特殊下層位置。只有
     * 與候選軌道相同開門面的占位會參與碰撞判斷；上下層分開計算。
     */
    fun plan(
        opening: WallOpening,
        stacksPerSide: Int,
        stackCount: Int,
        occupiedPositions: Set<TileWallPosition>,
        extraStackAfterHead: Boolean = false,
    ): SingleSideReservedWallTrack? {
        require(stacksPerSide > 0) { "Stacks per side must be positive" }
        require(stackCount in 1..stacksPerSide) { "Reserved wall stack count must fit on one wall side" }
        val preferredHead = opening.stacksFromRight - 1
        val minimumHead = stackCount - 1
        val maximumHead = stacksPerSide - 1 - if (extraStackAfterHead) 1 else 0
        if (minimumHead > maximumHead) return null
        return (minimumHead..maximumHead)
            .sortedWith(compareBy<Int> { kotlin.math.abs(it - preferredHead) }.thenBy { it })
            .firstNotNullOfOrNull { head ->
                val indices = List(stackCount) { offset -> head - offset }
                val trackPositions = buildSet {
                    indices.forEach { stack ->
                        add(TileWallPosition(opening.wallSideOffsetFromDealer, stack, 0))
                        add(TileWallPosition(opening.wallSideOffsetFromDealer, stack, 1))
                    }
                    if (extraStackAfterHead) {
                        add(TileWallPosition(opening.wallSideOffsetFromDealer, head + 1, 0))
                        add(TileWallPosition(opening.wallSideOffsetFromDealer, head + 1, 1))
                    }
                }
                if (trackPositions.any { it in occupiedPositions }) {
                    null
                } else {
                    SingleSideReservedWallTrack(opening.wallSideOffsetFromDealer, indices)
                }
            }
    }
}
