package com.doublemoon1119.mahjongcraft.flow.server.game.history.replay

import com.doublemoon1119.mahjongcraft.flow.common.game.history.HistoryActionResult
import com.doublemoon1119.mahjongcraft.flow.common.game.history.HistoryFact
import com.doublemoon1119.mahjongcraft.flow.common.game.history.HistoryFactTypeKeys
import com.doublemoon1119.mahjongcraft.flow.common.game.history.HistoryOutboxEvent
import com.doublemoon1119.mahjongcraft.flow.common.game.history.HistoryTableResult
import com.doublemoon1119.mahjongcraft.flow.common.game.history.HistoryWinDetails
import com.doublemoon1119.mahjongcraft.flow.common.game.history.replay.HistoryReplayFact
import com.doublemoon1119.mahjongcraft.flow.common.game.history.replay.HistoryRoundEvents
import com.doublemoon1119.mahjongcraft.flow.common.game.history.replay.HistoryRoundPosition
import com.doublemoon1119.mahjongcraft.flow.common.game.history.replay.HistoryRoundState
import com.doublemoon1119.mahjongcraft.flow.common.game.history.replay.HistoryWinDetailValue
import com.doublemoon1119.mahjongcraft.flow.common.game.model.GameFlowConfig
import com.doublemoon1119.mahjongcraft.flow.common.game.model.WinSettlementDetailField
import com.doublemoon1119.mahjongcraft.flow.common.game.model.WinSettlementDetailValue
import com.doublemoon1119.mahjongcraft.flow.persistence.format.history.HistoryRecordingPersistenceMapper
import com.doublemoon1119.mahjongcraft.flow.persistence.format.history.replay.CompactReplayCodec
import com.doublemoon1119.mahjongcraft.flow.persistence.format.history.replay.CompactReplayDictionary
import com.doublemoon1119.mahjongcraft.flow.persistence.format.history.replay.CompactReplayRoundReader
import com.doublemoon1119.mahjongcraft.flow.persistence.format.history.replay.HistoryReplayProjectionRegistry
import com.doublemoon1119.mahjongcraft.flow.persistence.format.history.replay.ReplayReadError
import com.doublemoon1119.mahjongcraft.flow.persistence.format.history.replay.ReplayReadLimits
import com.doublemoon1119.mahjongcraft.flow.persistence.format.history.replay.ReplayReadResult
import com.doublemoon1119.mahjongcraft.flow.persistence.format.history.replay.registerBuiltInHistoryReplayProjections
import com.doublemoon1119.mahjongcraft.flow.persistence.format.registry.buildBuiltInPersistenceRegistries
import com.doublemoon1119.mahjongcraft.logic.base.GameAction
import com.doublemoon1119.mahjongcraft.logic.base.Tile
import com.doublemoon1119.mahjongcraft.logic.rules.riichi.RiichiDiscardPile
import com.doublemoon1119.mahjongcraft.logic.rules.riichi.RiichiExhaustiveDrawReason
import com.doublemoon1119.mahjongcraft.logic.rules.riichi.RiichiRuleConfig
import com.doublemoon1119.mahjongcraft.logic.table.RoundCompletionClassification
import com.doublemoon1119.mahjongcraft.logic.table.RoundCompletionSummary
import com.doublemoon1119.mahjongcraft.logic.table.RoundTransitionDirective
import com.doublemoon1119.mahjongcraft.logic.table.TableState
import com.doublemoon1119.mahjongcraft.testing.logic.base.FakeIdentifiedTileFactory
import com.doublemoon1119.mahjongcraft.testing.logic.table.FakeMahjongPlayerFactory
import com.doublemoon1119.mahjongcraft.testing.logic.table.FakeTableStateFactory
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue
import kotlin.uuid.Uuid

