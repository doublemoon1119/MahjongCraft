package com.doublemoon1119.mahjongcraft.platform.fabric.server.history

import com.doublemoon1119.mahjongcraft.flow.common.game.history.HistoryEventDraft
import com.doublemoon1119.mahjongcraft.flow.common.game.history.HistoryFact
import com.doublemoon1119.mahjongcraft.flow.common.game.history.HistoryOutboxEvent
import com.doublemoon1119.mahjongcraft.flow.common.game.history.HistoryRecordingDecision
import com.doublemoon1119.mahjongcraft.flow.common.game.history.HistoryRecordingState
import com.doublemoon1119.mahjongcraft.flow.common.game.history.HistoryRecordingTerminal
import com.doublemoon1119.mahjongcraft.flow.common.game.model.Game
import com.doublemoon1119.mahjongcraft.flow.common.game.model.GameFlowConfig
import com.doublemoon1119.mahjongcraft.flow.persistence.format.history.HistoryOutboxEventPersistenceDto
import com.doublemoon1119.mahjongcraft.flow.persistence.format.history.HistoryRecordingPersistenceMapper
import com.doublemoon1119.mahjongcraft.flow.persistence.format.history.replay.CompactReplayCodec
import com.doublemoon1119.mahjongcraft.flow.server.game.repository.GameRepositoryImpl
import com.doublemoon1119.mahjongcraft.flow.server.state.AuthoritativeStateSnapshot
import com.doublemoon1119.mahjongcraft.flow.server.state.AuthoritativeStateStore
import com.doublemoon1119.mahjongcraft.flow.server.state.AuthoritativeStateUpdate
import com.doublemoon1119.mahjongcraft.logic.module.MahjongModuleRegistryImpl
import com.doublemoon1119.mahjongcraft.logic.rules.riichi.RiichiDiscardPile
import com.doublemoon1119.mahjongcraft.logic.rules.riichi.RiichiDynamicState
import com.doublemoon1119.mahjongcraft.logic.rules.riichi.RiichiRuleConfig
import com.doublemoon1119.mahjongcraft.logic.table.Wind
import com.doublemoon1119.mahjongcraft.platform.minecraft.table.TableLocationRegistry
import com.doublemoon1119.mahjongcraft.testing.flow.bundled.bundledPersistenceRegistries
import com.doublemoon1119.mahjongcraft.testing.flow.bundled.registerBundledRuleModules
import com.doublemoon1119.mahjongcraft.testing.logic.table.FakeMahjongPlayerFactory
import com.doublemoon1119.mahjongcraft.testing.logic.table.FakeTableStateFactory
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.sql.DriverManager
import kotlin.io.path.createTempDirectory
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertSame
import kotlin.test.assertTrue
import kotlin.uuid.Uuid

