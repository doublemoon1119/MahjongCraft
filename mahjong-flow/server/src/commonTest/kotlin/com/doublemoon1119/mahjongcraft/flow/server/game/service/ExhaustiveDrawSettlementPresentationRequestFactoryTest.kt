package com.doublemoon1119.mahjongcraft.flow.server.game.service

import com.doublemoon1119.mahjongcraft.flow.common.game.model.BuiltInExhaustiveDrawSettlementStatusIds
import com.doublemoon1119.mahjongcraft.flow.common.game.model.ExhaustiveDrawHandDisclosure
import com.doublemoon1119.mahjongcraft.logic.base.Hand
import com.doublemoon1119.mahjongcraft.logic.base.Meld
import com.doublemoon1119.mahjongcraft.logic.base.MeldType
import com.doublemoon1119.mahjongcraft.logic.base.PublicHandOrder
import com.doublemoon1119.mahjongcraft.logic.base.RelativeDirection
import com.doublemoon1119.mahjongcraft.logic.base.Tile
import com.doublemoon1119.mahjongcraft.logic.module.RevealedHandSettlement
import com.doublemoon1119.mahjongcraft.logic.rules.riichi.RiichiExhaustiveDrawReason
import com.doublemoon1119.mahjongcraft.logic.rules.riichi.RiichiRuleConfig
import com.doublemoon1119.mahjongcraft.logic.rules.riichi.RiichiRuleModule
import com.doublemoon1119.mahjongcraft.logic.table.Wind
import com.doublemoon1119.mahjongcraft.testing.logic.base.FakeHandFactory
import com.doublemoon1119.mahjongcraft.testing.logic.base.FakeIdentifiedTileFactory
import com.doublemoon1119.mahjongcraft.testing.logic.table.FakeMahjongPlayerFactory
import com.doublemoon1119.mahjongcraft.testing.logic.table.FakeTableStateFactory
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** [ExhaustiveDrawSettlementPresentationRequestFactory] 的手牌公開策略與玩家數測試。 */
class ExhaustiveDrawSettlementPresentationRequestFactoryTest {
    /** 各測試使用的日麻設定。 */
    private val config = RiichiRuleConfig()

    /** 解析流局揭露政策與牌序的日麻模組。 */
    private val module = RiichiRuleModule("mahjongcraft:riichi", config)

    /** 一般流局應翻開聽牌者並蓋起未聽者。 */
    @Test
    fun `normal draw reveals tenpai hands and conceals noten hands`() {
        val state = tableState(4)
        val tenpaiPlayer = state.players.first()
        val revealedState = state.copy(revealedHandTileIds = tenpaiPlayer.hand.allTiles.map { it.id }.toSet())
        val request = ExhaustiveDrawSettlementPresentationRequestFactory.create(
            previousState = state,
            currentState = revealedState,
            module = module,
            aiPlayerIds = emptySet(),
            reason = RiichiExhaustiveDrawReason.Normal,
            tenpaiPlayerIds = setOf(tenpaiPlayer.id),
            revealedHands = listOf(RevealedHandSettlement(tenpaiPlayer.id, setOf(Tile.Numeric(Tile.Suit.Character, 1)))),
        )

        assertEquals(ExhaustiveDrawHandDisclosure.TENPAI, request.players.first().handDisclosure)
        assertEquals(ExhaustiveDrawHandDisclosure.CONCEALED, request.players[1].handDisclosure)
        assertEquals(tenpaiPlayer.hand.allTiles.map { it.id }, request.players.first().revealedHandTileIds)
        assertEquals(emptyList(), request.players[1].revealedHandTileIds)
    }

    /** 排名只把傳入的 AI 玩家標成 AI。 */
    @Test
    fun `ranking marks only the given players as ai`() {
        val state = tableState(4)
        val aiPlayer = state.players[1]
        val request = ExhaustiveDrawSettlementPresentationRequestFactory.create(
            previousState = state,
            currentState = state,
            module = module,
            aiPlayerIds = setOf(aiPlayer.id),
            reason = RiichiExhaustiveDrawReason.Normal,
            tenpaiPlayerIds = emptySet(),
            revealedHands = emptyList(),
        )

        assertEquals(listOf(aiPlayer.id), request.players.filter { it.ranking.isAi }.map { it.ranking.playerId })
    }

