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
     * **軌道一律落在開門面這一面牆內，不跨面。** 軌道由頭端往回數 [stackCount] 墩，所以頭端不得低於
     * `stackCount - 1`，否則墩序號會溢到前一面。開門點本身低於這個下限時（例如 17 墩的牆、軌道 10 墩、
     * 開門點在第 8 墩），軌道會整條往開門面內推，**保留牌因而不與開門點相鄰**——這是單面限制下的必然
     * 結果，不是版位錯誤。
     *
     * [initialVacantStackCount] 表示開局時實際由保留牌佔用、因此必須避開其他牌的軌道前綴；其後格位
     * 可以在開局時仍由活牌佔用，供後續補牌 transition 搬入，避免為未來容量預先挖出空墩。
     *
     * [extraStacksAfterHead] 會在軌道頭端之外多保留幾格，供保留牌整體朝開門空位平移時使用——平移量
     * 為幾墩，就要保留幾墩，否則最外側的保留牌會被推出牆面。只有與候選軌道相同開門面的占位會參與碰撞
     * 判斷；上下層分開計算。
     */
    fun plan(
        opening: WallOpening,
        stacksPerSide: Int,
        stackCount: Int,
        occupiedPositions: Set<TileWallPosition>,
        initialVacantStackCount: Int = stackCount,
        extraStacksAfterHead: Int = 0,
    ): SingleSideReservedWallTrack? {
        require(stacksPerSide > 0) { "Stacks per side must be positive" }
        require(stackCount in 1..stacksPerSide) { "Reserved wall stack count must fit on one wall side" }
        require(initialVacantStackCount in 1..stackCount) {
            "Initially vacant stack count must fit inside the reserved wall track"
        }
        require(extraStacksAfterHead >= 0) { "Extra stacks after the track head must not be negative" }
        val preferredHead = opening.stacksFromRight - 1
        // 單面限制的下限，理由與後果見本函式 KDoc。
        val minimumHead = stackCount - 1
        val maximumHead = stacksPerSide - 1 - extraStacksAfterHead
        if (minimumHead > maximumHead) return null
        return (minimumHead..maximumHead)
            .sortedWith(compareBy<Int> { kotlin.math.abs(it - preferredHead) }.thenBy { it })
            .firstNotNullOfOrNull { head ->
                val indices = List(stackCount) { offset -> head - offset }
                val trackPositions = buildSet {
                    indices.take(initialVacantStackCount).forEach { stack ->
                        add(TileWallPosition(opening.wallSideOffsetFromDealer, stack, 0))
                        add(TileWallPosition(opening.wallSideOffsetFromDealer, stack, 1))
                    }
                    repeat(extraStacksAfterHead) { offset ->
                        add(TileWallPosition(opening.wallSideOffsetFromDealer, head + 1 + offset, 0))
                        add(TileWallPosition(opening.wallSideOffsetFromDealer, head + 1 + offset, 1))
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