/** 驗證精簡 Replay 讀取器以正式編碼器產生的文件重建局內資料。 */
class CompactReplayRoundReaderGenuineTest {
    /** 保存時間可倒退或相同，但不允許整數溢位或行為者數量失配。 */
    @Test
    fun `reader validates transaction actors and safe timestamp arithmetic`() = runTest {
        val fixture = fixture()
        val content = CompactReplayDictionary.decode(fixture.document.getValue("payload").jsonObject).jsonObject
        val rounds = content.getValue("rounds").jsonArray
        val round = rounds.first().jsonObject
        val transactions = round.getValue("transactions").jsonArray

        /** 替換一筆交易欄位，保留正式格式其餘內容。
         * @param index 交易索引。
         * @param fields 替換欄位。
         * @return 故意變更的文件。
         */
        fun changed(index: Int, fields: Map<String, JsonElement>): JsonObject {
            val values = transactions.toMutableList()
            values[index] = JsonObject(values[index].jsonObject + fields)
            val replaced = JsonObject(round + ("transactions" to JsonArray(values)))
            return JsonObject(fixture.document + ("payload" to CompactReplayDictionary.encode(JsonObject(content + ("rounds" to JsonArray(listOf(replaced)))))))
        }
        val negative = assertIs<ReplayReadResult.Success<HistoryRoundEvents>>(reader().readEvents(changed(1, mapOf("dt" to JsonPrimitive(-5))), fixture.matchId, 1, 1, 1)).value
        assertEquals(-5L, negative.transactions.single().occurredAtEpochMillis)
        val same = assertIs<ReplayReadResult.Success<HistoryRoundEvents>>(reader().readEvents(changed(1, mapOf("dt" to JsonPrimitive(0))), fixture.matchId, 1, 1, 1)).value
        assertEquals(0L, same.transactions.single().occurredAtEpochMillis)
        assertEquals(ReplayReadError.INVALID_DOCUMENT, assertIs<ReplayReadResult.Failure>(reader().readEvents(changed(1, mapOf("a" to JsonArray(listOf(JsonPrimitive(0))))), fixture.matchId, 1, 1, 1)).error)
        assertEquals(ReplayReadError.INVALID_DOCUMENT, assertIs<ReplayReadResult.Failure>(reader().readEvents(changed(1, mapOf("a" to JsonPrimitive(999))), fixture.matchId, 1, 1, 1)).error)
        assertEquals(ReplayReadError.INVALID_DOCUMENT, assertIs<ReplayReadResult.Failure>(reader().readEvents(changed(1, mapOf("dt" to JsonPrimitive(Long.MAX_VALUE))), fixture.matchId, 1, 0, 20)).error)
    }

    /** 建立內建投影已註冊並凍結的讀取器。
     * @param limits 本次使用的資源上限。
     * @return 測試讀取器。
     */
    private fun reader(limits: ReplayReadLimits = ReplayReadLimits()): CompactReplayRoundReader {
        val registry = HistoryReplayProjectionRegistry()
        registerBuiltInHistoryReplayProjections(registry)
        registry.freeze()
        return CompactReplayRoundReader(registry, limits)
    }

    /** 驗證正式編碼器保存對局識別資料與初始座位。 */
    @Test
    fun `reader preserves genuine replay identity and seat mapping`() = runTest {
        val fixture = fixture()
        val events = assertIs<ReplayReadResult.Success<HistoryRoundEvents>>(reader().readEvents(fixture.document, fixture.matchId, 1, 0, 20)).value
        assertEquals(fixture.matchId, events.identity.matchId)
        assertEquals(fixture.table.id, events.identity.tableId)
        val seats = fixture.table.players.sortedBy { it.initialSeatIndex }
        assertEquals(seats.map { it.id }, events.identity.players.map { it.playerId })
        assertEquals(seats.map { it.initialSeatIndex }, events.identity.players.map { it.initialSeatIndex })
    }

    /** 驗證交易分頁、相對時間與局內交易索引。 */
    @Test
    fun `reader paginates genuine encoded transactions`() = runTest {
        val fixture = fixture()
        val events = assertIs<ReplayReadResult.Success<HistoryRoundEvents>>(reader().readEvents(fixture.document, fixture.matchId, 1, 1, 1)).value
        assertEquals(listOf(1), events.transactions.map { it.index })
        assertEquals(125L, events.transactions.single().occurredAtEpochMillis)
        assertEquals(2, events.nextTransactionIndex)
    }