    /** 九種九牌只公開宣告者，其餘玩家必須蓋牌。 */
    @Test
    fun `abortive proof reveals declarer and conceals other hands`() {
        val state = tableState(4)
        val declarer = state.players[2]
        val revealedState = state.copy(revealedHandTileIds = declarer.hand.allTiles.map { it.id }.toSet())
        val request = ExhaustiveDrawSettlementPresentationRequestFactory.create(
            previousState = state,
            currentState = revealedState,
            module = module,
            aiPlayerIds = emptySet(),
            reason = RiichiExhaustiveDrawReason.KyuushuKyuuhai,
            tenpaiPlayerIds = null,
            revealedHands = listOf(RevealedHandSettlement(declarer.id, emptySet())),
        )

        assertEquals(ExhaustiveDrawHandDisclosure.PROOF, request.players[2].handDisclosure)
        request.players.filterNot { it.ranking.playerId == declarer.id }.forEach { player ->
            assertEquals(ExhaustiveDrawHandDisclosure.CONCEALED, player.handDisclosure)
        }
    }

    /** Request 應保留規則實際提供的二至四人座位數，不補齊固定四列。 */
    @Test
    fun `request preserves actual player count`() {
        (2..4).forEach { playerCount ->
            val state = tableState(playerCount)
            val request = ExhaustiveDrawSettlementPresentationRequestFactory.create(
                previousState = state,
                currentState = state,
                module = module,
                aiPlayerIds = emptySet(),
                reason = RiichiExhaustiveDrawReason.SuufonRenda,
                tenpaiPlayerIds = null,
                revealedHands = emptyList(),
            )

            assertEquals(playerCount, request.players.size)
            request.players.forEach { player ->
                assertEquals(ExhaustiveDrawHandDisclosure.CONCEALED, player.handDisclosure)
            }
        }
    }

    /** 驗證流局手牌處理範圍只包含立牌，不包含副露中的實體牌。 */
    @Test
    fun `conceal targets standing tiles only and never melds`() {
        val meldTiles = List(3) { FakeIdentifiedTileFactory.create(Tile.Honor.White) }
        val standing = FakeIdentifiedTileFactory.create(Tile.Numeric(Tile.Suit.Dot, 5))
        val withMeld = FakeMahjongPlayerFactory.create(
            initialSeat = Wind.EAST,
            hand = Hand(
                tiles = listOf(standing),
                melds = listOf(Meld(MeldType.PON, meldTiles, meldTiles.first(), RelativeDirection.Left, publiclyRevealedTileIds = meldTiles.map { it.id }.toSet(), faceDownTileIds = emptySet())),
            ),
        )
        val state = FakeTableStateFactory.create(
            players = listOf(withMeld) + List(3) { FakeMahjongPlayerFactory.create() },
            config = config,
        )

        val request = ExhaustiveDrawSettlementPresentationRequestFactory.create(
            previousState = state,
            currentState = state,
            module = module,
            aiPlayerIds = emptySet(),
            reason = RiichiExhaustiveDrawReason.SuufonRenda,
            tenpaiPlayerIds = null,
            revealedHands = emptyList(),
        )

        assertEquals(PublicHandOrder.arrange(listOf(standing.id)), request.players.first().handTileIds)
        meldTiles.forEach { meldTile ->
            assertTrue(
                meldTile.id !in request.players.first().handTileIds,
                "A meld tile must never be scheduled for the exhaustive-draw conceal animation.",
            )
        }
    }

