package com.doublemoon1119.mahjongcraft.platform.fabric.client.history

import com.doublemoon1119.mahjongcraft.flow.network.dto.message.HistoryReplayDiscardDto
import com.doublemoon1119.mahjongcraft.flow.network.dto.message.HistoryReplayIdentityDto
import com.doublemoon1119.mahjongcraft.flow.network.dto.message.HistoryReplayMeldDto
import com.doublemoon1119.mahjongcraft.flow.network.dto.message.HistoryReplayPlayerIdentityDto
import com.doublemoon1119.mahjongcraft.flow.network.dto.message.HistoryReplayPlayerStateDto
import com.doublemoon1119.mahjongcraft.flow.network.dto.message.HistoryReplayWinningHandDto
import com.doublemoon1119.mahjongcraft.flow.network.dto.message.HistoryRoundPositionDto
import com.doublemoon1119.mahjongcraft.flow.network.dto.message.HistoryRoundStateDto
import com.doublemoon1119.mahjongcraft.flow.network.dto.message.HistoryWinnerDetailsDto
import com.doublemoon1119.mahjongcraft.flow.network.dto.model.MeldTypeDto
import com.doublemoon1119.mahjongcraft.flow.network.dto.model.RelativeDirectionDto
import com.doublemoon1119.mahjongcraft.flow.network.dto.model.SuitDto
import com.doublemoon1119.mahjongcraft.flow.network.dto.model.TileDto
import com.doublemoon1119.mahjongcraft.flow.network.dto.model.WindDto
import com.doublemoon1119.mahjongcraft.flow.network.dto.snapshot.MatchRoundPhaseDto
import com.doublemoon1119.mahjongcraft.flow.network.dto.snapshot.MatchRoundPositionDto
import com.doublemoon1119.mahjongcraft.platform.minecraft.decision.DecisionTileOrientationDto
import com.doublemoon1119.mahjongcraft.platform.minecraft.history.HistoryDiscardMarkerDisplay
import com.doublemoon1119.mahjongcraft.platform.minecraft.history.HistoryDiscardMarkerDisplayRegistryImpl
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** 測試用 canonical 對局 UUID。 */
private const val TEST_MATCH_ID = "00000000-0000-0000-0000-000000000001"

/** 測試用 canonical 牌桌 UUID。 */
private const val TEST_TABLE_ID = "00000000-0000-0000-0000-000000000002"

/** 驗證歷史牌面 presenter 只依保存描述呈現手牌、和牌張與副露。 */
class HistoryRoundStatePresentationTest {
    /** 同一份精確描述可分別呈現自摸與榮和，且保留立牌順序。 */
    @Test
    fun `winning hand preserves descriptor order for tsumo and ron`() {
        val player = player(handTiles = listOf(0, 1), lastDrawn = 2)
        val tsumo = HistoryRoundStatePresenter.winningHand(
            state(players = listOf(player)),
            winner(0, HistoryReplayWinningHandDto(listOf(1, 0), 2)),
            200f,
        )
        val ron = HistoryRoundStatePresenter.winningHand(
            state(players = listOf(player.copy(lastDrawn = null))),
            winner(0, HistoryReplayWinningHandDto(listOf(1, 0), 2)),
            200f,
        )

        assertEquals(listOf(1, 0, 2), tsumo?.placements?.map { it.tile })
        assertEquals(listOf(1, 0, 2), ron?.placements?.map { it.tile })
    }

    /** 缺少和牌描述或描述與保存手牌成員不一致時不猜測。 */
    @Test
    fun `winning hand is unavailable without exact descriptor membership`() {
        val state = state(players = listOf(player(handTiles = listOf(0, 1))))
        assertNull(HistoryRoundStatePresenter.winningHand(state, winner(0, null), 200f))
        assertNull(HistoryRoundStatePresenter.winningHand(state, winner(0, HistoryReplayWinningHandDto(listOf(0, 2), null)), 200f))
    }

    /** 多名贏家各自可引用同一張外部和牌張，呈現彼此獨立。 */
    @Test
    fun `multiple winners may present the same external winning tile independently`() {
        val state = state(
            players = listOf(
                player(0, listOf(0)),
                player(1, listOf(2)),
            ),
        )
        val hand = HistoryReplayWinningHandDto(listOf(0), 1)

        assertNotNull(HistoryRoundStatePresenter.winningHand(state, winner(0, hand), 200f))
        assertNotNull(HistoryRoundStatePresenter.winningHand(state, winner(1, HistoryReplayWinningHandDto(listOf(2), 1)), 200f))
    }

    /** 可用牌集合以手牌加最近摸牌組成，但相同索引不重複。 */
    @Test
    fun `hand and last drawn tile do not duplicate the same tile`() {
        val layout = HistoryRoundStatePresenter.hand(player(handTiles = listOf(0), lastDrawn = 0), 200f)

        assertEquals(listOf(0), layout.placements.map { it.tile })
    }

