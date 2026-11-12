package com.doublemoon1119.mahjongcraft.flow.server.game.taiwan

import com.doublemoon1119.mahjongcraft.bundled.TaiwanWinSettlementDetailResolver
import com.doublemoon1119.mahjongcraft.flow.common.game.model.ResolvedRoundOutcome
import com.doublemoon1119.mahjongcraft.flow.common.game.model.RoundOutcomePresentationClassification
import com.doublemoon1119.mahjongcraft.flow.common.game.model.WinSettlementDetailEntry
import com.doublemoon1119.mahjongcraft.flow.common.game.model.WinSettlementDetailValue
import com.doublemoon1119.mahjongcraft.flow.common.game.model.WinSettlementQuantity
import com.doublemoon1119.mahjongcraft.flow.common.game.model.taiwan.TaiwanRoundOutcomeIds
import com.doublemoon1119.mahjongcraft.flow.common.game.model.taiwan.TaiwanSpecialFlowerWinDetail
import com.doublemoon1119.mahjongcraft.flow.common.game.model.taiwan.TaiwanWinSettlementIds
import com.doublemoon1119.mahjongcraft.logic.base.IdentifiedTile
import com.doublemoon1119.mahjongcraft.logic.base.Tile
import com.doublemoon1119.mahjongcraft.logic.base.TileFaceView
import com.doublemoon1119.mahjongcraft.logic.base.TileValue
import com.doublemoon1119.mahjongcraft.logic.judgment.HandValueResult
import com.doublemoon1119.mahjongcraft.logic.rules.taiwan.TaiwanHandValueResult
import com.doublemoon1119.mahjongcraft.logic.rules.taiwan.TaiwanPlayerTileAreaIds
import com.doublemoon1119.mahjongcraft.logic.rules.taiwan.TaiwanRuleConfig
import com.doublemoon1119.mahjongcraft.logic.rules.taiwan.tai.TaiwanTaiResult
import com.doublemoon1119.mahjongcraft.logic.rules.taiwan.tai.TaiwanTaiType
import com.doublemoon1119.mahjongcraft.logic.rules.taiwan.tai.flower.calculateTaiwanEightFlowers
import com.doublemoon1119.mahjongcraft.logic.rules.taiwan.tile.TaiwanTileTypes
import com.doublemoon1119.mahjongcraft.logic.table.MahjongPlayer
import com.doublemoon1119.mahjongcraft.logic.table.RoundTransitionDirective
import com.doublemoon1119.mahjongcraft.logic.table.TableState
import com.doublemoon1119.mahjongcraft.testing.logic.base.FakeIdentifiedTileFactory
import com.doublemoon1119.mahjongcraft.testing.logic.table.FakeMahjongPlayerFactory
import com.doublemoon1119.mahjongcraft.testing.logic.table.FakeTableStateFactory
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.uuid.Uuid

/** 驗證台灣麻將結算詳情中的花牌、台種、莊家台與合計台數欄位。 */
class TaiwanWinSettlementDetailResolverTest {
    /** 依牌值直接轉成牌面，測試不需要公開外觀裁切。 */
    private val publicFace: (TileValue) -> TileFaceView = { TileFaceView(it.tile) }

    /** 一般胡牌沒有額外資料。 */
    private val handValue = object : HandValueResult {}

    /** 八張花牌，依公開順序排列。 */
    private val flowers = TaiwanTileTypes.ALL.map { FakeIdentifiedTileFactory.create(Tile.Extension(it)) }

    /** 一般胡牌列出贏家所有公開的花牌；沒有花牌時不提供欄位，不顯示那一行。 */
    @Test
    fun `normal win lists the winner flowers only when there are any`() {
        val winner = withFlowers(FakeMahjongPlayerFactory.create(), flowers.take(2))
        val other = withFlowers(FakeMahjongPlayerFactory.create(), flowers.drop(2).take(1))
        val state = table(winner, other)

        val fields = TaiwanWinSettlementDetailResolver.resolve(state, winner.id, null, handValue, publicFace).associate { it.id to it.value }
        val withoutFlowers = FakeMahjongPlayerFactory.create()
        val none = TaiwanWinSettlementDetailResolver.resolve(table(withoutFlowers), withoutFlowers.id, null, handValue, publicFace)

        assertEquals(faces(flowers.take(2)), fields[TaiwanWinSettlementIds.FLOWERS_FIELD])
        assertEquals(null, none.firstOrNull { it.id == TaiwanWinSettlementIds.FLOWERS_FIELD })
    }