/** 驗證完整返回房間後才刪除原始事件並保存可重啟的 Replay。 */
class HistoryArchiveServiceTest {
    /** 相同規則局號的重複莊局必須以實際出現順序建立唯一 SQL round 索引。 */
    @Test
    fun `archive assigns distinct round indexes to repeated round numbers`() {
        val tableId = Uuid.random()
        val matchId = Uuid.random()
        val table = FakeTableStateFactory.create(
            id = tableId,
            players = listOf(
                FakeMahjongPlayerFactory.create(Wind.EAST, discardPile = RiichiDiscardPile()),
                FakeMahjongPlayerFactory.create(Wind.SOUTH, discardPile = RiichiDiscardPile()),
                FakeMahjongPlayerFactory.create(Wind.WEST, discardPile = RiichiDiscardPile()),
                FakeMahjongPlayerFactory.create(Wind.NORTH, discardPile = RiichiDiscardPile()),
            ),
            config = RiichiRuleConfig(),
            roundNumber = 1,
        )
        val events = listOf(
            HistoryOutboxEvent(
                matchId = matchId,
                venueId = tableId,
                roundNumber = 1,
                sequence = 1,
                transactionFirstSequence = 1,
                occurredAtEpochMillis = 100,
                actorPlayerId = null,
                fact = HistoryFact.MatchStarted(table, GameFlowConfig(), emptyMap()),
            ),
            HistoryOutboxEvent(
                matchId = matchId,
                venueId = tableId,
                roundNumber = 1,
                sequence = 2,
                transactionFirstSequence = 2,
                occurredAtEpochMillis = 200,
                actorPlayerId = null,
                fact = HistoryFact.RoundStarted(table),
            ),
            HistoryOutboxEvent(
                matchId = matchId,
                venueId = tableId,
                roundNumber = 1,
                sequence = 3,
                transactionFirstSequence = 3,
                occurredAtEpochMillis = 300,
                actorPlayerId = null,
                fact = HistoryFact.RoundStarted(table),
            ),
            HistoryOutboxEvent(
                matchId = matchId,
                venueId = tableId,
                roundNumber = 1,
                sequence = 4,
                transactionFirstSequence = 4,
                occurredAtEpochMillis = 400,
                actorPlayerId = null,
                fact = HistoryFact.MatchCompleted("test:complete", emptyMap()),
            ),
            HistoryOutboxEvent(
                matchId = matchId,
                venueId = tableId,
                roundNumber = 1,
                sequence = 5,
                transactionFirstSequence = 5,
                occurredAtEpochMillis = 500,
                actorPlayerId = null,
                fact = HistoryFact.ReturnedToRoom,
            ),
        )
        val registries = bundledPersistenceRegistries()
        val mapper = HistoryRecordingPersistenceMapper(registries, Json)
        val modules = MahjongModuleRegistryImpl().apply { registerBundledRuleModules() }
        val service = HistoryArchiveService(mapper, registries, modules, TableLocationRegistry(), Json)
        val path = createTempDirectory("mahjongcraft-history-repeated-round-").resolve("history.sqlite")
        val database = SqliteHistoryDatabase.open(path)
        database.appendPendingBatch(
            events.map { event ->
                PendingHistoryRecord(
                    event.matchId.toString(),
                    event.sequence,
                    event.roundNumber,
                    event.occurredAtEpochMillis,
                    1,
                    Json.encodeToString(HistoryOutboxEventPersistenceDto.serializer(), mapper.encodePendingEvent(event)),
                )
            },
        )

        assertEquals(1, service.archiveReady(database, AuthoritativeStateSnapshot(), scanAllPending = true))
        DriverManager.getConnection("jdbc:sqlite:${path.toAbsolutePath()}").use { connection ->
            connection.prepareStatement("SELECT round_number FROM history_round WHERE match_id = ? ORDER BY round_number").use { statement ->
                statement.setString(1, matchId.toString())
                statement.executeQuery().use { rows ->
                    val roundNumbers = buildList { while (rows.next()) add(rows.getInt(1)) }
                    assertEquals(listOf(1, 2, 3), roundNumbers)
                }
            }
            connection.prepareStatement("SELECT payload FROM history_replay WHERE match_id = ?").use { statement ->
                statement.setString(1, matchId.toString())
                statement.executeQuery().use { rows ->
                    assertTrue(rows.next())
                    val decoded = CompactReplayCodec.decodeCompact(Json.parseToJsonElement(rows.getString(1)).jsonObject)
                    assertEquals(3, decoded.size)
                    assertTrue(decoded.all { it.isNotEmpty() })
                }
            }
        }
    }