    /** 驗證真實桌況檢查點產生的差異能重建玩家分數與牌參照。 */
    @Test
    fun `reader applies genuine table checkpoint patch`() = runTest {
        val fixture = fixture()
        val initial = assertIs<ReplayReadResult.Success<HistoryRoundState>>(reader().readState(fixture.document, fixture.matchId, 1, HistoryRoundPosition.Initial)).value
        val after = assertIs<ReplayReadResult.Success<HistoryRoundState>>(reader().readState(fixture.document, fixture.matchId, 1, HistoryRoundPosition.AfterTransaction(1))).value
        assertEquals(fixture.table.players[0].score, initial.players[0].score)
        assertEquals(fixture.changed.players[0].score, after.players[0].score)
        assertEquals(listOf(1, 0), initial.players.map { it.initialSeatIndex })
        assertEquals(fixture.table.currentPlayer.initialSeatIndex, initial.currentPlayerSeat)
        assertEquals(initial.tileCatalog.tiles.size + 1, after.tileCatalog.tiles.size)
        assertEquals(fixture.changed.players[0].hand.lastDrawn?.tile, after.players[0].lastDrawn?.let { after.tileCatalog.tiles[it.tileIndex] })
        assertEquals(fixture.changed.players[0].hand.tiles.map { it.tile }, after.players[0].handTiles.map { after.tileCatalog.tiles[it.tileIndex] })
        val finalState = assertIs<ReplayReadResult.Success<HistoryRoundState>>(reader().readState(fixture.document, fixture.matchId, 1, HistoryRoundPosition.AfterTransaction(2))).value
        assertEquals(fixture.settled.players.associate { it.initialSeatIndex to it.score }, finalState.outcome?.scoresBySeat)
        assertEquals("test:round_completed", finalState.outcome?.reasonId)
        assertEquals(RoundCompletionClassification.EXHAUSTIVE_DRAW, finalState.outcome?.classification)
    }

    /** 驗證正式文件的語意完成事實可映射為歷史公開模型。 */
    @Test
    fun `reader maps genuine completion fact`() = runTest {
        val fixture = fixture()
        val events = assertIs<ReplayReadResult.Success<HistoryRoundEvents>>(reader().readEvents(fixture.document, fixture.matchId, 1, 0, 20)).value
        assertTrue(events.transactions.last().facts.any { it is HistoryReplayFact.Completion })
    }

    /** 結算差額以該筆交易前的分數為基準，且晚於分頁起點的結算仍可取得。 */
    @Test
    fun `reader derives settlement delta from transaction before state`() = runTest {
        val fixture = fixture()
        val events = assertIs<ReplayReadResult.Success<HistoryRoundEvents>>(
            reader().readEvents(fixture.document, fixture.matchId, 1, 2, 1),
        ).value
        val completion = events.transactions.single().facts.filterIsInstance<HistoryReplayFact.Completion>().single { it.typeKey == HistoryFactTypeKeys.ROUND_COMPLETED }
        assertEquals(
            fixture.settled.players.associate { settledPlayer ->
                settledPlayer.initialSeatIndex to settledPlayer.score - fixture.changed.players.single { player -> player.initialSeatIndex == settledPlayer.initialSeatIndex }.score
            },
            completion.outcome?.scoreChangesBySeat,
        )
    }

    /** 流局摘要沿用前一筆流局動作交易的分數快照，不因後續交易而變成零分差。 */
    @Test
    fun `reader preserves exhaustive draw settlement delta across transactions`() = runTest {
        val fixture = exhaustiveDrawFixture(listOf(26_000, 24_000))
        val page = assertIs<ReplayReadResult.Success<HistoryRoundEvents>>(
            reader().readEvents(fixture.document, fixture.matchId, 1, 2, 1),
        ).value
        val completion = page.transactions.single().facts.filterIsInstance<HistoryReplayFact.Completion>().single()
        assertEquals(mapOf(0 to 1_000, 1 to -1_000), completion.outcome?.scoreChangesBySeat)

        val state = assertIs<ReplayReadResult.Success<HistoryRoundState>>(
            reader().readState(fixture.document, fixture.matchId, 1, HistoryRoundPosition.AfterTransaction(2)),
        ).value
        assertEquals(mapOf(0 to 1_000, 1 to -1_000), state.outcome?.scoreChangesBySeat)
    }

