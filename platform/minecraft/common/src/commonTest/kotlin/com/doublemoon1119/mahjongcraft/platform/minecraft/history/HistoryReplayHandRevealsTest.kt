package com.doublemoon1119.mahjongcraft.platform.minecraft.history

import com.doublemoon1119.mahjongcraft.flow.network.dto.message.HistoryReplayFactDto
import com.doublemoon1119.mahjongcraft.flow.network.dto.message.HistoryReplayTransactionDto
import com.doublemoon1119.mahjongcraft.flow.network.dto.message.HistoryReplayWinningHandDto
import com.doublemoon1119.mahjongcraft.flow.network.dto.message.HistoryRoundOutcomeDto
import com.doublemoon1119.mahjongcraft.flow.network.dto.message.HistoryWinnerDetailsDto
import com.doublemoon1119.mahjongcraft.logic.tile.component.TilePose
import com.doublemoon1119.mahjongcraft.platform.minecraft.tile.HandRevealSteps
import com.doublemoon1119.mahjongcraft.platform.minecraft.tile.MahjongTileTableLayout
import com.doublemoon1119.mahjongcraft.platform.minecraft.tile.MahjongTileWallPlacement
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** 驗證牌譜回放的攤牌判斷與共用的倒牌步驟。 */
class HistoryReplayHandRevealsTest {
    /** 記下手牌的胡牌從那一步之後倒牌；不記手牌的胡牌不倒；胡牌後繼續打時前一位贏家維持倒下。 */
    @Test
    fun `winners reveal from the step after their win`() {
        val transactions = listOf(
            transaction(index = 0, winner(seat = 1, standing = listOf(3, 4), winningTile = 9)),
            transaction(index = 1, winner(seat = 2, standing = emptyList(), winningTile = null)),
            transaction(index = 2, winner(seat = 3, standing = listOf(5), winningTile = null)),
        )
        assertEquals(emptyMap(), HistoryReplayHandReveals.revealedWinners(transactions, step = 0))
        assertEquals(mapOf(1 to 9), HistoryReplayHandReveals.revealedWinners(transactions, step = 1))
        assertEquals(mapOf(1 to 9), HistoryReplayHandReveals.revealedWinners(transactions, step = 2))
        assertEquals(mapOf(1 to 9, 3 to null), HistoryReplayHandReveals.revealedWinners(transactions, step = 3))
    }

    /** 自摸時自摸那張先倒，其餘的牌等它倒完再停一下才倒；榮和時理好後停一下全部一起倒。 */
    @Test
    fun `win timings follow the live game`() {
        val tsumo = HandRevealSteps.winTimings(isTsumo = true)
        val ron = HandRevealSteps.winTimings(isTsumo = false)
        val reorder = MahjongTileTableLayout.WIN_POST_REORDER_DELAY_TICKS.toLong()
        val laydown = MahjongTileTableLayout.WIN_LAYDOWN_DURATION_TICKS.toLong()
        val pause = MahjongTileTableLayout.WIN_PRE_HAND_LAYDOWN_DELAY_TICKS.toLong()
        assertEquals(reorder, tsumo.winningTileLaydown)
        assertEquals(reorder + laydown + pause, tsumo.handLaydown)
        assertNull(ron.winningTileLaydown)
        assertEquals(reorder + pause, ron.handLaydown)
        assertEquals(ron.handLaydown + laydown, ron.end)
    }

    /** 已經是目標姿態的牌沒有動作；整組翻牌聲只放在真的要轉的牌中間那張。 */
    @Test
    fun `turning steps skip tiles already in place`() {
        assertTrue(HandRevealSteps.laydown(TilePose.FACE_UP, startTick = 10L, playSound = true).isEmpty())
        assertTrue(HandRevealSteps.conceal(TilePose.FACE_DOWN, startTick = 10L, playSound = true).isEmpty())
        assertEquals(4, HandRevealSteps.laydown(TilePose.UPRIGHT, startTick = 10L, playSound = true).size)
        assertEquals(2, HandRevealSteps.reorder(MahjongTileWallPlacement(0.0, 1.0, 0.0, 0f), startTick = 0L).size)
        assertEquals(3, HandRevealSteps.groupSoundIndex(listOf(TilePose.UPRIGHT, TilePose.FACE_UP, TilePose.FACE_UP, TilePose.UPRIGHT, TilePose.UPRIGHT)))
        assertNull(HandRevealSteps.groupSoundIndex(listOf(TilePose.FACE_UP)))
    }

    /**
     * 建立只有一筆胡牌結算的交易。
     *
     * @param index 交易索引。
     * @param winner 贏家。
     * @return 交易。
     */
    private fun transaction(index: Int, winner: HistoryWinnerDetailsDto): HistoryReplayTransactionDto = HistoryReplayTransactionDto(
        index = index,
        occurredAtEpochMillis = 0L,
        isOpening = false,
        declaredTileCountAfter = 0,
        facts = listOf(
            element = HistoryReplayFactDto.Completion(
                typeKey = "test:win",
                outcome = HistoryRoundOutcomeDto(
                    reasonId = "test:win",
                    beneficiarySeats = listOf(winner.seatIndex),
                    scoresBySeat = emptyMap(),
                    classification = null,
                    responsibleSeats = emptyList(),
                    transitionDirective = null,
                    nextDealerSeat = null,
                    scoreChangesBySeat = emptyMap(),
                    winnerDetails = listOf(winner),
                    hasEarlierWinSettlement = false,
                    winningTile = null,
                ),
            ),
        ),
    )

    /**
     * 建立一位贏家。
     *
     * @param seat 初始座位。
     * @param standing 記下的立牌；空的代表不攤牌。
     * @param winningTile 和牌張。
     * @return 贏家詳情。
     */
    private fun winner(seat: Int, standing: List<Int>, winningTile: Int?): HistoryWinnerDetailsDto = HistoryWinnerDetailsDto(
        seatIndex = seat,
        detailFields = emptyList(),
        hand = HistoryReplayWinningHandDto(standingTiles = standing, winningTile = winningTile),
    )
}
