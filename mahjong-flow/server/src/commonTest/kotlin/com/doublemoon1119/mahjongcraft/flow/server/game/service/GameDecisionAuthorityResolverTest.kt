package com.doublemoon1119.mahjongcraft.flow.server.game.service

import com.doublemoon1119.mahjongcraft.flow.common.game.model.Game
import com.doublemoon1119.mahjongcraft.flow.common.game.model.GameFlowConfig
import com.doublemoon1119.mahjongcraft.flow.common.game.model.PlayerDecisionPhase
import com.doublemoon1119.mahjongcraft.logic.base.GameAction
import com.doublemoon1119.mahjongcraft.logic.base.Hand
import com.doublemoon1119.mahjongcraft.logic.base.IdentifiedTile
import com.doublemoon1119.mahjongcraft.logic.base.Tile
import com.doublemoon1119.mahjongcraft.logic.table.PendingKanReaction
import com.doublemoon1119.mahjongcraft.logic.table.PendingReaction
import com.doublemoon1119.mahjongcraft.testing.logic.table.FakeMahjongPlayerFactory
import com.doublemoon1119.mahjongcraft.testing.logic.table.FakeTableStateFactory
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.uuid.Uuid

/** [GameDecisionAuthorityResolver] 的單元測試。 */
class GameDecisionAuthorityResolverTest {
    /** 驗證捨牌反應只包含仍未回應的合資格玩家。 */
    @Test
    fun `test discard reaction resolves only unanswered eligible players`() {
        val firstId = Uuid.random()
        val secondId = Uuid.random()
        val game = game(
            playerIds = listOf(firstId, secondId),
            pendingReaction = PendingReaction(
                discarderId = Uuid.random(),
                tileId = Uuid.random(),
                eligiblePlayerIds = setOf(firstId, secondId),
                responses = mapOf(firstId to GameAction.Pass),
            ),
        )

        assertEquals(
            mapOf(secondId to PlayerDecisionPhase.DISCARD_REACTION),
            GameDecisionAuthorityResolver().resolve(game),
        )
    }

    /** 驗證搶槓視窗優先解析，且只包含尚未回應的玩家。 */
    @Test
    fun `test chankan reaction resolves unanswered eligible players`() {
        val playerId = Uuid.random()
        val robbedTile = IdentifiedTile(Uuid.random(), Tile.Honor.White)
        val game = Game(
            tableState = FakeTableStateFactory.create(
                players = listOf(FakeMahjongPlayerFactory.create(id = playerId)),
                pendingKanReaction = PendingKanReaction(
                    declarerId = Uuid.random(),
                    kanAction = GameAction.Kan(
                        type = GameAction.KanType.ADDED_KAN,
                        tileId = robbedTile.id,
                        withTiles = emptyList(),
                    ),
                    robbedTile = robbedTile,
                    eligiblePlayerIds = setOf(playerId),
                ),
            ),
            flowConfig = GameFlowConfig(),
        )

        assertEquals(
            mapOf(playerId to PlayerDecisionPhase.KAN_REACTION),
            GameDecisionAuthorityResolver().resolve(game),
        )
    }

    /** 驗證目前玩家摸牌後取得自己回合的決策權。 */
    @Test
    fun `test drawn current player resolves own turn decision`() {
        val playerId = Uuid.random()
        val player = FakeMahjongPlayerFactory.create(
            id = playerId,
            hand = Hand(lastDrawn = IdentifiedTile(Uuid.random(), Tile.Honor.East)),
        )
        val game = Game(
            tableState = FakeTableStateFactory.create(players = listOf(player)),
            flowConfig = GameFlowConfig(),
        )

        assertEquals(
            mapOf(playerId to PlayerDecisionPhase.OWN_TURN),
            GameDecisionAuthorityResolver().resolve(game),
        )
    }

    /** 驗證尚未摸牌的機械回合動作不會建立思考計時。 */
    @Test
    fun `test current player without draw or claimed meld has no decision`() {
        val game = game(listOf(Uuid.random()))

        assertEquals(emptyMap(), GameDecisionAuthorityResolver().resolve(game))
    }

    /** 驗證強制自動操作玩家不再建立新的思考計時器。 */
    @Test
    fun `test forced auto play player is excluded from decisions`() {
        val playerId = Uuid.random()
        val player = FakeMahjongPlayerFactory.create(
            id = playerId,
            hand = Hand(lastDrawn = IdentifiedTile(Uuid.random(), Tile.Honor.East)),
        )
        val game = Game(
            tableState = FakeTableStateFactory.create(players = listOf(player)),
            flowConfig = GameFlowConfig(),
            forcedAutoPlayPlayerIds = setOf(playerId),
        )

        assertEquals(emptyMap(), GameDecisionAuthorityResolver().resolve(game))
    }

    /**
     * 驗證 AI 玩家摸牌後不會被視為需要決策——AI 的下一步由 `AiTurnDriver` 同步解析，不涉及等待，
     * 誤判成需要決策會讓它被建立思考計時器，計時器真的耗盡時就會被誤標記進
     * `forcedAutoPlayPlayerIds`，改由 `ForcedAutoPlayDriver` 的固定邏輯接管，而不是它自己的策略。
     */
    @Test
    fun `test ai current player does not resolve own turn decision`() {
        val playerId = Uuid.random()
        val aiPlayer = FakeMahjongPlayerFactory.create(
            id = playerId,
            hand = Hand(lastDrawn = IdentifiedTile(Uuid.random(), Tile.Honor.East)),
            aiStrategyKey = "example-strategy",
        )
        val game = Game(
            tableState = FakeTableStateFactory.create(players = listOf(aiPlayer)),
            flowConfig = GameFlowConfig(),
        )

        assertEquals(emptyMap(), GameDecisionAuthorityResolver().resolve(game))
    }

    /** 驗證 AI 玩家即使在捨牌反應視窗的合資格清單裡，也不會被視為需要決策。 */
    @Test
    fun `test ai eligible player does not resolve discard reaction decision`() {
        val aiId = Uuid.random()
        val humanId = Uuid.random()
        val game = Game(
            tableState = FakeTableStateFactory.create(
                players = listOf(
                    FakeMahjongPlayerFactory.create(id = aiId, aiStrategyKey = "example-strategy"),
                    FakeMahjongPlayerFactory.create(id = humanId),
                ),
                pendingReaction = PendingReaction(
                    discarderId = Uuid.random(),
                    tileId = Uuid.random(),
                    eligiblePlayerIds = setOf(aiId, humanId),
                    responses = emptyMap(),
                ),
            ),
            flowConfig = GameFlowConfig(),
        )

        assertEquals(
            mapOf(humanId to PlayerDecisionPhase.DISCARD_REACTION),
            GameDecisionAuthorityResolver().resolve(game),
        )
    }

    /** 建立指定玩家及捨牌反應視窗的權威遊戲。 */
    private fun game(
        playerIds: List<Uuid>,
        pendingReaction: PendingReaction? = null,
    ): Game = Game(
        tableState = FakeTableStateFactory.create(
            players = playerIds.map { FakeMahjongPlayerFactory.create(id = it) },
            pendingReaction = pendingReaction,
        ),
        flowConfig = GameFlowConfig(),
    )
}
