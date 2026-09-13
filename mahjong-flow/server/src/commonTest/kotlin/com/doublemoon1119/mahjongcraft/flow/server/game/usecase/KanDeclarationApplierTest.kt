package com.doublemoon1119.mahjongcraft.flow.server.game.usecase

import com.doublemoon1119.mahjongcraft.logic.base.GameAction
import com.doublemoon1119.mahjongcraft.logic.base.Tile
import com.doublemoon1119.mahjongcraft.logic.rules.riichi.RiichiDynamicState
import com.doublemoon1119.mahjongcraft.logic.rules.riichi.RiichiRuleConfig
import com.doublemoon1119.mahjongcraft.logic.rules.riichi.RiichiRuleModule
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
    /** 測試補牌套用流程使用的內建日麻規則模組。 */
    private val module = RiichiRuleModule("mahjongcraft:riichi", RiichiRuleConfig())

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
        )

        val result = assertIs<KanDeclarationApplier.Result.Applied>(
            KanDeclarationApplier.applyCompletedDecision(
                state,
                state,
                state.currentPlayer.id,
                decision,
                GameAction.Kan(GameAction.KanType.CLOSED_KAN, drawnTile.id, emptyList()),
                module,
            ),
        )

        assertEquals(drawnTile, result.tableState.currentPlayer.hand.lastDrawn)
        assertEquals(listOf(remainingTile), result.tableState.tileWall.getAllTiles())
        assertEquals(listOf(reservedTile), result.tableState.reservedWallTiles)
        assertEquals(emptyList(), result.wallRevealBatches)
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
            KanDeclarationApplier.applyCompletedDecision(
                state,
                state,
                state.currentPlayer.id,
                invalidDecision,
                GameAction.Kan(GameAction.KanType.CLOSED_KAN, reservedTile.id, emptyList()),
                module,
            ),
        )

        assertEquals(SupplementalDrawReasonIds.INVALID_RESULT, result.reasonId)
        assertEquals(listOf(liveTile), state.tileWall.getAllTiles())
        assertEquals(listOf(reservedTile), state.reservedWallTiles)
    }

    /** 驗證補牌與實體布局 transition 共同成功時，才回傳帶有移動階段的新桌況。 */
    @Test
    fun `test applies supplemental draw and physical wall transition atomically`() {
        val loweredTile = FakeIdentifiedTileFactory.create(Tile.Numeric(Tile.Suit.Character, 1))
        val replenishment = FakeIdentifiedTileFactory.create(Tile.Numeric(Tile.Suit.Character, 2))
        val reservedTiles = List(4) { FakeIdentifiedTileFactory.create(Tile.Honor.White) }
        val beforeState = FakeTableStateFactory.create(
            config = RiichiRuleConfig(),
            tileWall = TileWall(listOf(loweredTile, replenishment)),
            initialDeadWall = reservedTiles,
            dynamicRuleState = RiichiDynamicState(),
        ).withFirstKanPhysicalWallLayout()
        val decision = SupplementalDrawDecision.Completed(
            drawnTiles = listOf(reservedTiles.first()),
            tileWall = TileWall(listOf(loweredTile)),
            reservedWallTiles = reservedTiles.drop(1) + replenishment,
            dynamicRuleState = RiichiDynamicState(completedSupplementalDrawCount = 1),
        )
        val action = GameAction.Kan(GameAction.KanType.CLOSED_KAN, reservedTiles.first().id, emptyList())

        val result = assertIs<KanDeclarationApplier.Result.Applied>(
            KanDeclarationApplier.applyCompletedDecision(
                beforeState,
                beforeState,
                beforeState.currentPlayer.id,
                decision,
                action,
                module,
            ),
        )

        assertEquals(
            (result.tableState.tileWall.getAllTiles() + result.tableState.reservedWallTiles).map { it.id }.toSet(),
            result.tableState.physicalWallLayout?.placements?.keys,
        )
        assertEquals(2, result.physicalWallTransitionPhases.size)
    }
}
