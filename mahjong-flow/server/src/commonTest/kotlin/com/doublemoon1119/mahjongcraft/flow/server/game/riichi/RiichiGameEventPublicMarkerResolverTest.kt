package com.doublemoon1119.mahjongcraft.flow.server.game.riichi

import com.doublemoon1119.mahjongcraft.flow.api.event.MatchCompletion
import com.doublemoon1119.mahjongcraft.flow.common.game.event.MatchEndPublicMarkerContext
import com.doublemoon1119.mahjongcraft.flow.common.game.event.WinPublicMarkerContext
import com.doublemoon1119.mahjongcraft.logic.base.Tile
import com.doublemoon1119.mahjongcraft.logic.rules.riichi.RiichiPlayerState
import com.doublemoon1119.mahjongcraft.logic.rules.riichi.RiichiRuleConfig
import com.doublemoon1119.mahjongcraft.logic.rules.riichi.RiichiScoreConfig
import com.doublemoon1119.mahjongcraft.logic.table.BuiltInMatchEndReasonIds
import com.doublemoon1119.mahjongcraft.testing.logic.base.FakeIdentifiedTileFactory
import com.doublemoon1119.mahjongcraft.testing.logic.table.FakeMahjongPlayerFactory
import com.doublemoon1119.mahjongcraft.testing.logic.table.FakeTableStateFactory
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.uuid.Uuid

/** 驗證日麻 resolver 只公開規則允許公開的立直、一發與擊飛標記。 */
class RiichiGameEventPublicMarkerResolverTest {
    /** 立直與一發標記讀取前一個桌況，即使結算後狀態已清除也不受影響。 */
    @Test
    fun `win markers reflect winner state from previous table`() {
        val winner = FakeMahjongPlayerFactory.create(
            playerRuleState = RiichiPlayerState(
                riichiTile = FakeIdentifiedTileFactory.create(Tile.Honor.East),
                isIppatsu = true,
            ),
        )
        val state = FakeTableStateFactory.create(
            config = RiichiRuleConfig(),
            players = listOf(winner) + List(3) { FakeMahjongPlayerFactory.create() },
        )
        val settledState = state.copy(players = state.players.map { it.copy(playerRuleState = RiichiPlayerState()) })

        val markers = RiichiGameEventPublicMarkerResolver.resolveWinMarkers(
            WinPublicMarkerContext(
                ruleModuleId = "mahjongcraft:riichi",
                matchId = Uuid.random(),
                outcomeId = "mahjongcraft:win",
                winnerId = winner.id,
                previousState = state,
                settledState = settledState,
                detailFields = emptyList(),
            ),
        )

        assertEquals(
            setOf(RiichiGameEventPublicMarkerIds.RIICHI, RiichiGameEventPublicMarkerIds.IPPATSU),
            markers.map { it.id }.toSet(),
        )
        assertTrue(markers.all { it.playerId == winner.id })
    }

    /** 擊飛終局提供門檻與低於門檻的玩家標記。 */
    @Test
    fun `match end markers include threshold and busted players only for bust completion`() {
        val busted = FakeMahjongPlayerFactory.create().copy(score = -100)
        val safe = FakeMahjongPlayerFactory.create().copy(score = 25000)
        val state = FakeTableStateFactory.create(
            config = RiichiRuleConfig(scoreConfig = RiichiScoreConfig(bustThreshold = 0)),
            players = listOf(busted, safe) + List(2) { FakeMahjongPlayerFactory.create() },
        )

        val markers = RiichiGameEventPublicMarkerResolver.resolveMatchEndMarkers(
            MatchEndPublicMarkerContext(
                ruleModuleId = "mahjongcraft:riichi",
                matchId = Uuid.random(),
                completion = MatchCompletion.COMPLETED,
                reasonId = BuiltInMatchEndReasonIds.PLAYER_BUSTED,
                previousState = state,
                finalState = state,
                finalScoresByPlayerId = state.players.associate { it.id to it.score },
            ),
        )

        assertTrue(markers.any { it.id == RiichiGameEventPublicMarkerIds.BUST_THRESHOLD })
        assertEquals(listOf(busted.id), markers.filter { it.id == RiichiGameEventPublicMarkerIds.BUSTED }.map { it.playerId })
    }

    /** 分數剛好等於門檻不算擊飛，且中止或其他終局原因不產生被擊飛標記。 */
    @Test
    fun `busted marker requires a completed bust reason and score below threshold`() {
        val state = FakeTableStateFactory.create(
            config = RiichiRuleConfig(scoreConfig = RiichiScoreConfig(bustThreshold = 0)),
        )
        val scores = state.players.associate { it.id to 0 }

        val atThreshold = RiichiGameEventPublicMarkerResolver.resolveMatchEndMarkers(
            MatchEndPublicMarkerContext(
                ruleModuleId = "mahjongcraft:riichi",
                matchId = Uuid.random(),
                completion = MatchCompletion.COMPLETED,
                reasonId = BuiltInMatchEndReasonIds.PLAYER_BUSTED,
                previousState = state,
                finalState = state,
                finalScoresByPlayerId = scores,
            ),
        )
        assertFalse(atThreshold.any { it.id == RiichiGameEventPublicMarkerIds.BUSTED })

        val aborted = RiichiGameEventPublicMarkerResolver.resolveMatchEndMarkers(
            MatchEndPublicMarkerContext(
                ruleModuleId = "mahjongcraft:riichi",
                matchId = Uuid.random(),
                completion = MatchCompletion.ABORTED,
                reasonId = BuiltInMatchEndReasonIds.PLAYER_BUSTED,
                previousState = state,
                finalState = null,
                finalScoresByPlayerId = mapOf(state.players.first().id to -1),
            ),
        )
        assertFalse(aborted.any { it.id == RiichiGameEventPublicMarkerIds.BUSTED })

        val otherReason = RiichiGameEventPublicMarkerResolver.resolveMatchEndMarkers(
            MatchEndPublicMarkerContext(
                ruleModuleId = "mahjongcraft:riichi",
                matchId = Uuid.random(),
                completion = MatchCompletion.COMPLETED,
                reasonId = "mahjongcraft:schedule_completed",
                previousState = state,
                finalState = state,
                finalScoresByPlayerId = mapOf(state.players.first().id to -1),
            ),
        )
        assertFalse(otherReason.any { it.id == RiichiGameEventPublicMarkerIds.BUSTED })
    }

    /** 沒有擊飛門檻的日麻設定不產生擊飛門檻或玩家標記。 */
    @Test
    fun `no bust threshold produces no match end markers`() {
        val state = FakeTableStateFactory.create(
            config = RiichiRuleConfig(scoreConfig = RiichiScoreConfig(bustThreshold = null)),
        )

        val markers = RiichiGameEventPublicMarkerResolver.resolveMatchEndMarkers(
            MatchEndPublicMarkerContext(
                ruleModuleId = "mahjongcraft:riichi",
                matchId = Uuid.random(),
                completion = MatchCompletion.COMPLETED,
                reasonId = BuiltInMatchEndReasonIds.PLAYER_BUSTED,
                previousState = state,
                finalState = state,
                finalScoresByPlayerId = state.players.associate { it.id to -1 },
            ),
        )

        assertNull(markers.firstOrNull())
    }
}
