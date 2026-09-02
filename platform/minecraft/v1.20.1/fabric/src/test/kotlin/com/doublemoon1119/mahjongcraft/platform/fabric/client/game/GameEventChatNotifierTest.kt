package com.doublemoon1119.mahjongcraft.platform.fabric.client.game

import com.doublemoon1119.mahjongcraft.logic.base.GameAction
import com.doublemoon1119.mahjongcraft.logic.rules.riichi.RiichiRuleConfig
import com.doublemoon1119.mahjongcraft.logic.rules.riichi.RiichiRuleModule
import com.doublemoon1119.mahjongcraft.logic.table.Wind
import com.doublemoon1119.mahjongcraft.logic.table.toSnapshot
import com.doublemoon1119.mahjongcraft.platform.minecraft.action.GameActionDisplayNameRegistryImpl
import com.doublemoon1119.mahjongcraft.platform.minecraft.settlement.ExhaustiveDrawReasonDisplayNameRegistryImpl
import com.doublemoon1119.mahjongcraft.platform.minecraft.tile.MinecraftTileAssetRegistryImpl
import com.doublemoon1119.mahjongcraft.platform.minecraft.tile.TileDisplayNameRegistryImpl
import com.doublemoon1119.mahjongcraft.platform.minecraft.tile.TileEmojiRegistryImpl
import com.doublemoon1119.mahjongcraft.testing.logic.table.FakeMahjongPlayerFactory
import com.doublemoon1119.mahjongcraft.testing.logic.table.FakeTableStateFactory
import net.minecraft.text.HoverEvent
import net.minecraft.text.Text
import net.minecraft.text.TranslatableTextContent
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * [buildRoundResultChatMessage]／[buildMatchResultChatMessage] 的單元測試
 * ——只涵蓋不需要真正 Minecraft client 執行環境的分支（AI 玩家、非對應事件、沒有前一份快照可比較）；
 * 真人玩家名稱解析需要 `MinecraftClient.getInstance()`，留給實機驗證。
 */
class GameEventChatNotifierTest {

    private val displayNameRegistry = TileDisplayNameRegistryImpl()
    private val actionDisplayNameRegistry = GameActionDisplayNameRegistryImpl()
    private val tileAssetRegistry = MinecraftTileAssetRegistryImpl()
    private val tileEmojiRegistry = TileEmojiRegistryImpl()
    private val exhaustiveDrawReasonDisplayNameRegistry = ExhaustiveDrawReasonDisplayNameRegistryImpl()

    /** 排名邏輯測的是 [MahjongRuleModule] 介面的預設實作，用哪個規則模組不影響結果——這裡沒有
     *  覆寫 `compareForRoundRanking`／`compareForMatchRanking`，借用即可，跟快照本身用的
     *  `FakeMahjongRuleConfig` 是不是同一種規則無關。 */
    private val module = RiichiRuleModule(id = "riichi", config = RiichiRuleConfig())

    @Test
    fun `returns null for actions that do not end a round`() {
        val previous = fakeSnapshot(scores = listOf(25000, 25000))
        val current = fakeSnapshot(scores = listOf(25000, 25000))

        val message = buildRoundResultChatMessage(
            action = GameAction.Discard(kotlin.uuid.Uuid.random()),
            previousSnapshot = previous,
            newSnapshot = current,
            module = module,
            actionDisplayNameRegistry = actionDisplayNameRegistry,
            displayNameRegistry = displayNameRegistry,
            tileAssetRegistry = tileAssetRegistry,
            tileEmojiRegistry = tileEmojiRegistry,
            exhaustiveDrawReasonDisplayNameRegistry = exhaustiveDrawReasonDisplayNameRegistry,
            playerDisplayName = { id, _ -> id.toString().take(4) },
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
            module = module,
            actionDisplayNameRegistry = actionDisplayNameRegistry,
            displayNameRegistry = displayNameRegistry,
            tileAssetRegistry = tileAssetRegistry,
            tileEmojiRegistry = tileEmojiRegistry,
            exhaustiveDrawReasonDisplayNameRegistry = exhaustiveDrawReasonDisplayNameRegistry,
            playerDisplayName = { id, _ -> id.toString().take(4) },
        )

        assertNull(message)
    }

