package com.doublemoon1119.mahjongcraft.platform.fabric.client.game

import com.doublemoon1119.mahjongcraft.logic.base.GameAction
import com.doublemoon1119.mahjongcraft.logic.table.toSnapshot
import com.doublemoon1119.mahjongcraft.platform.minecraft.tile.MinecraftTileAssetRegistryImpl
import com.doublemoon1119.mahjongcraft.platform.minecraft.tile.TileDisplayNameRegistryImpl
import com.doublemoon1119.mahjongcraft.platform.minecraft.tile.TileEmojiRegistryImpl
import com.doublemoon1119.mahjongcraft.testing.logic.table.FakeMahjongPlayerFactory
import com.doublemoon1119.mahjongcraft.testing.logic.table.FakeTableStateFactory
import net.minecraft.text.TranslatableTextContent
import kotlin.test.Test
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * [buildRoundResultChatMessage] 的單元測試——只涵蓋不需要真正 Minecraft client 執行環境的分支
 * （AI 玩家、非回合結束事件、沒有前一份快照可比較）；真人玩家名稱解析需要 `MinecraftClient.getInstance()`，
 * 留給實機驗證。
 */
class RoundResultChatNotifierTest {

    private val displayNameRegistry = TileDisplayNameRegistryImpl()
    private val tileAssetRegistry = MinecraftTileAssetRegistryImpl()
    private val tileEmojiRegistry = TileEmojiRegistryImpl()

    @Test
    fun `returns null for actions that do not end a round`() {
        val previous = fakeSnapshot(scores = listOf(25000, 25000))
        val current = fakeSnapshot(scores = listOf(25000, 25000))

        val message = buildRoundResultChatMessage(
            action = GameAction.Discard(kotlin.uuid.Uuid.random()),
            previousSnapshot = previous,
            newSnapshot = current,
            displayNameRegistry = displayNameRegistry,
            tileAssetRegistry = tileAssetRegistry,
            tileEmojiRegistry = tileEmojiRegistry,
        )

        assertNull(message)
    }

    @Test
    fun `returns null when there is no previous snapshot to compare against`() {
        val current = fakeSnapshot(scores = listOf(25000, 25000))

        val message = buildRoundResultChatMessage(
            action = GameAction.Tsumo,
            previousSnapshot = null,
            newSnapshot = current,
            displayNameRegistry = displayNameRegistry,
            tileAssetRegistry = tileAssetRegistry,
            tileEmojiRegistry = tileEmojiRegistry,
        )

        assertNull(message)
    }

    @Test
    fun `builds a tsumo message with only the changed players' score deltas`() {
        val previous = fakeSnapshot(scores = listOf(25000, 25000, 25000, 25000))
        val current = fakeSnapshot(
            ids = previous.players.map { it.id },
            scores = listOf(33000, 25000, 22000, 23000),
        )

        val message = buildRoundResultChatMessage(
            action = GameAction.Tsumo,
            previousSnapshot = previous,
            newSnapshot = current,
            displayNameRegistry = displayNameRegistry,
            tileAssetRegistry = tileAssetRegistry,
            tileEmojiRegistry = tileEmojiRegistry,
        )

        val broadcastContent = message?.content as? TranslatableTextContent
        kotlin.test.assertEquals("mahjongcraft.message.round_result_broadcast", broadcastContent?.key)

        val deltaLines = message?.siblings
            .orEmpty()
            .mapNotNull { it.content as? TranslatableTextContent }
            .filter { it.key == "mahjongcraft.message.round_result_score_delta" }
        val formattedDeltas = deltaLines.map { it.args[1].toString() }.toSet()

        assertTrue("+8000" in formattedDeltas, "expected a +8000 delta line, got: $formattedDeltas")
        assertTrue("-3000" in formattedDeltas, "expected a -3000 delta line, got: $formattedDeltas")
        assertTrue("-2000" in formattedDeltas, "expected a -2000 delta line, got: $formattedDeltas")
        kotlin.test.assertEquals(3, deltaLines.size, "player with unchanged score must not get a delta line")
    }

    /** AI 玩家（`aiStrategyKey` 非 null）避免觸發需要真正 client 執行環境的名稱解析分支。 */
    private fun fakeSnapshot(
        scores: List<Int>,
        ids: List<kotlin.uuid.Uuid> = scores.map { kotlin.uuid.Uuid.random() },
    ) = FakeTableStateFactory.create(
        players = ids.zip(scores).map { (id, score) ->
            FakeMahjongPlayerFactory.create(id = id, aiStrategyKey = "fake").copy(score = score)
        },
    ).toSnapshot(visibleHandPlayerIds = emptySet())
}
