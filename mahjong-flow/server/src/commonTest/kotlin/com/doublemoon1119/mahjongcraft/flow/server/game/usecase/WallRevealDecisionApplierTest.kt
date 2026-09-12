package com.doublemoon1119.mahjongcraft.flow.server.game.usecase

import com.doublemoon1119.mahjongcraft.logic.base.GameAction
import com.doublemoon1119.mahjongcraft.logic.base.Tile
import com.doublemoon1119.mahjongcraft.logic.rules.riichi.RiichiDynamicState
import com.doublemoon1119.mahjongcraft.logic.rules.riichi.RiichiPendingKanDoraReveal
import com.doublemoon1119.mahjongcraft.logic.rules.riichi.RiichiRuleConfig
import com.doublemoon1119.mahjongcraft.logic.table.TableState
import com.doublemoon1119.mahjongcraft.logic.table.WallRevealDecision
import com.doublemoon1119.mahjongcraft.testing.logic.base.FakeIdentifiedTileFactory
import com.doublemoon1119.mahjongcraft.testing.logic.table.FakeMahjongPlayerFactory
import com.doublemoon1119.mahjongcraft.testing.logic.table.FakeTableStateFactory
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.uuid.Uuid

/** [WallRevealDecisionApplier] 的 Flow 邊界驗證測試。 */
class WallRevealDecisionApplierTest {
    /** 驗證宣告集合與可見差集一致時只套用規則動態狀態。 */
    @Test
    fun `test valid newly revealed tile set is applied`() {
        val fixture = fixture()
        val updatedDynamicState = fixture.dynamicState.copy(
            revealedKanDoraCount = 1,
            pendingKanDoraReveals = emptyList(),
        )
        val updatedTable = fixture.tableState.copy(dynamicRuleState = updatedDynamicState)
        val newlyRevealedTileIds = updatedDynamicState.getVisibleTileIds(updatedTable) -
            fixture.dynamicState.getVisibleTileIds(fixture.tableState)

        val result = assertIs<WallRevealDecisionApplier.Result.Applied>(
            WallRevealDecisionApplier.applyUpdatedDecision(
                fixture.tableState,
                WallRevealDecision.Updated(updatedDynamicState, newlyRevealedTileIds),
            ),
        )

        assertEquals(updatedDynamicState, result.tableState.dynamicRuleState)
        assertEquals(newlyRevealedTileIds, result.newlyRevealedTileIds)
    }

    /** 驗證 policy 不得宣告不屬於目前牌牆的公開牌 ID。 */
    @Test
    fun `test unknown newly revealed tile id is rejected`() {
        val fixture = fixture()
        val result = WallRevealDecisionApplier.applyUpdatedDecision(
            fixture.tableState,
            WallRevealDecision.Updated(fixture.dynamicState, setOf(Uuid.random())),
        )

        assertEquals(
            WallRevealDecisionApplier.Result.Rejected(WallRevealDecisionApplier.INVALID_RESULT_REASON_ID),
            result,
        )
    }

    /** 建立含一筆延後槓寶牌的有效日麻桌況。 */
    private fun fixture(): Fixture {
        val player = FakeMahjongPlayerFactory.create()
        val dynamicState = RiichiDynamicState(
            completedSupplementalDrawCount = 1,
            revealedKanDoraCount = 0,
            pendingKanDoraReveals = listOf(
                RiichiPendingKanDoraReveal(player.id, GameAction.KanType.OPEN_KAN, 1),
            ),
        )
        return Fixture(
            FakeTableStateFactory.create(
                players = listOf(player),
                config = RiichiRuleConfig(),
                initialDeadWall = List(14) {
                    FakeIdentifiedTileFactory.create(Tile.Numeric(Tile.Suit.Dot, 3))
                },
                dynamicRuleState = dynamicState,
            ),
            dynamicState,
        )
    }

    /** 測試共用桌況與對應日麻動態狀態。 */
    private data class Fixture(
        val tableState: TableState,
        val dynamicState: RiichiDynamicState,
    )
}