    /** 副露保留來源橫置牌與加槓疊放牌。 */
    @Test
    fun `winning hand includes sideways and added kan meld placements`() {
        val addedKan = HistoryReplayMeldDto(
            MeldTypeDto.AddedKan,
            listOf(3, 4, 5, 6),
            sourceTile = 4,
            sourceDirection = RelativeDirectionDto.Left,
        )
        val state = state(players = listOf(player(handTiles = listOf(0, 1), melds = listOf(addedKan))))
        val layout = HistoryRoundStatePresenter.winningHand(
            state,
            winner(0, HistoryReplayWinningHandDto(listOf(0, 1), null)),
            300f,
        )

        assertNotNull(layout)
        val meldPlacements = layout.placements.drop(2)
        assertEquals(4, meldPlacements.size)
        assertTrue(meldPlacements.any { it.tile == 6 && it.y < 1f })
        assertTrue(meldPlacements.any { it.orientation.name.contains("ROTATED") })
    }

    /** 群組保留順序並在超過寬度時整組換行。 */
    @Test
    fun `combine wraps groups while preserving group order`() {
        val first = HistoryRoundStatePresenter.hand(player(handTiles = listOf(0)), 30f)
        val second = HistoryRoundStatePresenter.hand(player(handTiles = listOf(1)), 30f)
        val combined = HistoryRoundStatePresenter.combine(listOf(first, second), 30f)

        assertEquals(listOf(0, 1), combined.placements.map { it.tile })
        assertTrue(combined.placements[1].y > combined.placements[0].y)
    }

    /** 牌河保留登記為橫擺的標記與已被取走的原槽位，未登記的標記不造成橫置。 */
    @Test
    fun `discards tilt registered sideways markers keep taken slots and ignore unknown markers`() {
        val player = player().copy(
            discards = listOf(
                HistoryReplayDiscardDto(0, true, emptySet()),
                HistoryReplayDiscardDto(1, false, setOf("example:declared")),
                HistoryReplayDiscardDto(2, false, setOf("custom:marker")),
            ),
        )
        val markers = HistoryDiscardMarkerDisplayRegistryImpl().apply {
            register("example:declared", HistoryDiscardMarkerDisplay(labelTranslationKey = "test.declared", sideways = true))
        }
        val layout = HistoryRoundStatePresenter.discards(player, 200f, markers)

        assertEquals(listOf(0, 1, 2), layout.placements.map { it.tile })
        assertEquals(DecisionTileOrientationDto.UPRIGHT, layout.placements[0].orientation)
        assertEquals(DecisionTileOrientationDto.ROTATED_RIGHT, layout.placements[1].orientation)
        assertEquals(DecisionTileOrientationDto.UPRIGHT, layout.placements[2].orientation)
    }

    /** 建立單一贏家明細。
     * @param seat 贏家座位索引。
     * @param hand 已保存的和牌手牌描述；null 表示缺少描述。
     * @return 贏家詳情 DTO。
     */
    private fun winner(seat: Int, hand: HistoryReplayWinningHandDto?) = HistoryWinnerDetailsDto(seat, emptyList(), hand)

    /** 建立玩家桌況。
     * @param seat 玩家座位索引。
     * @param handTiles 保存的立牌索引。
     * @param lastDrawn 最近摸牌索引。
     * @param melds 保存的副露。
     * @return 玩家桌況 DTO。
     */
    private fun player(
        seat: Int = 0,
        handTiles: List<Int> = emptyList(),
        lastDrawn: Int? = null,
        melds: List<HistoryReplayMeldDto> = emptyList(),
    ) = HistoryReplayPlayerStateDto(seat, handTiles, melds, lastDrawn, emptyList(), 25000, WindDto.EAST, null, emptyList())

    /** 建立包含指定玩家的歷史桌況。
     * @param players 玩家桌況列表。
     * @return 歷史桌況 DTO。
     */
    private fun state(players: List<HistoryReplayPlayerStateDto>) = HistoryRoundStateDto(
        identity = HistoryReplayIdentityDto(
            TEST_MATCH_ID,
            TEST_TABLE_ID,
            players.map { HistoryReplayPlayerIdentityDto(it.initialSeatIndex, null, "test:ai") },
        ),
        roundNumber = 1,
        position = HistoryRoundPositionDto.Initial,
        tileCatalog = (0..6).map { TileDto.Numeric(SuitDto.CHARACTER, (it % 9) + 1) },
        players = players,
        wallTiles = emptyList(),
        reservedTiles = emptyList(),
        currentPlayerSeat = players.first().initialSeatIndex,
        dealerSeat = players.first().initialSeatIndex,
        prevalentWind = WindDto.EAST,
        roundPosition = MatchRoundPositionDto(0, WindDto.EAST, 1, MatchRoundPhaseDto.REGULAR),
        comboCount = 0,
        finishedPlayerSeats = emptySet(),
        dynamicRuleState = null,
        hasPendingReaction = false,
        hasPendingRobbingReaction = false,
        outcome = null,
    )
}
