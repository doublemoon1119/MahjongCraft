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
 */
class ExamplePairingWallLayout(
    private val playerCount: Int,
) : TileWallLayout {
    override fun sideArrangement(dealerSeatIndex: Int): TileWallSideArrangement = when (playerCount) {
        2 -> TileWallSideArrangement(TileWallSideReference.DEALER, listOf(0, 2))
        else -> TileWallSideArrangement(TileWallSideReference.DEALER, listOf(0, 3, 2, 1))
    }

    override fun resolve(shuffledTiles: List<IdentifiedTile>, opening: WallOpening): TileWallLayoutResult {
        val stacksPerSide = if (playerCount == 2) 4 else playerCount
        val sideCount = if (playerCount == 2) 2 else 4
        val totalStacks = sideCount * stacksPerSide
        require(shuffledTiles.size == totalStacks * 2) { "Pairing wall requires ${totalStacks * 2} tiles" }
        require(opening.wallSideOffsetFromDealer in 0 until sideCount)
        require(opening.stacksFromRight in 1..stacksPerSide)
        val stacks = (0 until totalStacks).map { index ->
            shuffledTiles.subList(index * 2, index * 2 + 2)
        }
        val breakStack = opening.wallSideOffsetFromDealer * stacksPerSide + opening.stacksFromRight - 1
        val drawOrder = (1..totalStacks).flatMap { offset -> stacks[(breakStack + offset) % totalStacks].asReversed() }
        val structure = buildMap {
            for (globalStack in 0 until totalStacks) {
                val side = globalStack / stacksPerSide
                val stack = globalStack % stacksPerSide
                stacks[globalStack].forEachIndexed { layer, tile -> put(tile.id, TileWallPosition(side, stack, layer)) }
            }
        }
        return TileWallLayoutResult(drawOrder = drawOrder, reservedWallTiles = emptyList(), structure = structure)
    }
}
