package com.doublemoon1119.mahjongcraft.platform.minecraft.character

import com.doublemoon1119.mahjongcraft.flow.common.game.model.Game
import com.doublemoon1119.mahjongcraft.flow.common.game.model.GameFlowConfig
import com.doublemoon1119.mahjongcraft.flow.common.room.model.RoomAdmissionState
import com.doublemoon1119.mahjongcraft.flow.common.room.model.RoomParticipantIdentity
import com.doublemoon1119.mahjongcraft.flow.server.game.service.GameAbortHandler
import com.doublemoon1119.mahjongcraft.platform.minecraft.api.event.MinecraftMatchAbortReasonIds
import com.doublemoon1119.mahjongcraft.platform.minecraft.config.MissingAiPlayerPolicy
import com.doublemoon1119.mahjongcraft.platform.minecraft.table.TableInteractionReach
import com.doublemoon1119.mahjongcraft.testing.logic.table.FakeTableStateFactory
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.uuid.Uuid

/** 用權威對局與假的世界查詢測試缺席政策，不模擬原生世界或加入斷線計時。 */
class WorldParticipantPresenceMonitorTest {
    /** 同一筆對局即使有多個缺席者，也只中止一次，不產生正常勝負。 */
    @Test
    fun `missing world participants abort once per match through the existing handler`() = runTest {
        val game = game()
        val calls = mutableListOf<Game>()
        val monitor = WorldParticipantPresenceMonitor(
            active = { true },
            policy = { MissingAiPlayerPolicy.ABORT_MATCH },
            tableActive = { true },
            available = { _, _ -> false },
            abortHandler = GameAbortHandler { expected, reason, diagnostic ->
                assertEquals(MinecraftMatchAbortReasonIds.AI_PLAYER_MISSING, reason)
                assertTrue(diagnostic.contains("memberId="))
                calls += expected
                true
            },
        )
        monitor.reconcile(listOf(game))
        assertEquals(listOf(game), calls)
    }

    /** 繼續政策不查找替身、不變更成員，也不呼叫中止。 */
    @Test
    fun `continue policy leaves the game unchanged without replacing or aborting actors`() = runTest {
        val game = game()
        WorldParticipantPresenceMonitor(
            active = { true },
            policy = { MissingAiPlayerPolicy.CONTINUE_MATCH },
            tableActive = { error("Continue policy does not require a world scan") },
            available = { _, _ -> error("No replacement lookup") },
            abortHandler = GameAbortHandler { _, _, _ -> error("Continue policy cannot abort") },
        ).reconcile(listOf(game))
    }

    /** 桌子卸載時不把無法查詢的實體當作缺席。 */
    @Test
    fun `an inactive table never triggers missing participant policy`() = runTest {
        WorldParticipantPresenceMonitor(
            active = { true },
            policy = { MissingAiPlayerPolicy.ABORT_MATCH },
            tableActive = { false },
            available = { _, _ -> error("An inactive table must not query entities") },
            abortHandler = GameAbortHandler { _, _, _ -> error("An unloaded table cannot abort") },
        ).reconcile(listOf(game()))
    }

    /** 玩家、內建自有代理與其他來源不套用世界實體政策；仍存在的離座者也不缺席。 */
    @Test
    fun `present actors and non world sources never abort`() = runTest {
        val game = game()
        val other = game.copy(roomAdmission = RoomAdmissionState(memberIdentities = game.roomPlayerIds.associateWith { RoomParticipantIdentity("example:other", it) }))
        var checked = 0
        WorldParticipantPresenceMonitor(
            active = { true },
            policy = { MissingAiPlayerPolicy.ABORT_MATCH },
            tableActive = { true },
            available = { _, identity ->
                assertEquals(SeatedParticipantSources.WORLD_ENTITY, identity.sourceId)
                checked++
                true
            },
            abortHandler = GameAbortHandler { _, _, _ -> error("Present actors cannot abort") },
        ).reconcile(listOf(game, other))
        assertEquals(game.roomAdmission.memberIdentities.size, checked)
    }

    /** 正常完賽與關服不再依缺席政策變更結果。 */
    @Test
    fun `completed matches and stopped sessions do not query or abort`() = runTest {
        var active = true
        val monitor = WorldParticipantPresenceMonitor(
            active = { active },
            policy = { MissingAiPlayerPolicy.ABORT_MATCH },
            tableActive = { error("No query after completion or shutdown") },
            available = { _, _ -> error("No entity query") },
            abortHandler = GameAbortHandler { _, _, _ -> error("No abort after completion or shutdown") },
        )
        monitor.reconcile(listOf(game().copy(isMatchOver = true)))
        active = false
        monitor.reconcile(listOf(game()))
    }

    /** 舊檢查中止一桌後若 session 已改變，不會繼續操作下一桌。 */
    @Test
    fun `a session change during a suspended handler stops the remaining batch`() = runTest {
        var active = true
        var count = 0
        val monitor = WorldParticipantPresenceMonitor(
            active = { active },
            policy = { MissingAiPlayerPolicy.ABORT_MATCH },
            tableActive = { true },
            available = { _, _ -> false },
            abortHandler = GameAbortHandler { _, _, _ ->
                count++
                active = false
                true
            },
        )
        monitor.reconcile(listOf(game(), game()))
        assertEquals(1, count)
    }

    /** 與原生管理器相同的桌邊範圍，讓鄰格下座與真正離場有不同結果。 */
    @Test
    fun `nearby dismounts remain present while an actor outside table reach is missing`() = runTest {
        var x = 3.5
        var aborts = 0
        val game = game()
        val monitor = WorldParticipantPresenceMonitor(
            active = { true },
            policy = { MissingAiPlayerPolicy.ABORT_MATCH },
            tableActive = { true },
            available = { _, _ -> TableInteractionReach.distance(x, 64.0, 0.5, 0, 64, 0) <= TableInteractionReach.SURVIVAL },
            abortHandler = GameAbortHandler { _, _, _ ->
                aborts++
                true
            },
        )
        monitor.reconcile(listOf(game))
        assertEquals(0, aborts)
        x = 20.0
        monitor.reconcile(listOf(game))
        assertEquals(1, aborts)
    }

    /** 建立兩名世界實體與其他正常席位共用的權威對局。 */
    private fun game(): Game {
        val table = FakeTableStateFactory.create()
        val ids = table.players.map { it.id }
        return Game(
            tableState = table,
            flowConfig = GameFlowConfig(),
            roomPlayerIds = ids,
            aiPlayerStrategyKeys = ids.take(2).associateWith { "example:ai" },
            roomAdmission = RoomAdmissionState(memberIdentities = ids.take(2).associateWith { RoomParticipantIdentity(SeatedParticipantSources.WORLD_ENTITY, Uuid.random()) }),
        )
    }
}