    /** 終局事件仍留在 Game 時不能提前封存；返回房間後可以原子封存。 */
    @Test
    fun `archive waits for return to room and removes pending only after replay is saved`() {
        val tableId = Uuid.random()
        val matchId = Uuid.random()
        val players = listOf(
            FakeMahjongPlayerFactory.create(Wind.EAST, discardPile = RiichiDiscardPile()),
            FakeMahjongPlayerFactory.create(Wind.SOUTH, discardPile = RiichiDiscardPile()),
            FakeMahjongPlayerFactory.create(Wind.WEST, discardPile = RiichiDiscardPile()),
            FakeMahjongPlayerFactory.create(Wind.NORTH, discardPile = RiichiDiscardPile()),
        )
        val table = FakeTableStateFactory.create(id = tableId, players = players, config = RiichiRuleConfig())
        val events = listOf(
            HistoryOutboxEvent(matchId, tableId, 1, 1, occurredAtEpochMillis = 100, actorPlayerId = null, fact = HistoryFact.MatchStarted(table, GameFlowConfig(), emptyMap())),
            HistoryOutboxEvent(matchId, tableId, 1, 2, occurredAtEpochMillis = 200, actorPlayerId = null, fact = HistoryFact.MatchCompleted("test:complete", emptyMap())),
            HistoryOutboxEvent(matchId, tableId, 1, 3, occurredAtEpochMillis = 300, actorPlayerId = null, fact = HistoryFact.ReturnedToRoom),
        )
        val registries = bundledPersistenceRegistries()
        val mapper = HistoryRecordingPersistenceMapper(registries, Json)
        val modules = MahjongModuleRegistryImpl().apply { registerBundledRuleModules() }
        val service = HistoryArchiveService(mapper, registries, modules, TableLocationRegistry(), Json)
        val path = createTempDirectory("mahjongcraft-history-archive-").resolve("history.sqlite")
        val database = SqliteHistoryDatabase.open(path)
        database.appendPendingBatch(
            events.take(2).map { event ->
                PendingHistoryRecord(
                    event.matchId.toString(),
                    event.sequence,
                    event.roundNumber,
                    event.occurredAtEpochMillis,
                    1,
                    Json.encodeToString(HistoryOutboxEventPersistenceDto.serializer(), mapper.encodePendingEvent(event)),
                )
            },
        )

        assertEquals(0, service.archiveReady(database, AuthoritativeStateSnapshot(), scanAllPending = true))
        assertEquals(2, database.readPending(matchId.toString()).size)
        val returned = events.last()
        database.appendPending(
            PendingHistoryRecord(
                returned.matchId.toString(),
                returned.sequence,
                returned.roundNumber,
                returned.occurredAtEpochMillis,
                1,
                Json.encodeToString(HistoryOutboxEventPersistenceDto.serializer(), mapper.encodePendingEvent(returned)),
            ),
        )
        val active = Game(tableState = table, flowConfig = GameFlowConfig(), matchId = matchId)
        assertEquals(0, service.archiveReady(database, AuthoritativeStateSnapshot(games = mapOf(tableId to active)), scanAllPending = true))
        assertEquals(3, database.readPending(matchId.toString()).size)
        assertEquals(1, service.archiveReady(database, AuthoritativeStateSnapshot(), scanAllPending = true))
        val reopened = SqliteHistoryDatabase.open(path)
        assertEquals(emptyList(), reopened.readPending(matchId.toString()))
        assertTrue(matchId.toString() in reopened.readReplayIds())
    }

