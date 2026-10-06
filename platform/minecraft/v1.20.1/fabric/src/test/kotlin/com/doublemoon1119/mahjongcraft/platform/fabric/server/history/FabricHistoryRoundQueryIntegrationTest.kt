package com.doublemoon1119.mahjongcraft.platform.fabric.server.history

import com.doublemoon1119.mahjongcraft.flow.common.concurrency.CoroutineDispatchers
import com.doublemoon1119.mahjongcraft.flow.common.game.history.HistoryFact
import com.doublemoon1119.mahjongcraft.flow.common.game.history.HistoryOutboxEvent
import com.doublemoon1119.mahjongcraft.flow.common.game.history.query.HistoryQueryAccess
import com.doublemoon1119.mahjongcraft.flow.common.game.history.query.HistoryQueryErrorCode
import com.doublemoon1119.mahjongcraft.flow.common.game.history.query.HistoryQueryResult
import com.doublemoon1119.mahjongcraft.flow.common.game.history.query.HistoryQueryScope
import com.doublemoon1119.mahjongcraft.flow.common.game.history.replay.HistoryRoundPosition
import com.doublemoon1119.mahjongcraft.flow.common.game.history.replay.HistoryRoundState
import com.doublemoon1119.mahjongcraft.flow.common.game.model.GameFlowConfig
import com.doublemoon1119.mahjongcraft.flow.persistence.format.history.HistoryRecordingPersistenceMapper
import com.doublemoon1119.mahjongcraft.flow.persistence.format.history.replay.CompactReplayCodec
import com.doublemoon1119.mahjongcraft.flow.server.state.AuthoritativeStateStore
import com.doublemoon1119.mahjongcraft.logic.module.MahjongModuleRegistryImpl
import com.doublemoon1119.mahjongcraft.logic.rules.riichi.RiichiDiscardPile
import com.doublemoon1119.mahjongcraft.logic.rules.riichi.RiichiRuleConfig
import com.doublemoon1119.mahjongcraft.platform.minecraft.config.MinecraftHistoryConfig
import com.doublemoon1119.mahjongcraft.platform.minecraft.config.MinecraftServerConfig
import com.doublemoon1119.mahjongcraft.platform.minecraft.config.MinecraftServerConfigState
import com.doublemoon1119.mahjongcraft.platform.minecraft.table.TableLocationRegistry
import com.doublemoon1119.mahjongcraft.testing.flow.bundled.bundledPersistenceRegistries
import com.doublemoon1119.mahjongcraft.testing.logic.table.FakeMahjongPlayerFactory
import com.doublemoon1119.mahjongcraft.testing.logic.table.FakeTableStateFactory
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import java.sql.DriverManager
import kotlin.io.path.createTempDirectory
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.uuid.Uuid