    @Test
    fun `builds a tsumo message listing every player's rank and score movement, not just the ones whose score changed`() {
        val dId = kotlin.uuid.Uuid.random()
        val bId = kotlin.uuid.Uuid.random()
        val cId = kotlin.uuid.Uuid.random()
        val aId = kotlin.uuid.Uuid.random()
        // B 自己的分數完全沒變（25000 → 25000），但 A 從 20000 衝到 26000 把 B 擠出第 2 名、
        // 掉到第 3 名——這是刻意設計的情境，證明「名次會不會變」不能只看自己的分數變化，B 這種
        // 玩家過去只看分數差異會被完全忽略，現在一定要出現在結果裡才對。
        val previous = fakeSnapshot(
            ids = listOf(dId, bId, cId, aId),
            scores = listOf(30000, 25000, 25000, 20000),
        )
        val current = fakeSnapshot(
            ids = listOf(dId, bId, cId, aId),
            scores = listOf(30000, 25000, 25000, 26000),
        )

        val message = buildRoundResultChatMessage(
            action = GameAction.Tsumo,
            previousSnapshot = previous,
            newSnapshot = current,
            module = module,
            actionDisplayNameRegistry = actionDisplayNameRegistry,
            displayNameRegistry = displayNameRegistry,
            tileAssetRegistry = tileAssetRegistry,
            tileEmojiRegistry = tileEmojiRegistry,
            exhaustiveDrawReasonDisplayNameRegistry = exhaustiveDrawReasonDisplayNameRegistry,
            playerDisplayName = { id, _ -> id.toString().take(4) },
        )

        val broadcastContent = message?.content as? TranslatableTextContent
        kotlin.test.assertEquals("mahjongcraft.message.round_result_broadcast", broadcastContent?.key)

        val playerLinesById = message?.hoverDetails()?.siblings
            .orEmpty()
            .mapNotNull { it.content as? TranslatableTextContent }
            .filter { it.key == "mahjongcraft.message.round_result_player_line" }
            .associateBy { it.args[0].toString().removePrefix("AI-") }
        kotlin.test.assertEquals(4, playerLinesById.size, "every player must appear, even ones whose own score never moved")

        val bLine = playerLinesById.getValue(bId.toString().take(4))
        assertEquals(listOf("2", "3", "↓", "25000", "25000"), bLine.args.drop(1).map { it.toString() }, "B's own score never changed, but A overtaking them should still drop them from rank 2 to rank 3")

        val aLine = playerLinesById.getValue(aId.toString().take(4))
        assertEquals(listOf("4", "2", "↑", "20000", "26000"), aLine.args.drop(1).map { it.toString() }, "A climbed from last place to 2nd")

        val dLine = playerLinesById.getValue(dId.toString().take(4))
        assertEquals(listOf("1", "1", "→", "30000", "30000"), dLine.args.drop(1).map { it.toString() }, "D stayed in 1st with no score change")
    }

