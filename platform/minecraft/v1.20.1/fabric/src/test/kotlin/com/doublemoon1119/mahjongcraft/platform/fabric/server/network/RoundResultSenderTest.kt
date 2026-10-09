package com.doublemoon1119.mahjongcraft.platform.fabric.server.network

import com.doublemoon1119.mahjongcraft.flow.common.game.model.ScoreRankingPlayer
import com.doublemoon1119.mahjongcraft.flow.common.game.model.ScoreRankingPresentation
import com.doublemoon1119.mahjongcraft.platform.minecraft.settlement.RoundResultKindDto
import com.doublemoon1119.mahjongcraft.platform.minecraft.settlement.RoundResultPlayerDto
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.uuid.Uuid

/** 結算結果的內容與收件玩家。 */
class RoundResultSenderTest {
    private val human = ScoreRankingPlayer(Uuid.random(), seatIndex = 1, isAi = false, previousScore = 25_000, currentScore = 43_000, previousRank = 2, currentRank = 1)
    private val ai = ScoreRankingPlayer(Uuid.random(), seatIndex = 0, isAi = true, previousScore = 25_000, currentScore = 7_000, previousRank = 1, currentRank = 2)
    private val ranking = ScoreRankingPresentation(listOf(human, ai))

    /** 結算結果原樣帶出每位玩家結算前後的分數與名次，依固定座位排列。 */
    @Test
    fun `payload carries the ranking values in seat order`() {
        val gameId = Uuid.random()

        val payload = roundResultPayload(
            gameId = gameId,
            ruleModuleId = "mahjongcraft:riichi",
            outcomeId = "mahjongcraft:tsumo",
            kind = RoundResultKindDto.WIN,
            roundContinues = true,
            ranking = ranking,
        )

        assertEquals(gameId.toString(), payload.gameId)
        assertEquals("mahjongcraft:riichi", payload.ruleModuleId)
        assertEquals("mahjongcraft:tsumo", payload.outcomeId)
        assertEquals(RoundResultKindDto.WIN, payload.kind)
        assertEquals(true, payload.roundContinues)
        assertEquals(
            listOf(
                RoundResultPlayerDto(ai.playerId.toString(), 0, true, 25_000, 7_000, 1, 2),
                RoundResultPlayerDto(human.playerId.toString(), 1, false, 25_000, 43_000, 2, 1),
            ),
            payload.players,
        )
    }

    /** 只送給不由 AI 操控的玩家。 */
    @Test
    fun `only human players receive the result`() {
        assertEquals(listOf(human.playerId), roundResultRecipients(ranking))
    }
}
