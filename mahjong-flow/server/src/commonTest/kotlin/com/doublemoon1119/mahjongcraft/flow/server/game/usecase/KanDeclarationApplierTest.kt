package com.doublemoon1119.mahjongcraft.flow.server.game.usecase

import com.doublemoon1119.mahjongcraft.logic.base.Tile
import com.doublemoon1119.mahjongcraft.logic.table.SupplementalDrawDecision
import com.doublemoon1119.mahjongcraft.logic.table.SupplementalDrawReasonIds
import com.doublemoon1119.mahjongcraft.logic.table.TileWall
import com.doublemoon1119.mahjongcraft.testing.logic.base.FakeIdentifiedTileFactory
import com.doublemoon1119.mahjongcraft.testing.logic.table.FakeTableStateFactory
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

/** [KanDeclarationApplier] 對規則補牌結果的通用邊界測試。 */
class KanDeclarationApplierTest {
    /** 驗證規則可從不同於日麻嶺上牌的來源補牌，而 Flow 只負責原子套用結果。 */
    @Test
    fun `test applies a valid rule-defined supplemental draw source`() {
        val drawnTile = FakeIdentifiedTileFactory.create(Tile.Numeric(Tile.Suit.Character, 1))
        val remainingTile = FakeIdentifiedTileFactory.create(Tile.Numeric(Tile.Suit.Character, 2))
        val reservedTile = FakeIdentifiedTileFactory.create(Tile.Honor.White)
        val state = FakeTableStateFactory.create(
            tileWall = TileWall(listOf(drawnTile, remainingTile)),
            initialDeadWall = listOf(reservedTile),
        )
        val decision = SupplementalDrawDecision.Completed(
            drawnTiles = listOf(drawnTile),
            tileWall = TileWall(listOf(remainingTile)),
            reservedWallTiles = listOf(reservedTile),
            dynamicRuleState = state.dynamicRuleState,
            newlyRevealedTileIds = setOf(reservedTile.id),
        )

        val result = assertIs<KanDeclarationApplier.Result.Applied>(
            KanDeclarationApplier.applyCompletedDecision(state, state, state.currentPlayer.id, decision),
        )

        assertEquals(drawnTile, result.tableState.currentPlayer.hand.lastDrawn)
        assertEquals(listOf(remainingTile), result.tableState.tileWall.getAllTiles())
        assertEquals(listOf(reservedTile), result.tableState.reservedWallTiles)
        assertEquals(setOf(reservedTile.id), result.newlyRevealedTileIds)
    }

    /** 驗證 policy 遺失牌張或重複使用同一 UUID 時，Flow 不會留下半完成桌況。 */
    @Test
    fun `test rejects a supplemental draw result that violates tile conservation`() {
        val liveTile = FakeIdentifiedTileFactory.create(Tile.Numeric(Tile.Suit.Dot, 1))
        val reservedTile = FakeIdentifiedTileFactory.create(Tile.Honor.Green)
        val state = FakeTableStateFactory.create(
            tileWall = TileWall(listOf(liveTile)),
            initialDeadWall = listOf(reservedTile),
        )
        val invalidDecision = SupplementalDrawDecision.Completed(
            drawnTiles = listOf(reservedTile),
            tileWall = TileWall(),
            reservedWallTiles = listOf(reservedTile),
            dynamicRuleState = state.dynamicRuleState,
        )

        val result = assertIs<KanDeclarationApplier.Result.Rejected>(
            KanDeclarationApplier.applyCompletedDecision(state, state, state.currentPlayer.id, invalidDecision),
        )

        assertEquals(SupplementalDrawReasonIds.INVALID_RESULT, result.reasonId)
        assertEquals(listOf(liveTile), state.tileWall.getAllTiles())
        assertEquals(listOf(reservedTile), state.reservedWallTiles)
    }
}