/** 以正式 encoder、SQLite、writer 與 reader 驗證單局查詢接線及 session 失效。 */
class FabricHistoryRoundQueryIntegrationTest {
    /** 真實 compact Replay 能查閱事件與初始桌況，detach 後舊 session 不再讀取。 */
    @Test
    fun `formal writer reads encoded rounds and rejects detached session`(): Unit = runBlocking {
        val registries = bundledPersistenceRegistries()
        val table = FakeTableStateFactory.create(players = List(2) { FakeMahjongPlayerFactory.create(discardPile = RiichiDiscardPile()) }, config = RiichiRuleConfig())
        val matchId = Uuid.random()
        val events = listOf(
            HistoryOutboxEvent(matchId, table.id, 1, 1L, 1L, 1L, null, HistoryFact.MatchStarted(table, GameFlowConfig(), emptyMap())),
            HistoryOutboxEvent(matchId, table.id, 1, 2L, 2L, 2L, null, HistoryFact.MatchCompleted("test:completed", table.players.associate { it.id to it.score })),
        )
        val payload = CompactReplayCodec.encodeCompact(events, HistoryRecordingPersistenceMapper(registries), registries).toString()
        val path = createTempDirectory("history-round-integration-").resolve("history.sqlite")
        SqliteHistoryDatabase.open(path)
        DriverManager.getConnection("jdbc:sqlite:$path").use { connection ->
            connection.prepareStatement("INSERT INTO history_match(match_id, table_id, rule_id, status, started_at_epoch_millis, ended_at_epoch_millis) VALUES (?, ?, 'mahjongcraft:riichi', 'COMPLETED', 1, 2)").use {
                it.setString(1, matchId.toString())
                it.setString(2, table.id.toString())
                it.executeUpdate()
            }
            connection.prepareStatement("INSERT INTO history_replay(match_id, format_version, created_at_epoch_millis, payload) VALUES (?, 1, 2, ?)").use {
                it.setString(1, matchId.toString())
                it.setString(2, payload)
                it.executeUpdate()
            }
            table.players.forEach { player ->
                connection.prepareStatement("INSERT INTO history_participant(match_id, seat_index, player_id) VALUES (?, ?, ?)").use {
                    it.setString(1, matchId.toString())
                    it.setInt(2, player.initialSeatIndex)
                    it.setString(3, player.id.toString())
                    it.executeUpdate()
                }
            }
        }
        val writer = FabricHistoryOutboxWriter(
            store = AuthoritativeStateStore(),
            registries = registries,
            json = Json,
            dispatchers = QueryTestDispatchers,
            moduleRegistry = MahjongModuleRegistryImpl(),
            locations = TableLocationRegistry(),
            configState = MinecraftServerConfigState(MinecraftServerConfig(history = MinecraftHistoryConfig(retentionDays = 0, includeAiMatches = true))),
            retentionCoordinator = HistoryRetentionCoordinator(),
            replayProjectionRegistry = buildTestHistoryReplayProjectionRegistry(),
        )
        try {
            writer.attach(path)
            val session = writer.currentSessionId
            val access = HistoryQueryAccess(table.players.first().id, false)
            val page = assertIs<HistoryManagementResult.Success<*>>(writer.queryRoundEvents(access, matchId, HistoryQueryScope.OWN, 1, 0, 20, session))
            assertIs<HistoryQueryResult.Success<*>>(page.value)
            val state = writer.queryRoundState(access, matchId, HistoryQueryScope.OWN, 1, HistoryRoundPosition.Initial, session)
            val value = assertIs<HistoryQueryResult.Success<*>>(assertIs<HistoryManagementResult.Success<*>>(state).value)
            assertEquals(table.players.size, assertIs<HistoryRoundState>(value.value).players.size)
            assertIs<HistoryQueryResult.Success<*>>(
                assertIs<HistoryManagementResult.Success<*>>(writer.confirmRoundPublication(access, matchId, HistoryQueryScope.OWN, session)).value,
            )
            // 讀取完成後出現清理收據，最後公開確認必須拒絕先前已讀入的內容。
            DriverManager.getConnection("jdbc:sqlite:$path").use { connection ->
                connection.prepareStatement("INSERT INTO history_tombstone(match_id, pruned_at_epoch_millis, reason) VALUES (?, 3, 'test')").use {
                    it.setString(1, matchId.toString())
                    it.executeUpdate()
                }
            }
            val confirmation = assertIs<HistoryManagementResult.Success<*>>(writer.confirmRoundPublication(access, matchId, HistoryQueryScope.OWN, session))
            assertEquals(HistoryQueryErrorCode.NOT_AVAILABLE, assertIs<HistoryQueryResult.Failure>(confirmation.value).error.code)
            writer.detach()
            assertIs<HistoryManagementResult.SessionChanged>(writer.queryRoundState(access, matchId, HistoryQueryScope.OWN, 1, HistoryRoundPosition.Initial, session))
        } finally {
            writer.detach()
        }
    }

    /** 避免測試依賴 Minecraft dispatcher 的實際平台主迴圈。 */
    private object QueryTestDispatchers : CoroutineDispatchers {
        /** 測試不操作平台物件，可直接接續主執行緒作業。 */
        override val main: CoroutineDispatcher = Dispatchers.Unconfined

        /** 使用正式背景 I/O 執行資料庫工作。 */
        override val io: CoroutineDispatcher = Dispatchers.IO

        /** 使用預設 dispatcher 執行計算。 */
        override val default: CoroutineDispatcher = Dispatchers.Default
    }
}
