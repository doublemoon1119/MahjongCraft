package com.doublemoon1119.mahjongcraft.platform.fabric.client.history

import com.doublemoon1119.mahjongcraft.flow.network.dto.message.HistoryReplayDiscardDto
import com.doublemoon1119.mahjongcraft.flow.network.dto.message.HistoryReplayIdentityDto
import com.doublemoon1119.mahjongcraft.flow.network.dto.message.HistoryReplayMeldDto
import com.doublemoon1119.mahjongcraft.flow.network.dto.message.HistoryReplayPlayerIdentityDto
import com.doublemoon1119.mahjongcraft.flow.network.dto.message.HistoryReplayPlayerStateDto
import com.doublemoon1119.mahjongcraft.flow.network.dto.message.HistoryReplayTileAreaDto
import com.doublemoon1119.mahjongcraft.flow.network.dto.message.HistoryReplayWinningHandDto
import com.doublemoon1119.mahjongcraft.flow.network.dto.message.HistoryRoundOutcomeDto
import com.doublemoon1119.mahjongcraft.flow.network.dto.message.HistoryRoundPositionDto
import com.doublemoon1119.mahjongcraft.flow.network.dto.message.HistoryRoundStateDto
import com.doublemoon1119.mahjongcraft.flow.network.dto.message.HistoryWinnerDetailsDto
import com.doublemoon1119.mahjongcraft.flow.network.dto.model.MeldTypeDto
import com.doublemoon1119.mahjongcraft.flow.network.dto.model.RelativeDirectionDto
import com.doublemoon1119.mahjongcraft.flow.network.dto.model.SuitDto
import com.doublemoon1119.mahjongcraft.flow.network.dto.model.TileDto
import com.doublemoon1119.mahjongcraft.flow.network.dto.model.TileFaceViewDto
import com.doublemoon1119.mahjongcraft.flow.network.dto.model.WindDto
import com.doublemoon1119.mahjongcraft.flow.network.dto.snapshot.MatchRoundPhaseDto
import com.doublemoon1119.mahjongcraft.flow.network.dto.snapshot.MatchRoundPositionDto
import com.doublemoon1119.mahjongcraft.platform.minecraft.decision.DecisionTileOrientationDto
import com.doublemoon1119.mahjongcraft.platform.minecraft.history.HistoryDiscardMarkerDisplay
import com.doublemoon1119.mahjongcraft.platform.minecraft.history.HistoryDiscardMarkerDisplayDefinition
import com.doublemoon1119.mahjongcraft.platform.minecraft.history.HistoryDiscardMarkerDisplayRegistryImpl
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
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

    /** 贏家卡片的手牌把和牌張分開；榮和的和牌張不在手牌中，取自和牌描述。 */
    @Test
    fun `card hand separates the winning tile for tsumo and ron winners`() {
        val tsumoPlayer = player(handTiles = listOf(1, 0), lastDrawn = 2)
        val tsumoState = state(players = listOf(tsumoPlayer), winners = listOf(winner(0, HistoryReplayWinningHandDto(listOf(1, 0), 2))))
        val ronPlayer = player(handTiles = listOf(1, 0))
        val ronState = state(players = listOf(ronPlayer), winners = listOf(winner(0, HistoryReplayWinningHandDto(listOf(1, 0), 5))))

        assertEquals(listOf(1, 0, 2), HistoryRoundStatePresenter.cardHand(tsumoState, tsumoPlayer, listOf(1, 0), 200f).placements.map { it.tile })
        assertEquals(listOf(1, 0, 5), HistoryRoundStatePresenter.cardHand(ronState, ronPlayer, listOf(1, 0), 200f).placements.map { it.tile })
    }

    /** 非贏家或和牌描述對不上時，照一般手牌顯示，最近摸牌分開放。 */
    @Test
    fun `card hand falls back to the plain hand without a matching winner`() {
        val drawing = player(handTiles = listOf(0, 1), lastDrawn = 2)
        val other = player(seat = 1, handTiles = listOf(3))
        val mismatched = state(players = listOf(drawing, other), winners = listOf(winner(0, HistoryReplayWinningHandDto(listOf(0, 4), 2))))

        assertEquals(listOf(0, 1, 2), HistoryRoundStatePresenter.cardHand(state(players = listOf(drawing)), drawing, listOf(0, 1), 200f).placements.map { it.tile })
        assertEquals(listOf(0, 1, 2), HistoryRoundStatePresenter.cardHand(mismatched, drawing, listOf(0, 1), 200f).placements.map { it.tile })
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

    /** 和牌手牌只含立牌與和牌張；副露另外以一列呈現，記下被鳴的牌來自哪個座位與加槓補上的牌。 */
    @Test
    fun `winning hand leaves melds to a separate row with sources and added kan tiles`() {
        val addedKan = HistoryReplayMeldDto(
            MeldTypeDto.AddedKan,
            listOf(3, 4, 5, 6),
            sourceTile = 4,
            sourceDirection = RelativeDirectionDto.Left,
            faceDownTileIds = emptySet(),
            publiclyRevealedTileIds = emptySet(),
        )
        val pon = HistoryReplayMeldDto(
            MeldTypeDto.Pon,
            listOf(7, 8, 9),
            sourceTile = 9,
            sourceDirection = RelativeDirectionDto.Across,
            faceDownTileIds = emptySet(),
            publiclyRevealedTileIds = emptySet(),
        )
        val winner = player(1, handTiles = listOf(0, 1), melds = listOf(addedKan, pon))
        val state = state(players = listOf(player(0, emptyList()), winner, player(2, emptyList()), player(3, emptyList())))

        val hand = HistoryRoundStatePresenter.winningHand(state, winner(1, HistoryReplayWinningHandDto(listOf(0, 1), null)), 300f)
        val melds = assertNotNull(HistoryRoundStatePresenter.exposedTiles(state, winner, 300f))

        assertEquals(listOf(0, 1), hand?.placements?.map { it.tile })
        assertEquals(listOf(3, 4, 5, 6, 7, 8, 9), melds.layout.placements.map { it.tile })
        assertTrue(melds.layout.placements.all { it.orientation == DecisionTileOrientationDto.UPRIGHT })
        assertEquals(mapOf(4 to 0, 9 to 3), melds.claimedFromSeat)
        assertEquals(setOf(6), melds.addedKanTiles)
        assertNull(HistoryRoundStatePresenter.exposedTiles(state, state.players[0], 300f))
    }

    /** 副露與移出牌區排在同一列，副露在前；空的移出牌區不列入。 */
    @Test
    fun `exposed tiles put melds and set aside areas on one row`() {
        val pon = HistoryReplayMeldDto(
            MeldTypeDto.Pon,
            listOf(1, 2, 3),
            sourceTile = 3,
            sourceDirection = RelativeDirectionDto.Left,
            faceDownTileIds = emptySet(),
            publiclyRevealedTileIds = emptySet(),
        )
        val owner = player(0, melds = listOf(pon)).copy(
            tileAreas = listOf(HistoryReplayTileAreaDto("test:flowers", listOf(4, 5)), HistoryReplayTileAreaDto("test:empty", emptyList())),
        )
        val state = state(players = listOf(owner, player(1), player(2), player(3)))

        val exposed = assertNotNull(HistoryRoundStatePresenter.exposedTiles(state, owner, 300f))

        assertEquals(listOf(1, 2, 3, 4, 5), exposed.layout.placements.map { it.tile })
        assertEquals(exposed.layout.placements[0].y, exposed.layout.placements[4].y)
        assertTrue(exposed.hasMelds)
        assertEquals(listOf("test:flowers"), exposed.areaIds)

        val flowersOnly = player(1).copy(tileAreas = listOf(HistoryReplayTileAreaDto("test:flowers", listOf(6))))
        val flowers = assertNotNull(HistoryRoundStatePresenter.exposedTiles(state, flowersOnly, 300f))
        assertFalse(flowers.hasMelds)
        assertEquals(listOf(6), flowers.layout.placements.map { it.tile })
    }

    /** 牌河中被鳴走的牌對應到記錄它為被鳴牌的副露擁有者。 */
    @Test
    fun `taken discards map to the player whose meld claimed them`() {
        val chi = HistoryReplayMeldDto(
            MeldTypeDto.Chi,
            listOf(1, 2, 3),
            sourceTile = 2,
            sourceDirection = RelativeDirectionDto.Left,
            faceDownTileIds = emptySet(),
            publiclyRevealedTileIds = emptySet(),
        )
        val closedKan = HistoryReplayMeldDto(
            MeldTypeDto.ClosedKan,
            listOf(4, 5, 6, 0),
            sourceTile = null,
            sourceDirection = RelativeDirectionDto.Self,
            faceDownTileIds = emptySet(),
            publiclyRevealedTileIds = emptySet(),
        )
        val discarder = player(0).copy(discards = listOf(HistoryReplayDiscardDto(2, true, emptySet())))
        val state = state(players = listOf(discarder, player(1, melds = listOf(chi)), player(2, melds = listOf(closedKan))))

        assertEquals(mapOf(2 to 1), HistoryRoundStatePresenter.takenBySeat(state))
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
            register(HistoryDiscardMarkerDisplayDefinition("example:declared", HistoryDiscardMarkerDisplay(labelTranslationKey = "test.declared", sideways = true)))
        }
        val layout = HistoryRoundStatePresenter.discards(player, 200f, markers)

        assertEquals(listOf(0, 1, 2), layout.placements.map { it.tile })
        assertEquals(DecisionTileOrientationDto.UPRIGHT, layout.placements[0].orientation)
        assertEquals(DecisionTileOrientationDto.ROTATED_RIGHT, layout.placements[1].orientation)
        assertEquals(DecisionTileOrientationDto.UPRIGHT, layout.placements[2].orientation)
    }

    /**
     * 建立單一贏家明細。
     *
     * @param seat 贏家座位索引。
     * @param hand 已保存的和牌手牌描述；null 表示缺少描述。
     * @return 贏家詳情 DTO。
     */
    private fun winner(seat: Int, hand: HistoryReplayWinningHandDto?) = HistoryWinnerDetailsDto(seat, emptyList(), hand)

    /**
     * 建立玩家桌況。
     *
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
    ) = HistoryReplayPlayerStateDto(seat, handTiles, melds, lastDrawn, emptyList(), 25000, WindDto.EAST, emptyList())

    /**
     * 建立包含指定玩家的歷史桌況。
     *
     * @param players 玩家桌況列表。
     * @param winners 本局贏家詳情；null 表示沒有結算。
     * @return 歷史桌況 DTO。
     */
    private fun state(
        players: List<HistoryReplayPlayerStateDto>,
        winners: List<HistoryWinnerDetailsDto>? = null,
    ) = HistoryRoundStateDto(
        identity = HistoryReplayIdentityDto(
            TEST_MATCH_ID,
            TEST_TABLE_ID,
            players.map { HistoryReplayPlayerIdentityDto(it.initialSeatIndex, null, "test:ai") },
        ),
        roundNumber = 1,
        position = HistoryRoundPositionDto.Initial,
        tileCatalog = (0..6).map { TileFaceViewDto(TileDto.Numeric(SuitDto.CHARACTER, (it % 9) + 1), emptyList(), null) },
        players = players,
        wallTiles = emptyList(),
        reservedTiles = emptyList(),
        currentPlayerSeat = players.first().initialSeatIndex,
        dealerSeat = players.first().initialSeatIndex,
        prevalentWind = WindDto.EAST,
        roundPosition = MatchRoundPositionDto(0, WindDto.EAST, 1, MatchRoundPhaseDto.REGULAR),
        comboCount = 0,
        finishedPlayerSeats = emptySet(),
        hasPendingReaction = false,
        hasPendingRobbingReaction = false,
        outcome = winners?.let {
            HistoryRoundOutcomeDto(
                reasonId = "test:win",
                beneficiarySeats = it.map(HistoryWinnerDetailsDto::seatIndex),
                scoresBySeat = emptyMap(),
                classification = null,
                responsibleSeats = emptyList(),
                transitionDirective = null,
                nextDealerSeat = null,
                scoreChangesBySeat = emptyMap(),
                winnerDetails = it,
                hasEarlierWinSettlement = false,
            )
        },
    )
}