    /** 莊家八仙過海：前七張一組，讓和牌成立的那張另外一格，再接台種、莊家台與合計台數。 */
    @Test
    fun `eight flowers separate the winning flower`() {
        val winner = withFlowers(FakeMahjongPlayerFactory.create(), flowers)
        val state = table(winner)
        val winningFlower = flowers[3]

        val fields = TaiwanWinSettlementDetailResolver.resolveSpecialOutcome(
            state = state,
            outcome = outcome(state, winner.id, TaiwanRoundOutcomeIds.EIGHT_FLOWERS, calculateTaiwanEightFlowers(), winningFlower.id),
            publicFace = publicFace,
        ).orEmpty().associate { it.id to it.value }

        assertEquals(faces(flowers - winningFlower), fields[TaiwanWinSettlementIds.FLOWERS_FIELD])
        assertEquals(faces(listOf(winningFlower)), fields[TaiwanWinSettlementIds.WINNING_FLOWER_FIELD])
        assertEquals(
            setOf(TaiwanWinSettlementIds.FLOWERS_FIELD, TaiwanWinSettlementIds.WINNING_FLOWER_FIELD, TaiwanWinSettlementIds.TAI_FIELD, TaiwanWinSettlementIds.TAI_TOTAL_FIELD),
            fields.keys,
        )
        assertEquals(tai(calculateTaiwanEightFlowers().tai + 1), fields[TaiwanWinSettlementIds.TAI_TOTAL_FIELD])
    }

    /** 莊家胡牌：手牌台種後面接莊家那一條，每一份付款都算，合計包含莊家台。 */
    @Test
    fun `dealer win counts the dealer tai for every payer`() {
        val (dealer, discarder) = List(2) { FakeMahjongPlayerFactory.create() }
        val state = table(dealer, discarder, dealerId = dealer.id, comboCount = 0)

        val fields = TaiwanWinSettlementDetailResolver.resolve(state, dealer.id, discarder.id, taiwanHand, publicFace).associate { it.id to it.value }

        assertEquals(
            WinSettlementDetailValue.Entries(handEntries + WinSettlementDetailEntry(TaiwanWinSettlementIds.DEALER_ENTRY, WinSettlementQuantity(TaiwanWinSettlementIds.TAI_UNIT, 1))),
            fields[TaiwanWinSettlementIds.TAI_FIELD],
        )
        assertEquals(tai(HAND_TAI + 1), fields[TaiwanWinSettlementIds.TAI_TOTAL_FIELD])
        assertEquals(null, fields[TaiwanWinSettlementIds.DEALER_TAI_TOTAL_FIELD])
    }

    /** 閒家胡莊家的牌：連莊時合併成「連 N 拉 N」一條，台數 2N+1，付款的就是莊家所以合計包含。 */
    @Test
    fun `non dealer winning from the dealer counts the streak`() {
        val (winner, dealer) = List(2) { FakeMahjongPlayerFactory.create() }
        val state = table(winner, dealer, dealerId = dealer.id, comboCount = 2)

        val fields = TaiwanWinSettlementDetailResolver.resolve(state, winner.id, dealer.id, taiwanHand, publicFace).associate { it.id to it.value }

        val streak = WinSettlementDetailEntry(
            id = TaiwanWinSettlementIds.DEALER_STREAK_ENTRY,
            quantity = WinSettlementQuantity(TaiwanWinSettlementIds.TAI_UNIT, 5),
            nameQuantity = WinSettlementQuantity(TaiwanWinSettlementIds.DEALER_STREAK_UNIT, 2),
        )
        assertEquals(WinSettlementDetailValue.Entries(handEntries + streak), fields[TaiwanWinSettlementIds.TAI_FIELD])
        assertEquals(tai(HAND_TAI + 5), fields[TaiwanWinSettlementIds.TAI_TOTAL_FIELD])
    }

    /** 閒家胡閒家的牌：莊家沒有參與付款，不列莊家那一條。 */
    @Test
    fun `non dealer winning from another non dealer omits the dealer tai`() {
        val (winner, discarder, dealer) = List(3) { FakeMahjongPlayerFactory.create() }
        val state = table(winner, discarder, dealer, dealerId = dealer.id, comboCount = 1)

        val fields = TaiwanWinSettlementDetailResolver.resolve(state, winner.id, discarder.id, taiwanHand, publicFace).associate { it.id to it.value }

        assertEquals(WinSettlementDetailValue.Entries(handEntries), fields[TaiwanWinSettlementIds.TAI_FIELD])
        assertEquals(tai(HAND_TAI), fields[TaiwanWinSettlementIds.TAI_TOTAL_FIELD])
    }

