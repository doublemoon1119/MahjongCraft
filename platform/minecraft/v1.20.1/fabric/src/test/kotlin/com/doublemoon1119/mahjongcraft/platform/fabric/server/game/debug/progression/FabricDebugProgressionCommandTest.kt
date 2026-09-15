package com.doublemoon1119.mahjongcraft.platform.fabric.server.game.debug.progression

import com.doublemoon1119.mahjongcraft.flow.common.concurrency.AppCoroutineScope
import com.doublemoon1119.mahjongcraft.flow.common.game.model.Game
import com.doublemoon1119.mahjongcraft.flow.server.game.repository.GameRepository
import com.doublemoon1119.mahjongcraft.flow.server.membership.repository.PlayerMembershipRepository
import com.doublemoon1119.mahjongcraft.logic.table.TableState
import com.doublemoon1119.mahjongcraft.platform.fabric.server.game.debug.support.DebugPlayerTableScope
import com.doublemoon1119.mahjongcraft.testing.flow.common.concurrency.TestCoroutineDispatchers
import com.mojang.brigadier.tree.CommandNode
import net.minecraft.server.command.ServerCommandSource
import kotlin.coroutines.CoroutineContext
import kotlin.coroutines.EmptyCoroutineContext
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.uuid.Uuid

/** 驗證終局推進預覽子指令樹的情境 literal 結構。 */
class FabricDebugProgressionCommandTest {
    private val command = FabricDebugProgressionCommand(
        gameRepository = UnusedGameRepository,
        playerTableScope = DebugPlayerTableScope(
            membershipRepository = UnusedMembershipRepository,
            scope = UnusedScope,
            dispatchers = TestCoroutineDispatchers(),
        ),
    )

    /** `match_progression` 本身不可執行，必須指定一個情境。 */
    @Test
    fun `match progression requires an explicit scenario`() {
        val node = command.buildMatchProgressionCommand().build()

        assertEquals("match_progression", node.name)
        assertNull(node.command, "match_progression is not executable on its own")
    }

    /** 八個內建情境各自掛一個可直接執行的 literal，且沒有更深的子節點。 */
    @Test
    fun `match progression keeps every built-in scenario literal`() {
        val children = command.buildMatchProgressionCommand().build().children

        assertEquals(
            setOf(
                "east_overtime",
                "east_end",
                "west_overtime",
                "extra_sudden_death",
                "extra_limit",
                "dealer_agari_yame",
                "dealer_tenpai_yame",
                "bust",
            ),
            children.map(CommandNode<ServerCommandSource>::getName).toSet(),
        )
        children.forEach { scenario ->
            assertNotNull(scenario.command, "${scenario.name} executes directly")
            assertEquals(
                emptySet(),
                scenario.children.map(CommandNode<ServerCommandSource>::getName).toSet(),
                "${scenario.name} takes no argument",
            )
        }
    }

    /** 指令樹建構不觸碰任何倉庫；每次呼叫都回傳獨立的節點實例。 */
    private object UnusedGameRepository : GameRepository {
        override suspend fun getGame(gameId: Uuid): Game? = error("Unexpected repository access")

        override suspend fun getAllGameIds(): Set<Uuid> = error("Unexpected repository access")

        override suspend fun getTableState(gameId: Uuid): TableState? = error("Unexpected repository access")

        override suspend fun setTableState(state: TableState) = error("Unexpected repository access")

        override suspend fun removeTableState(gameId: Uuid) = error("Unexpected repository access")

        override suspend fun clearAll() = error("Unexpected repository access")

        override suspend fun <T> updateGame(gameId: Uuid, block: suspend (Game?) -> Pair<Game?, T>): T = error("Unexpected repository access")

        override suspend fun <T> update(gameId: Uuid, block: suspend (TableState?) -> Pair<TableState?, T>): T = error("Unexpected repository access")
    }

    /** 指令樹建構不解析任何玩家入座狀態。 */
    private object UnusedMembershipRepository : PlayerMembershipRepository {
        override suspend fun claim(playerId: Uuid, tableId: Uuid): Boolean = error("Unexpected membership lookup")

        override suspend fun getTableId(playerId: Uuid): Uuid? = error("Unexpected membership lookup")

        override suspend fun release(playerId: Uuid, tableId: Uuid) = error("Unexpected membership lookup")

        override suspend fun replaceAll(tableIdsByPlayerId: Map<Uuid, Uuid>) = error("Unexpected membership lookup")

        override suspend fun clearAll() = error("Unexpected membership lookup")
    }

    /** 指令樹建構不啟動任何協程。 */
    private object UnusedScope : AppCoroutineScope {
        override val coroutineContext: CoroutineContext = EmptyCoroutineContext

        override fun cancel() = error("Unexpected scope cancellation")

        override suspend fun shutdown(timeoutMillis: Long) = error("Unexpected scope shutdown")
    }
}
