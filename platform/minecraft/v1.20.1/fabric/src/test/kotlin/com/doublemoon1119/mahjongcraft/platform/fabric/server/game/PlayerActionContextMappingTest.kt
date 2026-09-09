package com.doublemoon1119.mahjongcraft.platform.fabric.server.game

import com.doublemoon1119.mahjongcraft.flow.common.game.model.GameCommand
import com.doublemoon1119.mahjongcraft.flow.server.game.service.PlayerActionContext
import com.doublemoon1119.mahjongcraft.logic.base.GameAction
import com.doublemoon1119.mahjongcraft.logic.base.IdentifiedTile
import com.doublemoon1119.mahjongcraft.logic.base.Tile
import com.doublemoon1119.mahjongcraft.logic.table.PendingKanReaction
import com.doublemoon1119.mahjongcraft.logic.table.PendingReaction
import com.doublemoon1119.mahjongcraft.platform.minecraft.text.GameTurnStatus
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.uuid.Uuid

/** Minecraft 平台玩家操作情境映射的單元測試。 */
class PlayerActionContextMappingTest {
    /** 驗證搶槓與捨牌反應使用各自的回應命令信封。 */
    @Test
    fun `test reaction contexts wrap action in matching command`() {
        val playerId = Uuid.random()
        val tile = IdentifiedTile(Uuid.random(), Tile.Honor.White)
        val kanContext = PlayerActionContext.KanReaction(
            playerId,
            PendingKanReaction(
                declarerId = Uuid.random(),
                kanAction = GameAction.Kan(GameAction.KanType.ADDED_KAN, tile.id, emptyList()),
                robbedTile = tile,
                eligiblePlayerIds = setOf(playerId),
            ),
        )
        val discardContext = PlayerActionContext.DiscardReaction(
            playerId,
            PendingReaction(Uuid.random(), tile.id, setOf(playerId)),
        )

        assertEquals(GameCommand.RespondToKan(GameAction.Pass), kanContext.toGameCommand(GameAction.Pass))
        assertEquals(GameCommand.RespondToDiscard(GameAction.Pass), discardContext.toGameCommand(GameAction.Pass))
    }

    /** 驗證自己回合只接受可映射的額外合法動作。 */
    @Test
    fun `test own turn context maps supported action only`() {
        val context = PlayerActionContext.OwnTurn(Uuid.random())

        assertEquals(GameCommand.Tsumo, context.toGameCommand(GameAction.Tsumo))
        assertNull(context.toGameCommand(GameAction.Pass))
    }

    /** 驗證三種操作情境及無情境對應到正確的手牌顯示狀態。 */
    @Test
    fun `test contexts map to turn status`() {
        val playerId = Uuid.random()
        val tile = IdentifiedTile(Uuid.random(), Tile.Honor.White)
        val kanContext = PlayerActionContext.KanReaction(
            playerId,
            PendingKanReaction(
                Uuid.random(),
                GameAction.Kan(GameAction.KanType.ADDED_KAN, tile.id, emptyList()),
                tile,
                setOf(playerId),
            ),
        )
        val discardContext = PlayerActionContext.DiscardReaction(
            playerId,
            PendingReaction(Uuid.random(), tile.id, setOf(playerId)),
        )

        assertEquals(GameTurnStatus.AWAITING_RESPONSE, kanContext.toTurnStatus())
        assertEquals(GameTurnStatus.AWAITING_RESPONSE, discardContext.toTurnStatus())
        assertEquals(GameTurnStatus.OWN_TURN, PlayerActionContext.OwnTurn(playerId).toTurnStatus())
        assertEquals(GameTurnStatus.WAITING, null.toTurnStatus())
    }
}