    /** 流局前後分數相同時仍保留合法的零分差，不虛構非零結算。 */
    @Test
    fun `reader preserves legitimate zero exhaustive draw settlement delta`() = runTest {
        val fixture = exhaustiveDrawFixture(listOf(25_000, 25_000))
        val completion = assertIs<ReplayReadResult.Success<HistoryRoundEvents>>(
            reader().readEvents(fixture.document, fixture.matchId, 1, 2, 1),
        ).value.transactions.single().facts.filterIsInstance<HistoryReplayFact.Completion>().single()
        assertEquals(mapOf(0 to 0, 1 to 0), completion.outcome?.scoreChangesBySeat)
    }

    /** 流局結算後的其他分數變化不會污染先前保存的流局分差。 */
    @Test
    fun `reader ignores later score change when deriving exhaustive draw delta`() = runTest {
        val fixture = exhaustiveDrawFixture(listOf(26_000, 24_000), listOf(25_500, 24_500))
        val page = assertIs<ReplayReadResult.Success<HistoryRoundEvents>>(
            reader().readEvents(fixture.document, fixture.matchId, 1, 3, 1),
        ).value
        val completion = page.transactions.single().facts.filterIsInstance<HistoryReplayFact.Completion>().single()
        assertEquals(mapOf(0 to 1_000, 1 to -1_000), completion.outcome?.scoreChangesBySeat)

        val state = assertIs<ReplayReadResult.Success<HistoryRoundState>>(
            reader().readState(fixture.document, fixture.matchId, 1, HistoryRoundPosition.AfterTransaction(3)),
        ).value
        assertEquals(mapOf(0 to 1_000, 1 to -1_000), state.outcome?.scoreChangesBySeat)
    }

    /** 對局完成事實不應偽裝成單局結算差額。 */
    @Test
    fun `reader does not derive delta for match completion`() = runTest {
        val fixture = fixture()
        val events = assertIs<ReplayReadResult.Success<HistoryRoundEvents>>(
            reader().readEvents(fixture.document, fixture.matchId, 1, 2, 1),
        ).value
        val completion = events.transactions.single().facts.filterIsInstance<HistoryReplayFact.Completion>().single { it.typeKey == HistoryFactTypeKeys.MATCH_COMPLETED }
        assertEquals(emptyMap(), completion.outcome?.scoreChangesBySeat)
    }

    /** 驗證胡牌結算保存交易邊界分數，後續續局變化不會覆寫胡牌快照。 */
    @Test
    fun `reader preserves win settlement snapshot across continuation`() = runTest {
        val fixture = continuingWinFixture()
        val page = assertIs<ReplayReadResult.Success<HistoryRoundEvents>>(
            reader().readEvents(fixture.document, fixture.matchId, 1, 2, 1),
        ).value
        val win = page.transactions.single().facts.filterIsInstance<HistoryReplayFact.Completion>().single().outcome
        assertEquals(mapOf(0 to 26000, 1 to 24000), win?.scoresBySeat)
        assertEquals(mapOf(0 to 2000, 1 to 0), win?.scoreChangesBySeat)
        assertEquals(1, win?.winnerDetails?.size)
        assertEquals(2, win?.winnerDetails?.single()?.detailFields?.size)
        val tiles = win?.winnerDetails?.single()?.detailFields?.last()?.value as HistoryWinDetailValue.Tiles
        assertEquals(1, tiles.tiles.size)
        assertTrue(tiles.tiles.single().tileIndex in page.tileCatalog.tiles.indices)

        val state = assertIs<ReplayReadResult.Success<HistoryRoundState>>(
            reader().readState(fixture.document, fixture.matchId, 1, HistoryRoundPosition.AfterTransaction(2)),
        ).value
        assertEquals(mapOf(0 to 26000, 1 to 24000), state.outcome?.scoresBySeat)

        val summary = assertIs<ReplayReadResult.Success<HistoryRoundEvents>>(
            reader().readEvents(fixture.document, fixture.matchId, 1, 4, 1),
        ).value.transactions.single().facts.filterIsInstance<HistoryReplayFact.Completion>().single { it.typeKey == HistoryFactTypeKeys.ROUND_COMPLETED }.outcome
        assertEquals(emptyMap(), summary?.scoreChangesBySeat)
        assertEquals(true, summary?.hasEarlierWinSettlement)
    }

