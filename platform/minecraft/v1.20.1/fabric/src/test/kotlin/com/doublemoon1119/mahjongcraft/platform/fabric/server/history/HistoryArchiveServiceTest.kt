package com.doublemoon1119.mahjongcraft.platform.fabric.server.history

import com.doublemoon1119.mahjongcraft.flow.common.di.registerBuiltInRuleModules
import com.doublemoon1119.mahjongcraft.flow.common.game.history.HistoryFact
import com.doublemoon1119.mahjongcraft.flow.common.game.history.HistoryOutboxEvent
import com.doublemoon1119.mahjongcraft.flow.common.game.model.Game
import com.doublemoon1119.mahjongcraft.flow.common.game.model.GameFlowConfig
import com.doublemoon1119.mahjongcraft.flow.persistence.format.history.HistoryOutboxEventPersistenceDto
import com.doublemoon1119.mahjongcraft.flow.persistence.format.history.HistoryRecordingPersistenceMapper
import com.doublemoon1119.mahjongcraft.flow.persistence.format.history.replay.CompactReplayCodec
import com.doublemoon1119.mahjongcraft.flow.persistence.format.registry.buildBuiltInPersistenceRegistries
import com.doublemoon1119.mahjongcraft.flow.server.state.AuthoritativeStateSnapshot
import com.doublemoon1119.mahjongcraft.logic.module.MahjongModuleRegistryImpl
import com.doublemoon1119.mahjongcraft.logic.rules.riichi.RiichiDiscardPile
import com.doublemoon1119.mahjongcraft.logic.rules.riichi.RiichiRuleConfig
import com.doublemoon1119.mahjongcraft.logic.table.Wind
import com.doublemoon1119.mahjongcraft.platform.minecraft.table.TableLocationRegistry
import com.doublemoon1119.mahjongcraft.testing.logic.table.FakeMahjongPlayerFactory
import com.doublemoon1119.mahjongcraft.testing.logic.table.FakeTableStateFactory
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import java.sql.DriverManager
import kotlin.io.path.createTempDirectory
import kotlin.test.Test
import kotlin.test.assertEquals
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
                tableId = tableId,
                roundNumber = 1,
                sequence = 1,
                transactionFirstSequence = 1,
                occurredAtEpochMillis = 100,
                actorPlayerId = null,
                fact = HistoryFact.MatchStarted(table, GameFlowConfig()),
            ),
            HistoryOutboxEvent(
                matchId = matchId,
                tableId = tableId,
                roundNumber = 1,
                sequence = 2,
                transactionFirstSequence = 2,
                occurredAtEpochMillis = 200,
                actorPlayerId = null,
                fact = HistoryFact.RoundStarted(table),
            ),
            HistoryOutboxEvent(
                matchId = matchId,
                tableId = tableId,
                roundNumber = 1,
                sequence = 3,
                transactionFirstSequence = 3,
                occurredAtEpochMillis = 300,
                actorPlayerId = null,
                fact = HistoryFact.RoundStarted(table),
            ),
            HistoryOutboxEvent(
                matchId = matchId,
                tableId = tableId,
                roundNumber = 1,
                sequence = 4,
                transactionFirstSequence = 4,
                occurredAtEpochMillis = 400,
                actorPlayerId = null,
                fact = HistoryFact.MatchCompleted("test:complete", emptyMap()),
            ),
            HistoryOutboxEvent(
                matchId = matchId,
                tableId = tableId,
                roundNumber = 1,
                sequence = 5,
                transactionFirstSequence = 5,
                occurredAtEpochMillis = 500,
                actorPlayerId = null,
                fact = HistoryFact.ReturnedToRoom,
            ),
        )
        val registries = buildBuiltInPersistenceRegistries()
        val mapper = HistoryRecordingPersistenceMapper(registries, Json)
        val modules = MahjongModuleRegistryImpl().apply { registerBuiltInRuleModules() }
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

        assertEquals(1, service.archiveReady(database, AuthoritativeStateSnapshot()))
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
            HistoryOutboxEvent(matchId, tableId, 1, 1, occurredAtEpochMillis = 100, actorPlayerId = null, fact = HistoryFact.MatchStarted(table, GameFlowConfig())),
            HistoryOutboxEvent(matchId, tableId, 1, 2, occurredAtEpochMillis = 200, actorPlayerId = null, fact = HistoryFact.MatchCompleted("test:complete", emptyMap())),
            HistoryOutboxEvent(matchId, tableId, 1, 3, occurredAtEpochMillis = 300, actorPlayerId = null, fact = HistoryFact.ReturnedToRoom),
        )
        val registries = buildBuiltInPersistenceRegistries()
        val mapper = HistoryRecordingPersistenceMapper(registries, Json)
        val modules = MahjongModuleRegistryImpl().apply { registerBuiltInRuleModules() }
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

        assertEquals(0, service.archiveReady(database, AuthoritativeStateSnapshot()))
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
        assertEquals(0, service.archiveReady(database, AuthoritativeStateSnapshot(games = mapOf(tableId to active))))
        assertEquals(3, database.readPending(matchId.toString()).size)
        assertEquals(1, service.archiveReady(database, AuthoritativeStateSnapshot()))
        val reopened = SqliteHistoryDatabase.open(path)
        assertEquals(emptyList(), reopened.readPending(matchId.toString()))
        assertTrue(matchId.toString() in reopened.readReplayIds())
    }
}
