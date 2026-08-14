package com.doublemoon1119.mahjongcraft.logic.rules.riichi.layout

import com.doublemoon1119.mahjongcraft.logic.base.IdentifiedTile
import com.doublemoon1119.mahjongcraft.logic.table.layout.TileWallLayout
import com.doublemoon1119.mahjongcraft.logic.table.layout.TileWallLayoutResult
import com.doublemoon1119.mahjongcraft.logic.table.layout.TileWallPosition
import com.doublemoon1119.mahjongcraft.logic.table.opening.WallOpening

/**
 * 四人日本麻將的固定 136 張牌牆布局。
 *
 * 四面牌牆，每面 17 墩、每墩 2 層，共 68 墩 136 張牌。開門缺口決定後，從缺口往右（朝向較小的
 * [TileWallPosition.stack] 方向，跨面時繞到前一面的最右墩）連續數 7 墩（14 張）劃為王牌；活牌從
 * 缺口的另一側（左邊）開始，往左連續繞完剩餘 61 墩，直到緊鄰王牌另一端為止。
 *
 * 牌牆內部的建牌填入順序（哪個索引對應哪面哪墩哪層）與墩內的摸牌順序（上層先或下層先）皆為本類別
 * 自訂的內部慣例——牌組進入此類別前已完成洗牌，因此這兩者不影響公平性，也不需要額外規則來源；
 * 但王牌相對開門缺口的方向與張數，是依可靠規則來源（WRC／通行日麻慣例：開門缺口往右數 7 墩、
 * 14 張為王牌）核對過的結果。
 */
object RiichiWallLayout : TileWallLayout {
    /** 四人日麻的牌牆面數。 */
    private const val SIDE_COUNT = 4

    /** 每面牌牆的墩數。 */
    private const val STACKS_PER_SIDE = 17

    /** 每墩的層數。 */
    private const val LAYERS_PER_STACK = 2

    /** 王牌固定墩數（14 張）。 */
    private const val DEAD_WALL_STACKS = 7

    /** 牌牆總墩數。 */
    private const val TOTAL_STACKS = SIDE_COUNT * STACKS_PER_SIDE

    /** 牌牆總張數。 */
    private const val TOTAL_TILES = TOTAL_STACKS * LAYERS_PER_STACK

    override fun resolve(shuffledTiles: List<IdentifiedTile>, opening: WallOpening): TileWallLayoutResult {
        require(shuffledTiles.size == TOTAL_TILES) {
            "Riichi wall layout requires exactly $TOTAL_TILES tiles, got ${shuffledTiles.size}"
        }
        require(opening.wallSideOffsetFromDealer < SIDE_COUNT) {
            "Wall side offset ${opening.wallSideOffsetFromDealer} exceeds side count $SIDE_COUNT"
        }
        require(opening.stacksFromRight <= STACKS_PER_SIDE) {
            "Stacks from right ${opening.stacksFromRight} exceeds stacks per side $STACKS_PER_SIDE"
        }

        // 建牌：全域墩序號 0..67，依面（0..3）、墩（0..16，從該面右端起算）排列；同一面內墩序號
        // 遞增代表往左移動，超出該面最左墩（16）後繞到下一面的最右墩（0），與實體牌牆的轉角相接
        // 方向一致。每墩依序取用洗好牌列中的下兩張。
        val stacks: List<List<IdentifiedTile>> = (0 until TOTAL_STACKS).map { globalStack ->
            val base = globalStack * LAYERS_PER_STACK
            shuffledTiles.subList(base, base + LAYERS_PER_STACK)
        }

        // 開門缺口右側緊鄰的墩（一基底 stacksFromRight 轉零基底）。
        val breakGlobalStack = opening.wallSideOffsetFromDealer * STACKS_PER_SIDE + (opening.stacksFromRight - 1)

        // 王牌：從缺口本身開始，往右（全域墩序號遞減，含跨面繞回）連續 7 墩。
        val deadWallStacks = (0 until DEAD_WALL_STACKS).map { offset ->
            floorMod(breakGlobalStack - offset, TOTAL_STACKS)
        }
        // 活牌：緊接在王牌另一端之後，往左（全域墩序號遞增）連續剩餘墩數，最終繞回緊鄰缺口右側。
        val liveWallStacks = (1..(TOTAL_STACKS - DEAD_WALL_STACKS)).map { offset ->
            floorMod(breakGlobalStack + offset, TOTAL_STACKS)
        }

        // 每墩固定上層先摸／先劃入王牌。
        val initialDeadWall = deadWallStacks.flatMap { stacks[it].asReversed() }
        val drawOrder = liveWallStacks.flatMap { stacks[it].asReversed() }

        val structure = buildMap {
            for (globalStack in 0 until TOTAL_STACKS) {
                val side = globalStack / STACKS_PER_SIDE
                val stack = globalStack % STACKS_PER_SIDE
                stacks[globalStack].forEachIndexed { layer, tile ->
                    put(tile.id, TileWallPosition(side, stack, layer))
                }
            }
        }

        return TileWallLayoutResult(drawOrder, initialDeadWall, structure)
    }

    /** 恆為非負餘數的取模，處理 [breakGlobalStack] 往回數可能產生的負值。 */
    private fun floorMod(value: Int, modulus: Int): Int = ((value % modulus) + modulus) % modulus
}