    /** 權威終局交易自動附加桌況變更時，封存仍須保留終局分數並移除原始事件。 */
    @Test
    fun `archive accepts store generated table change after match completion`() = runBlocking {
        val table = FakeTableStateFactory.create(
            players = listOf(
                FakeMahjongPlayerFactory.create(Wind.EAST, discardPile = RiichiDiscardPile()),
                FakeMahjongPlayerFactory.create(Wind.SOUTH, discardPile = RiichiDiscardPile()),
                FakeMahjongPlayerFactory.create(Wind.WEST, discardPile = RiichiDiscardPile()),
                FakeMahjongPlayerFactory.create(Wind.NORTH, discardPile = RiichiDiscardPile()),
            ),
            config = RiichiRuleConfig(),
            dynamicRuleState = RiichiDynamicState(riichiStickCount = 1),
        )
        val matchId = Uuid.random()
        val opening = HistoryOutboxEvent(
            matchId,
            table.id,
            table.roundNumber,
            sequence = 1,
            occurredAtEpochMillis = 100,
            actorPlayerId = null,
            fact = HistoryFact.MatchStarted(table, GameFlowConfig(), emptyMap()),
        )
        val game = Game(table, GameFlowConfig(), matchId = matchId)
        val store = AuthoritativeStateStore(historyRecordingEnabled = true)
        store.load(
            AuthoritativeStateSnapshot(
                games = mapOf(table.id to game),
                historyRecordingState = HistoryRecordingState(
                    nextSequenceByMatchId = mapOf(matchId to 2),
                    pendingEvents = listOf(opening),
                    decisionsByMatchId = mapOf(matchId to HistoryRecordingDecision.RECORDING),
                ),
            ),
        )
        val finalTable = table.copy(
            players = table.players.mapIndexed { index, player ->
                if (index == 0) player.copy(score = player.score + 1_000) else player
            },
            dynamicRuleState = RiichiDynamicState(riichiStickCount = 0),
        )
        GameRepositoryImpl(store).updateGame(
            table.id,
            history = { _, after, _ ->
                listOf(
                    HistoryEventDraft(
                        null,
                        HistoryFact.MatchCompleted(
                            "mahjongcraft:test_complete",
                            after!!.tableState.players.associate { it.id to it.score },
                        ),
                    ),
                )
            },
        ) { current ->
            current!!.copy(tableState = finalTable, isMatchOver = true, matchEndReasonId = "mahjongcraft:test_complete") to Unit
        }
        store.update { state ->
            AuthoritativeStateUpdate(
                state.copy(games = emptyMap()),
                Unit,
                historyDraftsByVenueId = mapOf(
                    table.id to listOf(
                        HistoryEventDraft(
                            null,
                            HistoryFact.ReturnedToRoom,
                        ),
                    ),
                ),
            )
        }
        val events = store.snapshot().historyRecordingState.pendingEvents
        assertEquals(HistoryFact.MatchCompleted::class, events[1].fact::class)
        assertEquals(HistoryFact.TableChanged::class, events[2].fact::class)
        assertEquals(events[1].transactionFirstSequence, events[2].transactionFirstSequence)
        assertEquals(HistoryFact.ReturnedToRoom, events[3].fact)

        val registries = bundledPersistenceRegistries()
        val mapper = HistoryRecordingPersistenceMapper(registries, Json)
        val modules = MahjongModuleRegistryImpl().apply { registerBundledRuleModules() }
        val service = HistoryArchiveService(mapper, registries, modules, TableLocationRegistry(), Json)
        val path = createTempDirectory("mahjongcraft-history-generated-table-change-").resolve("history.sqlite")
        val database = SqliteHistoryDatabase.open(path)
        database.appendPendingBatch(
            events.map { event ->
                PendingHistoryRecord(
                    event.matchId.toString(),
                    event.sequence,
                    event.roundNumber,
                    event.occurredAtEpochMillis,
                    1,
                    Json.encodeToString(HistoryOutboxEventPersistenceDto.serializer(), mapper.encodePendingEvent(event)),
                )
            },
        )

        assertEquals(1, service.archiveReady(database, AuthoritativeStateSnapshot(), scanAllPending = true))
        assertEquals(emptyList(), database.readPending(matchId.toString()))
        assertTrue(matchId.toString() in database.readReplayIds())
        DriverManager.getConnection("jdbc:sqlite:${path.toAbsolutePath()}").use { connection ->
            connection.prepareStatement("SELECT payload FROM history_replay WHERE match_id = ?").use { statement ->
                statement.setString(1, matchId.toString())
                statement.executeQuery().use { rows ->
                    assertTrue(rows.next())
                    val replay = CompactReplayCodec.decodeCompact(Json.parseToJsonElement(rows.getString(1)).jsonObject)
                    val players = replay.last().last().projection.jsonObject.getValue("players").jsonArray
                    assertEquals(finalTable.players.first().score, players.first().jsonObject.getValue("score").jsonPrimitive.int)
                }
            }
            connection.prepareStatement("SELECT final_score FROM history_participant_result WHERE match_id = ? AND seat_index = 0").use { statement ->
                statement.setString(1, matchId.toString())
                statement.executeQuery().use { rows ->
                    assertTrue(rows.next())
                    assertEquals(finalTable.players.first().score, rows.getInt(1))
                }
            }
        }
    }