    /** 閒家自摸：莊家那一條只適用莊家那一份，合計只算手牌台數，另外列出莊家那一份的合計。 */
    @Test
    fun `non dealer self draw marks the dealer tai as paid by the dealer alone`() {
        val (winner, dealer) = List(2) { FakeMahjongPlayerFactory.create() }
        val state = table(winner, dealer, dealerId = dealer.id, comboCount = 1)

        val fields = TaiwanWinSettlementDetailResolver.resolve(state, winner.id, null, taiwanHand, publicFace).associate { it.id to it.value }

        val streak = WinSettlementDetailEntry(
            id = TaiwanWinSettlementIds.DEALER_STREAK_ENTRY,
            quantity = WinSettlementQuantity(TaiwanWinSettlementIds.TAI_UNIT, 3),
            nameQuantity = WinSettlementQuantity(TaiwanWinSettlementIds.DEALER_STREAK_UNIT, 1),
            appliesToSomePayersOnly = true,
        )
        assertEquals(WinSettlementDetailValue.Entries(handEntries + streak), fields[TaiwanWinSettlementIds.TAI_FIELD])
        assertEquals(tai(HAND_TAI), fields[TaiwanWinSettlementIds.TAI_TOTAL_FIELD])
        assertEquals(tai(HAND_TAI + 3), fields[TaiwanWinSettlementIds.DEALER_TAI_TOTAL_FIELD])
    }

    /** 測試用手牌成立的台種：門清與正花兩張。 */
    private val taiwanHand = TaiwanHandValueResult(
        isWinningHand = true,
        taiResults = listOf(TaiwanTaiResult(TaiwanTaiType.ConcealedHand, 1), TaiwanTaiResult(TaiwanTaiType.SeatFlower, 2)),
        totalTai = HAND_TAI,
        decomposition = null,
    )

    /** [taiwanHand] 對應的清單條目。 */
    private val handEntries = listOf(
        WinSettlementDetailEntry(TaiwanWinSettlementIds.tai(TaiwanTaiType.ConcealedHand), WinSettlementQuantity(TaiwanWinSettlementIds.TAI_UNIT, 1)),
        WinSettlementDetailEntry(TaiwanWinSettlementIds.tai(TaiwanTaiType.SeatFlower), WinSettlementQuantity(TaiwanWinSettlementIds.TAI_UNIT, 2)),
    )

    /**
     * 只有台數的數值欄位值。
     *
     * @param amount 台數。
     * @return 數值欄位值。
     */
    private fun tai(amount: Int) = WinSettlementDetailValue.Quantities(listOf(WinSettlementQuantity(TaiwanWinSettlementIds.TAI_UNIT, amount)))

    /**
     * 組成只有指定玩家的台麻桌況。
     *
     * @param players 座位上的玩家。
     * @param dealerId 莊家；預設為第一位玩家。
     * @param comboCount 連莊次數。
     * @return 桌況。
     */
    private fun table(
        vararg players: MahjongPlayer,
        dealerId: Uuid = players.first().id,
        comboCount: Int = 0,
    ): TableState = FakeTableStateFactory.create(players = players.toList(), dealerPlayerId = dealerId, config = TaiwanRuleConfig(), comboCount = comboCount)

    /**
     * 讓玩家擁有指定的公開花牌。
     *
     * @param player 玩家。
     * @param tiles 公開的花牌。
     * @return 更新後的玩家。
     */
    private fun withFlowers(player: MahjongPlayer, tiles: List<IdentifiedTile>): MahjongPlayer = player.copy(tileAreas = player.tileAreas.withArea(TaiwanPlayerTileAreaIds.FLOWERS, tiles))

    /**
     * 花牌的牌面欄位值。
     *
     * @param tiles 花牌。
     * @return 牌面欄位值。
     */
    private fun faces(tiles: List<IdentifiedTile>) = WinSettlementDetailValue.TileFaces(tiles.map { TileFaceView(it.tile) })

    /**
     * 組成特殊花牌和牌結果。
     *
     * @param state 桌況。
     * @param winnerId 贏家。
     * @param outcomeId 結果識別碼。
     * @param taiResult 台種。
     * @param winningFlowerId 讓和牌成立的花牌。
     * @return 特殊結果。
     */
    private fun outcome(
        state: TableState,
        winnerId: Uuid,
        outcomeId: String,
        taiResult: TaiwanTaiResult,
        winningFlowerId: Uuid,
    ) = ResolvedRoundOutcome(
        id = outcomeId,
        settledTableState = state,
        beneficiaryPlayerIds = setOf(winnerId),
        scoreDeltas = state.players.associate { it.id to 0 },
        transitionDirective = RoundTransitionDirective.PassToNextSeat,
        presentationClassification = RoundOutcomePresentationClassification.WIN_EQUIVALENT,
        extensionDetail = TaiwanSpecialFlowerWinDetail(taiTypeId = TaiwanWinSettlementIds.tai(taiResult.type), tai = taiResult.tai, winningFlowerId = winningFlowerId),
    )

    /** 測試用常數。 */
    private companion object {
        /** [taiwanHand] 的手牌台數。 */
        const val HAND_TAI = 3
    }
}