    /** 公開手牌只使用權威 revealedHandTileIds，不因規則結果而整手外洩。 */
    @Test
    fun `revealed hand ids are intersected with authoritative disclosure`() {
        val state = tableState(4)
        val player = state.players.first()
        val firstTileId = player.hand.standingTiles.first().id
        val request = ExhaustiveDrawSettlementPresentationRequestFactory.create(
            previousState = state,
            currentState = state.copy(revealedHandTileIds = setOf(firstTileId)),
            module = module,
            aiPlayerIds = emptySet(),
            reason = RiichiExhaustiveDrawReason.Normal,
            tenpaiPlayerIds = setOf(player.id),
            revealedHands = listOf(RevealedHandSettlement(player.id, emptySet())),
        )

        assertEquals(listOf(firstTileId), request.players.first().revealedHandTileIds)
        assertEquals(ExhaustiveDrawHandDisclosure.TENPAI, request.players.first().handDisclosure)
    }

    /** 流局時仍在摸牌位的牌另外列出，不併入公開排序的立牌。 */
    @Test
    fun `a drawn tile stays out of the sorted hand`() {
        val nineCharacters = FakeIdentifiedTileFactory.create(Tile.Numeric(Tile.Suit.Character, 9))
        val oneCharacters = FakeIdentifiedTileFactory.create(Tile.Numeric(Tile.Suit.Character, 1))
        val drawn = FakeIdentifiedTileFactory.create(Tile.Honor.North)
        val player = FakeMahjongPlayerFactory.create(
            initialSeat = Wind.EAST,
            hand = Hand(tiles = listOf(nineCharacters, oneCharacters), lastDrawn = drawn),
        )
        val state = FakeTableStateFactory.create(
            players = listOf(player) + List(3) { FakeMahjongPlayerFactory.create() },
            config = config,
        )

        val request = ExhaustiveDrawSettlementPresentationRequestFactory.create(
            previousState = state,
            currentState = state.copy(revealedHandTileIds = player.hand.allTiles.map { it.id }.toSet()),
            module = module,
            aiPlayerIds = emptySet(),
            reason = RiichiExhaustiveDrawReason.Normal,
            tenpaiPlayerIds = setOf(player.id),
            revealedHands = listOf(RevealedHandSettlement(player.id, emptySet())),
        )

        assertEquals(listOf(oneCharacters.id, nineCharacters.id), request.players.first().handTileIds)
        assertEquals(drawn.id, request.players.first().drawnTileId)
        assertTrue(drawn.id in request.players.first().revealedHandTileIds)
    }

    /** 驗證流局要求中的立牌清單依規則牌序排列。 */
    @Test
    fun `settlement hand tiles follow the rule tile order`() {
        val nineCharacters = FakeIdentifiedTileFactory.create(Tile.Numeric(Tile.Suit.Character, 9))
        val oneCharacters = FakeIdentifiedTileFactory.create(Tile.Numeric(Tile.Suit.Character, 1))
        val east = FakeIdentifiedTileFactory.create(Tile.Honor.East)
        val player = FakeMahjongPlayerFactory.create(
            initialSeat = Wind.EAST,
            hand = Hand(tiles = listOf(east, nineCharacters, oneCharacters)),
        )
        val state = FakeTableStateFactory.create(
            players = listOf(player) + List(3) { FakeMahjongPlayerFactory.create() },
            config = config,
        )

        val request = ExhaustiveDrawSettlementPresentationRequestFactory.create(
            previousState = state,
            currentState = state.copy(revealedHandTileIds = player.hand.allTiles.map { it.id }.toSet()),
            module = module,
            aiPlayerIds = emptySet(),
            reason = RiichiExhaustiveDrawReason.Normal,
            tenpaiPlayerIds = emptySet(),
            revealedHands = emptyList(),
        )

        assertEquals(
            listOf(oneCharacters.id, nineCharacters.id, east.id),
            request.players.first().handTileIds,
        )
    }