    /** 規則效果直接完成胡牌時，同樣抑制後續局完成事實的重複差額。 */
    @Test
    fun `reader marks earlier win rule effect before round completion`() = runTest {
        val fixture = continuingWinFixture(useWinRuleEffect = true)
        val summary = assertIs<ReplayReadResult.Success<HistoryRoundEvents>>(
            reader().readEvents(fixture.document, fixture.matchId, 1, 4, 1),
        ).value.transactions.single().facts.filterIsInstance<HistoryReplayFact.Completion>().single { it.typeKey == HistoryFactTypeKeys.ROUND_COMPLETED }.outcome
        assertEquals(true, summary?.hasEarlierWinSettlement)
        assertEquals(emptyMap(), summary?.scoreChangesBySeat)
    }

    /** 缺少和牌結算事實時，局完成摘要不會將流程推進的零分差冒充和牌分差。 */
    @Test
    fun `reader does not fabricate zero win delta from completion summary`() = runTest {
        val fixture = continuingWinFixture(recordWinSettlement = false)
        val summary = assertIs<ReplayReadResult.Success<HistoryRoundEvents>>(
            reader().readEvents(fixture.document, fixture.matchId, 1, 4, 1),
        ).value.transactions.single().facts.filterIsInstance<HistoryReplayFact.Completion>().single { it.typeKey == HistoryFactTypeKeys.ROUND_COMPLETED }.outcome
        assertEquals(emptyMap(), summary?.scoreChangesBySeat)
        assertEquals(false, summary?.hasEarlierWinSettlement)
        assertTrue(summary?.winnerDetails.orEmpty().isEmpty())
    }

    /** 驗證對局識別不符及容量超限皆分類失敗。 */
    @Test
    fun `reader rejects corrupt identity and limits`() = runTest {
        val fixture = fixture()
        assertEquals(ReplayReadError.INVALID_DOCUMENT, assertIs<ReplayReadResult.Failure>(reader().readEvents(fixture.document, Uuid.random(), 1, 0, 20)).error)
        assertEquals(ReplayReadError.LIMIT_EXCEEDED, assertIs<ReplayReadResult.Failure>(reader(ReplayReadLimits(maxTransactionsPerRound = 2)).readEvents(fixture.document, fixture.matchId, 1, 0, 20)).error)
    }

    /** 驗證取消發生於讀取工作中時直接傳播。 */
    @Test
    fun `reader propagates cancellation after work starts`() = runTest {
        val fixture = fixture()
        var returned = false
        val job: Job = launch(start = CoroutineStart.UNDISPATCHED) {
            reader().readEvents(fixture.document, fixture.matchId, 1, 0, 20)
            returned = true
        }
        assertTrue(job.isActive)
        job.cancel()
        job.join()
        assertTrue(job.isCancelled)
        assertEquals(false, returned)
    }

    /** 未知必要牌河使桌況不可取得，但不阻擋可獨立驗證的事件摘要。 */
    @Test
    fun `unknown required discard mapper does not fabricate empty state`() = runTest {
        val fixture = fixture()
        val doc = changeInitial(fixture.document) { initial ->
            val players = initial.getValue("players").jsonArray.toMutableList()
            val player = players.first().jsonObject
            val pile = player.getValue("discardPile").jsonObject
            players[0] = JsonObject(player + ("discardPile" to JsonObject(pile + ("typeKey" to JsonPrimitive("test:unknown")))))
            JsonObject(initial + ("players" to JsonArray(players)))
        }
        assertEquals(ReplayReadError.UNSUPPORTED_CONTENT, assertIs<ReplayReadResult.Failure>(reader().readState(doc, fixture.matchId, 1, HistoryRoundPosition.Initial)).error)
        assertIs<ReplayReadResult.Success<HistoryRoundEvents>>(reader().readEvents(doc, fixture.matchId, 1, 0, 1))
    }

    /** 未宣告牌與重複玩家座位均拒絕，不以陣列位置補足身分。 */
    @Test
    fun `state rejects undeclared tile and duplicate player seat`() = runTest {
        val fixture = fixture()
        for (field in listOf("initialSeatIndex", "hand")) {
            val doc = changeInitial(fixture.document) { initial ->
                val players = initial.getValue("players").jsonArray.toMutableList()
                val player = players.first().jsonObject
                players[0] = JsonObject(player + (field to if (field == "initialSeatIndex") JsonPrimitive(0) else JsonObject(player.getValue("hand").jsonObject + ("tiles" to JsonArray(listOf(JsonPrimitive(9999)))))))
                JsonObject(initial + ("players" to JsonArray(players)))
            }
            assertEquals(ReplayReadError.INVALID_DOCUMENT, assertIs<ReplayReadResult.Failure>(reader().readState(doc, fixture.matchId, 1, HistoryRoundPosition.Initial)).error)
        }
    }