    /** 編碼與資料庫內容相同時視為一致，不同時視為衝突。 */
    @Test
    fun `staged event comparison separates matches from conflicts`() {
        assertEquals(StagedHistoryEventComparison.Matches, compareStagedHistoryEvent("payload") { "payload" })
        assertEquals(StagedHistoryEventComparison.Conflicts, compareStagedHistoryEvent("payload") { "other" })
    }

    /** 編碼拋出例外時回報編碼失敗並保留原始例外，不當成內容衝突。 */
    @Test
    fun `staged event comparison keeps the encoding failure`() {
        val failure = IllegalStateException("unregistered extension")

        val comparison = compareStagedHistoryEvent("payload") { throw failure }

        assertSame(failure, assertIs<StagedHistoryEventComparison.EncodingFailed>(comparison).error)
    }

    /** 彙總值相同的兩種序號，推出不同的最早缺口；彙總證明連續時只檢查尾端。 */
    @Test
    fun `earliest gap follows the actual sequences`() {
        val summary = PendingSequenceSummary(count = 4, first = 1, last = 7)

        assertEquals(3L, firstMissingSequence(summary, listOf(1L, 2L, 4L, 7L), emptyList(), nextSequence = 8))
        assertEquals(2L, firstMissingSequence(summary, listOf(1L, 3L, 4L, 7L), emptyList(), nextSequence = 8))
        val contiguous = PendingSequenceSummary(count = 3, first = 1, last = 3)
        assertEquals(null, firstMissingSequence(contiguous, null, listOf(4L, 5L), nextSequence = 6))
        assertEquals(4L, firstMissingSequence(contiguous, null, listOf(5L), nextSequence = 6))
        assertEquals(4L, firstMissingSequence(contiguous, null, emptyList(), nextSequence = 5), "The authoritative save already assigned sequence 4.")
    }

    /** 待寫事件剛好補足資料庫的缺口時不算缺口。 */
    @Test
    fun `staged events can fill a database gap`() {
        val summary = PendingSequenceSummary(count = 3, first = 1, last = 4)

        assertEquals(null, firstMissingSequence(summary, listOf(1L, 2L, 4L), listOf(3L), nextSequence = 5))
    }

    /** 同一次連線中已驗證的事件不再解碼；重新開啟資料庫後完整解碼，因此之後才損壞的內容在下一次連線被發現。 */
    @Test
    fun `validated events are decoded again only on a new connection`() {
        val fixture = ArchiveFixture("mahjongcraft-history-incremental-")
        val events = fixture.matchEvents()
        fixture.database.appendPendingBatch(events.map(fixture::record))
        val recording = HistoryRecordingState(nextSequenceByMatchId = mapOf(fixture.matchId to events.size + 1L))

        fixture.service.reconcile(fixture.database, recording)
        assertTrue(fixture.service.blockedMatchIds.isEmpty())
        fixture.corruptPayload(sequence = 2)

        fixture.service.reconcile(fixture.database, recording)
        assertTrue(fixture.service.blockedMatchIds.isEmpty(), "Already validated events are not decoded again in the same connection.")

        fixture.service.beginConnection()
        fixture.service.reconcile(fixture.database, recording)
        assertEquals(setOf(fixture.matchId.toString()), fixture.service.blockedMatchIds)
    }

