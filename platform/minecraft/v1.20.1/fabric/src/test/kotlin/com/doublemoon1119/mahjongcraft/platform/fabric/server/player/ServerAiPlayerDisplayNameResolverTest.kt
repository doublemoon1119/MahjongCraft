package com.doublemoon1119.mahjongcraft.platform.fabric.server.player

import com.doublemoon1119.mahjongcraft.flow.common.game.model.Game
import com.doublemoon1119.mahjongcraft.flow.common.game.model.GameFlowConfig
import com.doublemoon1119.mahjongcraft.logic.table.Wind
import com.doublemoon1119.mahjongcraft.platform.minecraft.character.AiCharacterSnapshot
import com.doublemoon1119.mahjongcraft.testing.logic.table.FakeMahjongPlayerFactory
import com.doublemoon1119.mahjongcraft.testing.logic.table.FakeTableStateFactory
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.uuid.Uuid

/** 伺服器名稱依當場快照與原房間順序產生，不在初始化時讀取保存狀態。 */
class ServerAiPlayerDisplayNameResolverTest {
    /** 角色快照在實際解析時才讀取；洗牌後仍沿用房間名稱順序。 */
    @Test
    fun `resolves lazily with room order instead of shuffled seats`() {
        val table = FakeTableStateFactory.create(players = Wind.entries.map { FakeMahjongPlayerFactory.create(initialSeat = it) })
        val actor = table.players[0].id
        val firstBot = table.players[1].id
        val secondBot = table.players[2].id
        val game = Game(
            tableState = table,
            flowConfig = GameFlowConfig(),
            roomPlayerIds = table.players.map { it.id }.reversed(),
            aiPlayerStrategyKeys = listOf(actor, firstBot, secondBot).associateWith { "example:strategy" },
        )
        var reads = 0
        val names = ServerAiPlayerDisplayNameResolver { matchId ->
            assertEquals(game.matchId, matchId)
            reads++
            mapOf(actor to AiCharacterSnapshot("example:actor", null, "Actor"))
        }

        assertEquals(0, reads)
        assertEquals("AI 1", names.resolve(game, secondBot))
        assertEquals("AI 2", names.resolve(game, firstBot))
        assertEquals("Actor", names.resolve(game, actor))
        assertEquals(3, reads)
    }

    /** 同桌不同場次各用自己的快照，不從其他場次推算編號。 */
    @Test
    fun `different matches use only their captured character names`() {
        val table = FakeTableStateFactory.create()
        val actor = table.players[0].id
        val bot = table.players[1].id
        val game = Game(
            tableState = table,
            flowConfig = GameFlowConfig(),
            aiPlayerStrategyKeys = listOf(actor, bot).associateWith { "example:strategy" },
        )
        val snapshots = mapOf(game.matchId to mapOf(actor to AiCharacterSnapshot("example:actor", null, "Actor")))
        val names = ServerAiPlayerDisplayNameResolver { matchId -> snapshots[matchId].orEmpty() }

        assertEquals("AI 1", names.resolve(game, bot))
        assertEquals("AI 2", names.resolve(game.copy(matchId = Uuid.random()), bot))
    }
}