    @Test
    fun `round result ranks players by this hand's seat when scores are tied, not the original seat`() {
        val eastId = kotlin.uuid.Uuid.random()
        val southId = kotlin.uuid.Uuid.random()
        val westId = kotlin.uuid.Uuid.random()
        val northId = kotlin.uuid.Uuid.random()
        // 東家跟南家同分；起家（initialSeat）刻意跟這一局的座位（seatWind）反過來排——南家的
        // initialSeat 是西、seatWind 是東，東家的 initialSeat 是東、seatWind 是南——如果
        // 用錯欄位（誤用 initialSeat），排序會反過來，藉此確認回合排名真的是比 seatWind。
        val players = listOf(
            FakeMahjongPlayerFactory.create(id = eastId, initialSeat = Wind.EAST, aiStrategyKey = "fake")
                .copy(score = 25000, seatWind = Wind.SOUTH),
            FakeMahjongPlayerFactory.create(id = southId, initialSeat = Wind.WEST, aiStrategyKey = "fake")
                .copy(score = 25000, seatWind = Wind.EAST),
            FakeMahjongPlayerFactory.create(id = westId, initialSeat = Wind.SOUTH, aiStrategyKey = "fake")
                .copy(score = 30000, seatWind = Wind.WEST),
            FakeMahjongPlayerFactory.create(id = northId, initialSeat = Wind.NORTH, aiStrategyKey = "fake")
                .copy(score = 20000, seatWind = Wind.NORTH),
        )
        val previous = FakeTableStateFactory.create(players = players).toSnapshot(visibleHandPlayerIds = emptySet())
        val current = FakeTableStateFactory.create(players = players.map { it.copy(score = it.score + 1) }).toSnapshot(visibleHandPlayerIds = emptySet())

        val message = buildRoundResultChatMessage(
            action = GameAction.Tsumo,
            previousSnapshot = previous,
            newSnapshot = current,
            module = module,
            actionDisplayNameRegistry = actionDisplayNameRegistry,
            displayNameRegistry = displayNameRegistry,
            tileAssetRegistry = tileAssetRegistry,
            tileEmojiRegistry = tileEmojiRegistry,
            exhaustiveDrawReasonDisplayNameRegistry = exhaustiveDrawReasonDisplayNameRegistry,
            playerDisplayName = { id, _ -> id.toString().take(4) },
        )

        val playerLines = message?.hoverDetails()?.siblings
            .orEmpty()
            .mapNotNull { it.content as? TranslatableTextContent }
            .filter { it.key == "mahjongcraft.message.round_result_player_line" }
        val orderedPlayerIdPrefixes = playerLines.map { it.args[0].toString().removePrefix("AI-") }

        assertEquals(
            listOf(westId, southId, eastId, northId).map { it.toString().take(4) },
            orderedPlayerIdPrefixes,
            "Expected west (highest score), then south before east (tied score, south sits closer to this hand's east), then north.",
        )
    }

    /** 取得簡短 round-result 訊息的 hover 詳情。 */
    private fun Text.hoverDetails(): Text? = style.hoverEvent?.getValue(HoverEvent.Action.SHOW_TEXT)
        ?: siblings.firstNotNullOfOrNull { it.hoverDetails() }

    @Test
    fun `returns null for match result when the action is not match ended`() {
        val snapshot = fakeSnapshot(scores = listOf(25000, 25000))

        assertNull(buildMatchResultChatMessage(GameAction.Tsumo, snapshot, module))
    }

    @Test
    fun `breaks tied final scores by seat proximity to the original dealer`() {
        val eastId = kotlin.uuid.Uuid.random()
        val southId = kotlin.uuid.Uuid.random()
        val westId = kotlin.uuid.Uuid.random()
        val northId = kotlin.uuid.Uuid.random()
        // 起家第二位跟第三位同分（25000），照固定起家順位第二位名次要在第三位前面；
        // 同時刻意把兩人的本局風位反過來，確認終局同分判準不受 seatWind 影響。
        val snapshot = FakeTableStateFactory.create(
            players = listOf(
                FakeMahjongPlayerFactory.create(id = eastId, initialSeat = Wind.EAST, aiStrategyKey = "fake").copy(score = 30000),
                FakeMahjongPlayerFactory.create(id = southId, initialSeat = Wind.SOUTH, aiStrategyKey = "fake")
                    .copy(score = 25000, seatWind = Wind.NORTH),
                FakeMahjongPlayerFactory.create(id = northId, initialSeat = Wind.NORTH, aiStrategyKey = "fake")
                    .copy(score = 25000, seatWind = Wind.SOUTH),
                FakeMahjongPlayerFactory.create(id = westId, initialSeat = Wind.WEST, aiStrategyKey = "fake").copy(score = 20000),
            ),
        ).toSnapshot(visibleHandPlayerIds = emptySet())

        val message = buildMatchResultChatMessage(
            GameAction.MatchEnded,
            snapshot,
            module,
        ) { id, _ -> id.toString().take(4) }

        assertEquals(false, message?.hoverDetails()?.string?.startsWith("\n"))
        val rankingLines = message?.hoverDetails()?.siblings
            .orEmpty()
            .mapNotNull { it.content as? TranslatableTextContent }
            .filter { it.key == "mahjongcraft.message.ranking_line" }
        val orderedPlayerIdPrefixes = rankingLines.map { it.args[1].toString() }

        assertEquals(
            listOf(eastId, southId, northId, westId).map { it.toString().take(4) },
            orderedPlayerIdPrefixes.map { it.removePrefix("AI-") },
            "Expected the second initial seat before the third when their final scores are tied.",
        )
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
