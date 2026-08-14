package com.doublemoon1119.mahjongcraft.logic.table.layout

import com.doublemoon1119.mahjongcraft.logic.base.IdentifiedTile
import com.doublemoon1119.mahjongcraft.logic.table.opening.WallOpening

/**
 * 四人麻將共用的四面牌牆排列邏輯。
 *
 * 供內建規則（目前為日本麻將與台灣麻將）的 [TileWallLayout] 實作重用同一段墩位運算，各規則只需
 * 提供自己的每面墩數與王牌墩數；不屬於 [TileWallLayout] 本身的公開契約，因為並非所有規則都必然採用
 * 四面牌牆的形狀。
 */
internal object FourSidedWallLayoutSupport {
    /** 四人麻將固定的牌牆面數。 */
    const val SIDE_COUNT = 4

    /** 每墩的層數。 */
    private const val LAYERS_PER_STACK = 2

    /**
     * 依 [stacksPerSide] 與 [deadWallStacks] 排列四面牌牆。
     *
     * 王牌從開門缺口本身開始，往右（全域墩序號遞減，跨面時繞到前一面的最右墩）連續數
     * [deadWallStacks] 墩；活牌緊接在王牌另一端之後，往左（全域墩序號遞增）連續繞完剩餘墩數，
     * 最終繞回緊鄰缺口右側。牌牆內部的建牌填入順序（哪個索引對應哪面哪墩哪層）與墩內的摸牌順序
     * （上層先或下層先）皆為內部慣例，牌組進入此函式前已完成洗牌，不影響公平性。
     *
     * @throws IllegalArgumentException 當 [shuffledTiles] 張數與 [stacksPerSide] 不吻合，或 [opening]
     * 超出面數／墩數範圍時拋出。
     */
    fun resolve(
        shuffledTiles: List<IdentifiedTile>,
        opening: WallOpening,
        stacksPerSide: Int,
        deadWallStacks: Int,
    ): TileWallLayoutResult {
        val totalStacks = SIDE_COUNT * stacksPerSide
        val totalTiles = totalStacks * LAYERS_PER_STACK
        require(shuffledTiles.size == totalTiles) {
            "Wall layout requires exactly $totalTiles tiles for $stacksPerSide stacks per side, got ${shuffledTiles.size}"
        }
        require(opening.wallSideOffsetFromDealer < SIDE_COUNT) {
            "Wall side offset ${opening.wallSideOffsetFromDealer} exceeds side count $SIDE_COUNT"
        }
        require(opening.stacksFromRight <= stacksPerSide) {
            "Stacks from right ${opening.stacksFromRight} exceeds stacks per side $stacksPerSide"
        }

        val stacks: List<List<IdentifiedTile>> = (0 until totalStacks).map { globalStack ->
            val base = globalStack * LAYERS_PER_STACK
            shuffledTiles.subList(base, base + LAYERS_PER_STACK)
        }

        // 開門缺口右側緊鄰的墩（一基底 stacksFromRight 轉零基底）。
        val breakGlobalStack = opening.wallSideOffsetFromDealer * stacksPerSide + (opening.stacksFromRight - 1)

        val deadWallGlobalStacks = (0 until deadWallStacks).map { offset ->
            floorMod(breakGlobalStack - offset, totalStacks)
        }
        val liveWallGlobalStacks = (1..(totalStacks - deadWallStacks)).map { offset ->
            floorMod(breakGlobalStack + offset, totalStacks)
        }

        val initialDeadWall = deadWallGlobalStacks.flatMap { stacks[it].asReversed() }
        val drawOrder = liveWallGlobalStacks.flatMap { stacks[it].asReversed() }

        val structure = buildMap {
            for (globalStack in 0 until totalStacks) {
                val side = globalStack / stacksPerSide
                val stack = globalStack % stacksPerSide
                stacks[globalStack].forEachIndexed { layer, tile ->
                    put(tile.id, TileWallPosition(side, stack, layer))
                }
            }
        }

        return TileWallLayoutResult(drawOrder, initialDeadWall, structure)
    }

    /** 恆為非負餘數的取模，處理往回數王牌時可能產生的負值。 */
    private fun floorMod(value: Int, modulus: Int): Int = ((value % modulus) + modulus) % modulus
}
