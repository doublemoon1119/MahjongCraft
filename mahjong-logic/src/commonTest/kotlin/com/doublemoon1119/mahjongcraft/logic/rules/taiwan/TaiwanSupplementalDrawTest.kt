package com.doublemoon1119.mahjongcraft.logic.rules.taiwan

import com.doublemoon1119.mahjongcraft.logic.base.GameAction
import com.doublemoon1119.mahjongcraft.logic.base.Tile
import com.doublemoon1119.mahjongcraft.logic.table.SupplementalDrawContext
import com.doublemoon1119.mahjongcraft.logic.table.SupplementalDrawDecision
import com.doublemoon1119.mahjongcraft.logic.table.SupplementalDrawReasonIds
import com.doublemoon1119.mahjongcraft.logic.table.TileWall
import com.doublemoon1119.mahjongcraft.testing.logic.base.FakeIdentifiedTileFactory
import com.doublemoon1119.mahjongcraft.testing.logic.table.FakeTableStateFactory
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.uuid.Uuid

/** 台灣麻將槓後補牌與保留區補位測試。 */
class TaiwanSupplementalDrawTest {
    /** 最後一張可用牌用於補位，下一次補牌因牌牆耗盡被拒絕。 */
    @Test
    fun `last live tile replenishes the reserved wall`() {
        val reserved = List(16) { FakeIdentifiedTileFactory.create(Tile.Honor.East) }
        val live = FakeIdentifiedTileFactory.create(Tile.Honor.South)
        val config = TaiwanRuleConfig()
        val state = FakeTableStateFactory.create(
            config = config,
            tileWall = TileWall(listOf(live)),
            reservedWallTiles = reserved,
        )
        val policy = TaiwanRuleModule("mahjongcraft:taiwan", config).createSupplementalDrawPolicy()
        val context = SupplementalDrawContext(
            tableStateBeforeAction = state,
            tableStateAfterAction = state,
            actorPlayerId = state.currentPlayer.id,
            action = GameAction.Kan(
                type = GameAction.KanType.CLOSED_KAN,
                tileId = Uuid.random(),
                withTiles = emptyList(),
            ),
        )
        val result = assertIs<SupplementalDrawDecision.Completed>(policy.resolve(context))
        assertEquals(listOf(reserved.first()), result.drawnTiles)
        assertEquals(reserved.drop(1) + live, result.reservedWallTiles)
        assertEquals(0, result.tileWall.remainingCount)

        val after = state.copy(tileWall = result.tileWall, reservedWallTiles = result.reservedWallTiles)
        val exhausted = policy.resolve(context.copy(tableStateBeforeAction = after, tableStateAfterAction = after))
        assertEquals(SupplementalDrawDecision.Rejected(SupplementalDrawReasonIds.WALL_EXHAUSTED), exhausted)
    }
}
