package com.doublemoon1119.mahjongcraft.example.pairing

import com.doublemoon1119.mahjongcraft.logic.base.IdentifiedTile
import com.doublemoon1119.mahjongcraft.logic.table.layout.TileWallLayout
import com.doublemoon1119.mahjongcraft.logic.table.layout.TileWallLayoutResult
import com.doublemoon1119.mahjongcraft.logic.table.layout.TileWallPosition
import com.doublemoon1119.mahjongcraft.logic.table.layout.TileWallSideArrangement
import com.doublemoon1119.mahjongcraft.logic.table.layout.TileWallSideReference
import com.doublemoon1119.mahjongcraft.logic.table.opening.WallOpening

/**
 * 依玩家人數建立簡易配對麻將的牌牆面數與每面墩數。
 *
 * @property playerCount 本局完整玩家人數。
 * @property wallTileValueCount 牌山使用的牌面種類數。
 */
class ExamplePairingWallLayout(
    private val playerCount: Int,
    private val wallTileValueCount: Int = 8,
) : TileWallLayout {
    override fun sideArrangement(dealerSeatIndex: Int): TileWallSideArrangement = when (playerCount) {
        2 -> TileWallSideArrangement(TileWallSideReference.DEALER, listOf(0, 2))
        else -> TileWallSideArrangement(TileWallSideReference.DEALER, listOf(0, 3, 2, 1))
    }

    override fun resolve(shuffledTiles: List<IdentifiedTile>, opening: WallOpening): TileWallLayoutResult {
        val stacksPerSide = ExamplePairingWallGeometry.stacksPerSide(playerCount, wallTileValueCount)
        val sideCount = stacksPerSide.size
        val totalStacks = wallTileValueCount * 2
        require(totalStacks % sideCount == 0 || playerCount != 2) {
            "Selected tile values cannot be evenly arranged for two players"
        }
        require(stacksPerSide.sum() == totalStacks)
        require(shuffledTiles.size == totalStacks * 2) { "Pairing wall requires ${totalStacks * 2} tiles" }
        require(opening.wallSideOffsetFromDealer in 0 until sideCount)
        require(opening.stacksFromRight in 1..stacksPerSide[opening.wallSideOffsetFromDealer])
        val stacks = (0 until totalStacks).map { index ->
            shuffledTiles.subList(index * 2, index * 2 + 2)
        }
        val breakStack = stacksPerSide.take(opening.wallSideOffsetFromDealer).sum() + opening.stacksFromRight - 1
        val drawOrder = (1..totalStacks).flatMap { offset -> stacks[(breakStack + offset) % totalStacks].asReversed() }
        val structure = buildMap {
            for (globalStack in 0 until totalStacks) {
                var side = 0
                var stack = globalStack
                while (stack >= stacksPerSide[side]) {
                    stack -= stacksPerSide[side]
                    side++
                }
                stacks[globalStack].forEachIndexed { layer, tile -> put(tile.id, TileWallPosition(side, stack, layer)) }
            }
        }
        return TileWallLayoutResult(drawOrder = drawOrder, reservedWallTiles = emptyList(), structure = structure)
    }
}
