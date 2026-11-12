package com.doublemoon1119.mahjongcraft.logic.table.layout

import com.doublemoon1119.mahjongcraft.logic.table.opening.WallOpening
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.floor

/** 為完整環狀牌牆規劃可跨越桌角的連續保留區軌道。 */
internal object CircularReservedWallTrackPlanner {
    /**
     * 從最接近開門位置的候選開始，尋找不會占用現有牌張的初始保留區位置。
     *
     * 軌道未來才使用的尾端可以暫時由活牌占用；補牌時，那些牌會依既有補位流程逐步移入保留區。
     *
     * @param opening 本局牌牆開門位置。
     * @param sideCount 牌牆面數。
     * @param stacksPerSide 每面牌牆的墩數。
     * @param stackCount 完整軌道需要的墩數。
     * @param occupiedPositions 目前不能占用的位置。
     * @param initialVacantStackCount 開局時立即由保留牌占用的軌道墩數。
     * @param extraStacksAfterHead 軌道頭端之外需要保持淨空的墩數。
     * @param alongWallStacks 保留牌沿牌牆方向套用的固定偏移。
     * @return 可用軌道；找不到時為 null。
     */
    fun plan(
        opening: WallOpening,
        sideCount: Int,
        stacksPerSide: Int,
        stackCount: Int,
        occupiedPositions: Set<TileWallPosition>,
        initialVacantStackCount: Int,
        extraStacksAfterHead: Int,
        alongWallStacks: Double,
    ): CircularReservedWallTrack? {
        require(sideCount > 0) { "Side count must be positive" }
        require(stacksPerSide > 0) { "Stacks per side must be positive" }
        val totalStacks = sideCount * stacksPerSide
        require(stackCount in 1..totalStacks) { "Reserved wall track must fit inside the wall" }
        require(initialVacantStackCount in 1..stackCount) {
            "Initially vacant stack count must fit inside the reserved wall track"
        }
        require(extraStacksAfterHead >= 0) { "Extra stacks after the track head must not be negative" }
        val preferredHead = opening.wallSideOffsetFromDealer * stacksPerSide + opening.stacksFromRight - 1
        return (0 until totalStacks)
            .sortedWith(compareBy<Int> { circularDistance(it, preferredHead, totalStacks) }.thenBy { it })
            .firstNotNullOfOrNull { head ->
                val trackIndices = List(stackCount) { offset -> floorMod(head - offset, totalStacks) }
                val immediatelyUsedStacks = buildList {
                    addAll(trackIndices.take(initialVacantStackCount))
                    repeat(extraStacksAfterHead) { offset -> add(floorMod(head + 1 + offset, totalStacks)) }
                }
                if (immediatelyUsedStacks.any { globalStack ->
                        overlapsOccupiedStack(
                            globalStack = globalStack,
                            alongWallStacks = alongWallStacks,
                            sideCount = sideCount,
                            stacksPerSide = stacksPerSide,
                            occupiedPositions = occupiedPositions,
                        )
                    }
                ) {
                    null
                } else {
                    CircularReservedWallTrack(trackIndices.map { index -> index.toPosition(stacksPerSide) })
                }
            }
    }

    /** 位移後的牌張是否會與仍在牌牆中的任一墩重疊。 */
    private fun overlapsOccupiedStack(
        globalStack: Int,
        alongWallStacks: Double,
        sideCount: Int,
        stacksPerSide: Int,
        occupiedPositions: Set<TileWallPosition>,
    ): Boolean {
        val totalStacks = sideCount * stacksPerSide
        val target = floorMod(globalStack + alongWallStacks, totalStacks.toDouble())
        val lowerGlobalStack = floorMod(floor(target).toInt(), totalStacks)
        val upperGlobalStack = floorMod(ceil(target).toInt(), totalStacks)
        return listOf(lowerGlobalStack, upperGlobalStack).distinct().any { occupiedGlobalStack ->
            val position = occupiedGlobalStack.toPosition(stacksPerSide)
            position in occupiedPositions || position.copy(layer = 1) in occupiedPositions
        }
    }

    /** 將環狀墩序號轉成牌牆位置。 */
    private fun Int.toPosition(stacksPerSide: Int): TileWallPosition = TileWallPosition(
        side = this / stacksPerSide,
        stack = this % stacksPerSide,
        layer = 0,
    )

    /** 兩個環狀墩序號之間的最短距離。 */
    private fun circularDistance(first: Int, second: Int, totalStacks: Int): Int {
        val direct = abs(first - second)
        return minOf(direct, totalStacks - direct)
    }

    /** 恆為非負的整數餘數。 */
    private fun floorMod(value: Int, modulus: Int): Int = ((value % modulus) + modulus) % modulus

    /** 恆為非負的浮點餘數。 */
    private fun floorMod(value: Double, modulus: Double): Double = ((value % modulus) + modulus) % modulus
}