    /** 局與交易位置不存在時回傳選取錯誤，末端空頁保留最終已宣告牌目錄。 */
    @Test
    fun `reader distinguishes missing selection and terminal empty page`() = runTest {
        val fixture = fixture()
        assertEquals(ReplayReadError.SELECTION_NOT_FOUND, assertIs<ReplayReadResult.Failure>(reader().readEvents(fixture.document, fixture.matchId, 2, 0, 1)).error)
        assertEquals(ReplayReadError.SELECTION_NOT_FOUND, assertIs<ReplayReadResult.Failure>(reader().readState(fixture.document, fixture.matchId, 1, HistoryRoundPosition.AfterTransaction(3))).error)
        val page = assertIs<ReplayReadResult.Success<HistoryRoundEvents>>(reader().readEvents(fixture.document, fixture.matchId, 1, 3, 1)).value
        assertTrue(page.transactions.isEmpty())
        assertEquals(null, page.nextTransactionIndex)
        assertEquals(fixture.changed.tileWall.remainingCount + 1, page.tileCatalog.tiles.size)
    }

    /** 建立由正式 [CompactReplayCodec] 產生的重播文件。 */
    private fun fixture(): Fixture {
        val base = FakeTableStateFactory.create(players = listOf(FakeMahjongPlayerFactory.create(discardPile = RiichiDiscardPile()), FakeMahjongPlayerFactory.create(discardPile = RiichiDiscardPile())), currentPlayerIndex = 1, config = RiichiRuleConfig())
        val table = base.copy(players = base.players.reversed())
        val newTile = FakeIdentifiedTileFactory.create(Tile.Numeric(Tile.Suit.Dot, 5))
        val changed = table.copy(players = table.players.mapIndexed { index, player -> if (index == 0) player.copy(score = player.score + 100, hand = player.hand.copy(lastDrawn = newTile)) else player })
        val settled = changed.copy(players = changed.players.map { player -> player.copy(score = player.score + if (player.initialSeatIndex == 0) 250 else -250) })
        val matchId = Uuid.random()
        val events = listOf(
            event(matchId, table.id, 1, 0L, HistoryFact.MatchStarted(table, GameFlowConfig())),
            event(matchId, table.id, 2, 125L, HistoryFact.TableChanged(HistoryTableResult.Checkpoint("test:checkpoint", changed))),
            event(matchId, table.id, 3, 250L, HistoryFact.RoundCompleted(RoundCompletionSummary("test:round_completed", RoundCompletionClassification.EXHAUSTIVE_DRAW, emptySet(), transitionDirective = RoundTransitionDirective.ADVANCE_DEALER, settledScoresByPlayerId = settled.players.associate { it.id to it.score }))),
            event(matchId, table.id, 4, 250L, HistoryFact.MatchCompleted("test:completed", settled.players.associate { it.id to it.score })).copy(transactionFirstSequence = 3),
            event(matchId, table.id, 5, 250L, HistoryFact.TableChanged(HistoryTableResult.Checkpoint("test:settled", settled))).copy(transactionFirstSequence = 3),
        )
        val registries = buildBuiltInPersistenceRegistries()
        return Fixture(CompactReplayCodec.encodeCompact(events, HistoryRecordingPersistenceMapper(registries), registries), matchId, table, changed, settled)
    }