    /** 等待牌清單依規則牌序排列，不受 resolver 回傳 Set 的迭代順序影響。 */
    @Test
    fun `settlement waiting tiles follow the rule tile order`() {
        val state = tableState(4)
        val player = state.players.first()
        val request = ExhaustiveDrawSettlementPresentationRequestFactory.create(
            previousState = state,
            currentState = state.copy(revealedHandTileIds = player.hand.standingTiles.map { it.id }.toSet()),
            module = module,
            aiPlayerIds = emptySet(),
            reason = RiichiExhaustiveDrawReason.Normal,
            tenpaiPlayerIds = setOf(player.id),
            revealedHands = listOf(
                RevealedHandSettlement(
                    playerId = player.id,
                    waitingTiles = setOf(
                        Tile.Honor.East,
                        Tile.Numeric(Tile.Suit.Character, 9),
                        Tile.Numeric(Tile.Suit.Character, 1),
                    ),
                ),
            ),
        )

        assertEquals(
            listOf(
                Tile.Numeric(Tile.Suit.Character, 1),
                Tile.Numeric(Tile.Suit.Character, 9),
                Tile.Honor.East,
            ),
            request.players.first().waitingTiles,
        )
    }

    /** 驗證本局已和牌的玩家保留排名資料，但不再包含流局手牌處理要求或聽牌狀態。 */
    @Test
    fun `a finished player keeps its ranking row but gets no conceal animation or status`() {
        val base = tableState(4)
        val finished = base.players.last()
        val state = base.copy(finishedPlayerIds = setOf(finished.id))

        val request = ExhaustiveDrawSettlementPresentationRequestFactory.create(
            previousState = base,
            currentState = state,
            module = module,
            aiPlayerIds = emptySet(),
            reason = RiichiExhaustiveDrawReason.Normal,
            tenpaiPlayerIds = setOf(base.players.first().id),
            revealedHands = emptyList(),
        )

        val finishedRow = request.players.first { it.ranking.playerId == finished.id }
        assertEquals(emptyList(), finishedRow.handTileIds, "A finished player's tiles are already concealed.")
        assertEquals(null, finishedRow.statusId, "A finished player is neither tenpai nor noten.")
        assertEquals(4, request.players.size, "The score ranking still lists everyone.")
        assertEquals(
            BuiltInExhaustiveDrawSettlementStatusIds.NOTEN,
            request.players.first { it.ranking.playerId == base.players[1].id }.statusId,
            "Players still in the round are judged as usual.",
        )
    }

    /** 已完成玩家即使仍有公開牌資料，也不能被流局呈現標成聽牌或再次公開。 */
    @Test
    fun `a finished player is always concealed from draw presentation`() {
        val base = tableState(4)
        val finished = base.players.last()
        val state = base.copy(
            finishedPlayerIds = setOf(finished.id),
            revealedHandTileIds = finished.hand.standingTiles.map { it.id }.toSet(),
        )

        val request = ExhaustiveDrawSettlementPresentationRequestFactory.create(
            previousState = base,
            currentState = state,
            module = module,
            aiPlayerIds = emptySet(),
            reason = RiichiExhaustiveDrawReason.Normal,
            tenpaiPlayerIds = setOf(finished.id),
            revealedHands = listOf(
                RevealedHandSettlement(
                    playerId = finished.id,
                    waitingTiles = setOf(Tile.Honor.East),
                ),
            ),
        )

        val finishedPresentation = request.players.first { it.ranking.playerId == finished.id }
        assertEquals(ExhaustiveDrawHandDisclosure.CONCEALED, finishedPresentation.handDisclosure)
        assertEquals(emptyList(), finishedPresentation.revealedHandTileIds)
        assertEquals(emptyList(), finishedPresentation.waitingTiles)
        assertEquals(null, finishedPresentation.statusId)
    }

    /**
     * 建立指定玩家數、每人持有一張可追蹤測試牌的日麻桌況。
     *
     * @param playerCount 桌況中的玩家數。
     * @return 每位玩家各持一張可追蹤測試牌的桌況。
     */
    private fun tableState(playerCount: Int) = FakeTableStateFactory.create(
        players = Wind.entries.take(playerCount).mapIndexed { index, wind ->
            FakeMahjongPlayerFactory.create(
                initialSeat = wind,
                hand = FakeHandFactory.create(listOf(Tile.Numeric(Tile.Suit.Character, index + 1))),
            ).copy(score = 25_000 - index * 1_000)
        },
        config = config,
    )
}