    /** 待寫佇列與資料庫重疊的序號內容不同時，即使該場已驗證過仍判定衝突並禁止寫入。 */
    @Test
    fun `overlapping staged events are still compared`() {
        val fixture = ArchiveFixture("mahjongcraft-history-overlap-")
        val events = fixture.matchEvents()
        fixture.database.appendPendingBatch(events.map(fixture::record))
        val recorded = HistoryRecordingState(nextSequenceByMatchId = mapOf(fixture.matchId to events.size + 1L))
        fixture.service.reconcile(fixture.database, recorded)

        val conflicting = events.last().copy(occurredAtEpochMillis = 999)
        fixture.service.reconcile(fixture.database, recorded.copy(pendingEvents = listOf(conflicting)))

        assertEquals(setOf(fixture.matchId.toString()), fixture.service.blockedMatchIds)
    }

    /** 連線期間只封存已有終局紀錄的場次；開啟資料庫時的完整掃描則找出所有可封存的場次。 */
    @Test
    fun `incremental archiving reads only ended matches`() {
        val fixture = ArchiveFixture("mahjongcraft-history-ended-")
        val events = fixture.matchEvents()
        fixture.database.appendPendingBatch(events.map(fixture::record))

        assertEquals(0, fixture.service.archiveReady(fixture.database, AuthoritativeStateSnapshot(), scanAllPending = false))
        val ended = AuthoritativeStateSnapshot(
            historyRecordingState = HistoryRecordingState(
                terminalByMatchId = mapOf(fixture.matchId to HistoryRecordingTerminal(endedAtEpochMillis = 600, completed = true, venueId = fixture.tableId)),
            ),
        )
        assertEquals(1, fixture.service.archiveReady(fixture.database, ended, scanAllPending = false))
    }

    /** 開啟資料庫時的完整掃描不需要終局紀錄。 */
    @Test
    fun `a full scan archives without a terminal record`() {
        val fixture = ArchiveFixture("mahjongcraft-history-full-scan-")
        fixture.database.appendPendingBatch(fixture.matchEvents().map(fixture::record))

        assertEquals(1, fixture.service.archiveReady(fixture.database, AuthoritativeStateSnapshot(), scanAllPending = true))
    }

    /** 資料庫已有終局紀錄、權威狀態已不再記錄序號的場次是已結束的場次，保留的事件不判為超出權威存檔。 */
    @Test
    fun `ended matches forgotten by the authoritative state are not orphans`() {
        val fixture = ArchiveFixture("mahjongcraft-history-ended-orphan-")
        fixture.database.appendPendingBatch(fixture.matchEvents().take(2).map(fixture::record))
        fixture.database.recordTerminals(listOf(HistoryTerminalRecord(fixture.matchId.toString(), fixture.tableId.toString(), 300, completed = false)))

        fixture.service.reconcile(fixture.database, HistoryRecordingState())

        assertEquals(null, fixture.service.lastArchiveError)
    }

    /** 權威狀態沒有紀錄、資料庫也沒有終局紀錄時，資料庫的事件超出權威存檔。 */
    @Test
    fun `events of an unknown match without an ending are orphans`() {
        val fixture = ArchiveFixture("mahjongcraft-history-unknown-orphan-")
        fixture.database.appendPendingBatch(fixture.matchEvents().take(2).map(fixture::record))

        fixture.service.reconcile(fixture.database, HistoryRecordingState())

        assertEquals(ORPHAN_ERROR, fixture.service.lastArchiveError)
    }

