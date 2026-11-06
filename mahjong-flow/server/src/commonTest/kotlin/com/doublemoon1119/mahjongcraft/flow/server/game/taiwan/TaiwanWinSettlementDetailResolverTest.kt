package com.doublemoon1119.mahjongcraft.flow.server.game.taiwan

import com.doublemoon1119.mahjongcraft.bundled.TaiwanWinSettlementDetailResolver
import com.doublemoon1119.mahjongcraft.flow.common.game.model.ResolvedRoundOutcome
import com.doublemoon1119.mahjongcraft.flow.common.game.model.RoundOutcomePresentationClassification
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
import com.doublemoon1119.mahjongcraft.logic.rules.taiwan.TaiwanPlayerTileAreaIds
import com.doublemoon1119.mahjongcraft.logic.rules.taiwan.TaiwanRuleConfig
import com.doublemoon1119.mahjongcraft.logic.rules.taiwan.TaiwanTaiType
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

/** 驗證台灣麻將結算詳情中的花牌欄位。 */
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

        val fields = TaiwanWinSettlementDetailResolver.resolve(state, winner.id, handValue, publicFace)
        val withoutFlowers = FakeMahjongPlayerFactory.create()
        val none = TaiwanWinSettlementDetailResolver.resolve(table(withoutFlowers), withoutFlowers.id, handValue, publicFace)

        assertEquals(listOf(TaiwanWinSettlementIds.FLOWERS_FIELD), fields.map { it.id })
        assertEquals(faces(flowers.take(2)), fields.single().value)
        assertEquals(emptyList(), none)
    }

    /** 八仙過海：前七張一組，讓和牌成立的那張另外一格，再接台種與合計台數。 */
    @Test
    fun `eight flowers separate the winning flower`() {
        val winner = withFlowers(FakeMahjongPlayerFactory.create(), flowers)
        val state = table(winner)
        val winningFlower = flowers[3]

        val fields = TaiwanWinSettlementDetailResolver.resolveSpecialOutcome(
            state = state,
            outcome = outcome(state, winner.id, TaiwanRoundOutcomeIds.EIGHT_FLOWERS, TaiwanTaiType.EIGHT_FLOWERS, winningFlower.id),
            publicFace = publicFace,
        ).orEmpty().associate { it.id to it.value }

        assertEquals(faces(flowers - winningFlower), fields[TaiwanWinSettlementIds.FLOWERS_FIELD])
        assertEquals(faces(listOf(winningFlower)), fields[TaiwanWinSettlementIds.WINNING_FLOWER_FIELD])
        assertEquals(
            setOf(TaiwanWinSettlementIds.FLOWERS_FIELD, TaiwanWinSettlementIds.WINNING_FLOWER_FIELD, TaiwanWinSettlementIds.TAI_FIELD, TaiwanWinSettlementIds.TAI_TOTAL_FIELD),
            fields.keys,
        )
        assertEquals(
            WinSettlementDetailValue.Quantities(listOf(WinSettlementQuantity(TaiwanWinSettlementIds.TAI_UNIT, TaiwanTaiType.EIGHT_FLOWERS.tai))),
            fields[TaiwanWinSettlementIds.TAI_TOTAL_FIELD],
        )
    }

    /**
     * 組成只有指定玩家的台麻桌況。
     *
     * @param players 座位上的玩家。
     * @return 桌況。
     */
    private fun table(vararg players: MahjongPlayer): TableState = FakeTableStateFactory.create(players = players.toList(), config = TaiwanRuleConfig())

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
     * @param type 台種。
     * @param winningFlowerId 讓和牌成立的花牌。
     * @return 特殊結果。
     */
    private fun outcome(
        state: TableState,
        winnerId: Uuid,
        outcomeId: String,
        type: TaiwanTaiType,
        winningFlowerId: Uuid,
    ) = ResolvedRoundOutcome(
        id = outcomeId,
        settledTableState = state,
        beneficiaryPlayerIds = setOf(winnerId),
        scoreDeltas = state.players.associate { it.id to 0 },
        transitionDirective = RoundTransitionDirective.PassToNextSeat,
        presentationClassification = RoundOutcomePresentationClassification.WIN_EQUIVALENT,
        extensionDetail = TaiwanSpecialFlowerWinDetail(taiTypeId = type.id, tai = type.tai, winningFlowerId = winningFlowerId),
    )
}