    /** 建立流局動作與局完成摘要分屬不同交易的正式 Replay 文件。
     * @param scores 流局結算交易寫入的玩家分數，依初始座位排列。
     * @param intermediateScores 流局結算後、局完成前的額外分數；沒有時為 null。
     * @return 正式編碼的流局測試 Replay fixture。
     */
    private fun exhaustiveDrawFixture(scores: List<Int>, intermediateScores: List<Int>? = null): Fixture {
        require(scores.size == 2)
        require(intermediateScores == null || intermediateScores.size == 2)
        val base = FakeTableStateFactory.create(
            players = listOf(
                FakeMahjongPlayerFactory.create(discardPile = RiichiDiscardPile()),
                FakeMahjongPlayerFactory.create(discardPile = RiichiDiscardPile()),
            ),
            currentPlayerIndex = 1,
            config = RiichiRuleConfig(),
        )
        val table = base.copy(players = base.players.reversed()).reversedScores(25_000)
        val settled = table.copy(
            players = table.players.map { player ->
                player.copy(score = scores[player.initialSeatIndex])
            },
        )
        val intermediate = intermediateScores?.let { values ->
            settled.copy(
                players = settled.players.map { player ->
                    player.copy(score = values[player.initialSeatIndex])
                },
            )
        }
        val matchId = Uuid.random()
        val settledScores = settled.players.associate { it.id to it.score }
        val actionResult = HistoryActionResult(
            affectedTileIds = emptyList(),
            newlyRevealedTileIds = emptyList(),
            remainingWallTileCount = settled.tileWall.remainingCount,
            reservedWallTileIds = settled.reservedWallTiles.map { it.id },
            scoresByPlayerId = settledScores,
            nextPlayerId = settled.currentPlayer.id,
        )
        val events = buildList {
            add(event(matchId, table.id, 1, 0L, HistoryFact.MatchStarted(table, GameFlowConfig())))
            add(event(matchId, table.id, 2, 100L, HistoryFact.ActionAccepted(GameAction.ExhaustiveDraw(RiichiExhaustiveDrawReason.Normal), actionResult)))
            add(event(matchId, table.id, 3, 100L, HistoryFact.TableChanged(HistoryTableResult.Checkpoint("test:exhaustive_draw_settled", settled))).copy(transactionFirstSequence = 2))
            if (intermediate != null) {
                add(event(matchId, table.id, 4, 150L, HistoryFact.TableChanged(HistoryTableResult.Checkpoint("test:intermediate_change", intermediate))))
            }
            add(
                event(
                    matchId,
                    table.id,
                    if (intermediate == null) 4 else 5,
                    200L,
                    HistoryFact.RoundCompleted(
                        RoundCompletionSummary(
                            "test:exhaustive_draw",
                            RoundCompletionClassification.EXHAUSTIVE_DRAW,
                            emptySet(),
                            transitionDirective = RoundTransitionDirective.ADVANCE_DEALER,
                            settledScoresByPlayerId = settledScores,
                        ),
                    ),
                ),
            )
            add(event(matchId, table.id, if (intermediate == null) 5 else 6, 300L, HistoryFact.MatchCompleted("test:completed", settledScores)))
        }
        val registries = buildBuiltInPersistenceRegistries()
        return Fixture(CompactReplayCodec.encodeCompact(events, HistoryRecordingPersistenceMapper(registries), registries), matchId, table, settled, settled)
    }

