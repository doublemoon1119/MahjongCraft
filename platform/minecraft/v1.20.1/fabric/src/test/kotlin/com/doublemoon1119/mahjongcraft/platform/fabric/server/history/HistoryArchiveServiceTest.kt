package com.doublemoon1119.mahjongcraft.platform.fabric.server.history

import com.doublemoon1119.mahjongcraft.flow.common.di.registerBuiltInRuleModules
import com.doublemoon1119.mahjongcraft.flow.common.game.history.HistoryFact
import com.doublemoon1119.mahjongcraft.flow.common.game.history.HistoryOutboxEvent
import com.doublemoon1119.mahjongcraft.flow.common.game.model.Game
import com.doublemoon1119.mahjongcraft.flow.common.game.model.GameFlowConfig
import com.doublemoon1119.mahjongcraft.flow.persistence.dto.history.HistoryOutboxEventPersistenceDto
import com.doublemoon1119.mahjongcraft.flow.persistence.dto.history.HistoryRecordingPersistenceMapper
import com.doublemoon1119.mahjongcraft.flow.persistence.dto.registry.buildBuiltInPersistenceRegistries
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
import kotlin.io.path.createTempDirectory
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.uuid.Uuid

/** 驗證完整返回房間後才刪除原始事件並保存可重啟的 Replay。 */
class HistoryArchiveServiceTest {
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