    /** 權威狀態仍記錄序號且比資料庫小時（存檔回溯），即使資料庫有終局紀錄仍判為超出權威存檔。 */
    @Test
    fun `a rolled back save is an orphan even when the match ended later`() {
        val fixture = ArchiveFixture("mahjongcraft-history-rollback-orphan-")
        fixture.database.appendPendingBatch(fixture.matchEvents().map(fixture::record))
        fixture.database.recordTerminals(listOf(HistoryTerminalRecord(fixture.matchId.toString(), fixture.tableId.toString(), 600, completed = true)))

        fixture.service.reconcile(fixture.database, HistoryRecordingState(nextSequenceByMatchId = mapOf(fixture.matchId to 3L)))

        assertEquals(ORPHAN_ERROR, fixture.service.lastArchiveError)
    }

    /**
     * 一場可封存對局的資料庫與對帳服務。
     *
     * @param prefix 暫存資料夾名稱前綴。
     */
    private class ArchiveFixture(prefix: String) {
        val tableId: Uuid = Uuid.random()
        val matchId: Uuid = Uuid.random()
        val path = createTempDirectory(prefix).resolve("history.sqlite")
        val database: SqliteHistoryDatabase = SqliteHistoryDatabase.open(path)
        private val registries = bundledPersistenceRegistries()
        private val mapper = HistoryRecordingPersistenceMapper(registries, Json)
        val service = HistoryArchiveService(
            mapper,
            registries,
            MahjongModuleRegistryImpl().apply { registerBundledRuleModules() },
            TableLocationRegistry(),
            Json,
        )

        /** 開局、一局、終局與返回房間的完整事件。 */
        fun matchEvents(): List<HistoryOutboxEvent> {
            val table = FakeTableStateFactory.create(
                id = tableId,
                players = listOf(
                    FakeMahjongPlayerFactory.create(Wind.EAST, discardPile = RiichiDiscardPile()),
                    FakeMahjongPlayerFactory.create(Wind.SOUTH, discardPile = RiichiDiscardPile()),
                    FakeMahjongPlayerFactory.create(Wind.WEST, discardPile = RiichiDiscardPile()),
                    FakeMahjongPlayerFactory.create(Wind.NORTH, discardPile = RiichiDiscardPile()),
                ),
                config = RiichiRuleConfig(),
                roundNumber = 1,
            )
            val facts = listOf(
                HistoryFact.MatchStarted(table, GameFlowConfig(), emptyMap()),
                HistoryFact.RoundStarted(table),
                HistoryFact.MatchCompleted("test:complete", emptyMap()),
                HistoryFact.ReturnedToRoom,
            )
            return facts.mapIndexed { index, fact ->
                HistoryOutboxEvent(
                    matchId = matchId,
                    venueId = tableId,
                    roundNumber = 1,
                    sequence = index + 1L,
                    transactionFirstSequence = index + 1L,
                    occurredAtEpochMillis = (index + 1L) * 100,
                    actorPlayerId = null,
                    fact = fact,
                )
            }
        }

        /** 寫進資料庫的待寫事件紀錄。 */
        fun record(event: HistoryOutboxEvent): PendingHistoryRecord = PendingHistoryRecord(
            event.matchId.toString(),
            event.sequence,
            event.roundNumber,
            event.occurredAtEpochMillis,
            1,
            Json.encodeToString(HistoryOutboxEventPersistenceDto.serializer(), mapper.encodePendingEvent(event)),
        )

        /** 直接在資料庫中把一筆事件的內容改成無法解碼的值。 */
        fun corruptPayload(sequence: Long) {
            DriverManager.getConnection("jdbc:sqlite:${path.toAbsolutePath()}").use { connection ->
                connection.prepareStatement("UPDATE history_pending_event SET payload = '{}' WHERE match_id = ? AND sequence = ?").use { statement ->
                    statement.setString(1, matchId.toString())
                    statement.setLong(2, sequence)
                    check(statement.executeUpdate() == 1) { "The event to corrupt does not exist" }
                }
            }
        }
    }

    private companion object {
        /** 資料庫事件超出權威存檔時的對帳錯誤摘要。 */
        const val ORPHAN_ERROR = "History database contains events beyond the authoritative save"
    }
}