    /** 建立包含立直後胡牌、續局分數變化與後續局結算的正式 Replay 文件。
     * @param useWinRuleEffect 是否以 WIN 規則效果取代獨立胡牌結算事實。
     * @param recordWinSettlement 是否記錄實際胡牌交易；關閉時驗證缺少明細的安全處理。
     * @return 正式編碼的測試 Replay fixture。
     */
    private fun continuingWinFixture(useWinRuleEffect: Boolean = false, recordWinSettlement: Boolean = true): Fixture {
        val base = FakeTableStateFactory.create(players = listOf(FakeMahjongPlayerFactory.create(discardPile = RiichiDiscardPile()), FakeMahjongPlayerFactory.create(discardPile = RiichiDiscardPile())), currentPlayerIndex = 1, config = RiichiRuleConfig())
        val table = base.reversedScores(25000)
        val winner = table.players.first()
        val beforeWin = table.copy(players = table.players.map { player -> player.copy(score = 24000) })
        val changed = beforeWin.copy(players = beforeWin.players.map { player -> if (player.initialSeatIndex == winner.initialSeatIndex) player.copy(score = 26000) else player })
        val continued = changed.copy(players = changed.players.map { player -> if (player.initialSeatIndex == winner.initialSeatIndex) player.copy(score = 23000) else player })
        val details = HistoryWinDetails(
            winner.id,
            "mahjongcraft:riichi",
            listOf(
                WinSettlementDetailField("mahjongcraft:yaku", WinSettlementDetailValue.Text("mahjongcraft.yaku.test")),
                WinSettlementDetailField("mahjongcraft:dora", WinSettlementDetailValue.Tiles(listOf(table.tileWall.getAllTiles().first().id))),
            ),
        )
        val matchId = Uuid.random()
        val events = listOf(
            event(matchId, table.id, 1, 0L, HistoryFact.MatchStarted(table, GameFlowConfig())),
            event(matchId, table.id, 2, 100L, HistoryFact.TableChanged(HistoryTableResult.Checkpoint("test:riichi", beforeWin))),
            event(matchId, table.id, 3, 200L, HistoryFact.TableChanged(HistoryTableResult.Checkpoint("test:win", changed))),
            event(
                matchId,
                table.id,
                4,
                200L,
                if (!recordWinSettlement) {
                    HistoryFact.ReactionResolved(null, null)
                } else if (useWinRuleEffect) {
                    HistoryFact.RuleEffectResolved(
                        "test:win_effect",
                        RoundCompletionSummary(
                            "test:ron",
                            RoundCompletionClassification.WIN,
                            setOf(winner.id),
                            transitionDirective = RoundTransitionDirective.ADVANCE_DEALER,
                            settledScoresByPlayerId = changed.players.associate { it.id to it.score },
                        ),
                        listOf(details),
                    )
                } else {
                    HistoryFact.WinSettled("test:ron", listOf(details))
                },
            ).copy(transactionFirstSequence = 3),
            event(matchId, table.id, 5, 300L, HistoryFact.TableChanged(HistoryTableResult.Checkpoint("test:continuation", continued))),
            event(matchId, table.id, 6, 400L, HistoryFact.RoundCompleted(RoundCompletionSummary("test:win", RoundCompletionClassification.WIN, setOf(winner.id), transitionDirective = RoundTransitionDirective.ADVANCE_DEALER, settledScoresByPlayerId = continued.players.associate { it.id to it.score }))),
            event(matchId, table.id, 7, 400L, HistoryFact.MatchCompleted("test:completed", continued.players.associate { it.id to it.score })).copy(transactionFirstSequence = 6),
        )
        val registries = buildBuiltInPersistenceRegistries()
        return Fixture(CompactReplayCodec.encodeCompact(events, HistoryRecordingPersistenceMapper(registries), registries), matchId, table, changed, continued)
    }

    /** 將測試桌況的玩家分數統一設為指定值。
     * @param score 每位玩家的新分數。
     * @return 分數更新後的桌況。
     */
    private fun TableState.reversedScores(score: Int): TableState = copy(players = players.map { it.copy(score = score) })

    /** 建立正式編碼器需要的有序歷史事件。
     * @param matchId 對局識別碼。
     * @param tableId 牌桌識別碼。
     * @param sequence 事件與交易序號。
     * @param time 保存時間。
     * @param fact 語意事實。
     * @return 同局有序事件。
     */
    private fun event(matchId: Uuid, tableId: Uuid, sequence: Long, time: Long, fact: HistoryFact) = HistoryOutboxEvent(matchId, tableId, 1, sequence, sequence, time, null, fact)

    /** 在正式文件中故意破壞初始投影，保留其他字典與交易資料。
     * @param document 正式文件。
     * @param transform 測試用破壞操作。
     * @return 重新字典化的破損文件。
     */
    private fun changeInitial(document: JsonObject, transform: (JsonObject) -> JsonObject): JsonObject {
        val content = CompactReplayDictionary.decode(document.getValue("payload").jsonObject).jsonObject
        val rounds = content.getValue("rounds").jsonArray.toMutableList()
        val round = rounds.first().jsonObject
        rounds[0] = JsonObject(round + ("initial" to transform(round.getValue("initial").jsonObject)))
        return JsonObject(document + ("payload" to CompactReplayDictionary.encode(JsonObject(content + ("rounds" to JsonArray(rounds))))))
    }

    /** 正式編碼器文件及其預期桌況。
     * @property document 正式編碼文件。
     * @property matchId 對局識別碼。
     * @property table 開局桌況。
     * @property changed 第二筆交易完成後桌況。
     * @property settled 保存於結算摘要的分數。
     */
    private data class Fixture(
        val document: JsonObject,
        val matchId: Uuid,
        val table: TableState,
        val changed: TableState,
        val settled: TableState,
    )
}
