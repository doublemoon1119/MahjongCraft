package com.doublemoon1119.mahjongcraft.platform.fabric.client.game

import com.doublemoon1119.mahjongcraft.flow.common.game.model.BuiltInRoundOutcomeIds
import com.doublemoon1119.mahjongcraft.logic.base.GameAction
import com.doublemoon1119.mahjongcraft.logic.rules.riichi.RiichiRuleConfig
import com.doublemoon1119.mahjongcraft.logic.rules.riichi.RiichiRuleModule
import com.doublemoon1119.mahjongcraft.logic.table.TableStateSnapshot
import com.doublemoon1119.mahjongcraft.logic.table.Wind
import com.doublemoon1119.mahjongcraft.logic.table.toSnapshot
import com.doublemoon1119.mahjongcraft.platform.minecraft.action.BuiltInGameActionIds
import com.doublemoon1119.mahjongcraft.platform.minecraft.action.GameActionVocabularyRegistryImpl
import com.doublemoon1119.mahjongcraft.platform.minecraft.extension.BuiltInMinecraftMahjongExtension
import com.doublemoon1119.mahjongcraft.platform.minecraft.settlement.ExhaustiveDrawReasonDisplayNameRegistryImpl
import com.doublemoon1119.mahjongcraft.platform.minecraft.settlement.RoundOutcomeDisplayNameRegistryImpl
import com.doublemoon1119.mahjongcraft.platform.minecraft.settlement.RoundResultKindDto
import com.doublemoon1119.mahjongcraft.platform.minecraft.settlement.RoundResultPayloadDto
import com.doublemoon1119.mahjongcraft.platform.minecraft.settlement.RoundResultPlayerDto
import com.doublemoon1119.mahjongcraft.platform.minecraft.settlement.WinSettlementTextKeys
import com.doublemoon1119.mahjongcraft.platform.minecraft.text.MinecraftMessageKeys
import com.doublemoon1119.mahjongcraft.testing.logic.table.FakeMahjongPlayerFactory
import com.doublemoon1119.mahjongcraft.testing.logic.table.FakeTableStateFactory
import net.minecraft.text.HoverEvent
import net.minecraft.text.Text
import net.minecraft.text.TranslatableTextContent
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.uuid.Uuid

/**
 * [buildRoundResultChatMessage]／[buildMatchResultChatMessage] 的單元測試；只涵蓋不需要真正 Minecraft client 執行環境的分支，
 * 真人玩家名稱解析需要 `MinecraftClient.getInstance()`，留給實機驗證。
 */
class GameEventChatNotifierTest {

    private val actionVocabularyRegistry = GameActionVocabularyRegistryImpl().apply { BuiltInMinecraftMahjongExtension.registerGameActionVocabulary(this) }
    private val roundOutcomeDisplayNameRegistry = RoundOutcomeDisplayNameRegistryImpl()
    private val exhaustiveDrawReasonDisplayNameRegistry = ExhaustiveDrawReasonDisplayNameRegistryImpl()

    /** 對局結束排名使用的規則模組；沒有覆寫排名比較器，預設實作與規則無關。 */
    private val module = RiichiRuleModule(id = "riichi", config = RiichiRuleConfig())

    /** 每位玩家一行，前後分數與名次原樣使用伺服器的值，依結算後名次排列，分數沒變的玩家也列出。 */
    @Test
    fun `round result lists every player with the server's scores and ranks`() {
        val winner = player(seatIndex = 0, previousScore = 25_000, currentScore = 43_000, previousRank = 1, currentRank = 1)
        val unchanged = player(seatIndex = 1, previousScore = 25_000, currentScore = 25_000, previousRank = 2, currentRank = 2)
        val payerA = player(seatIndex = 2, previousScore = 25_000, currentScore = 19_000, previousRank = 3, currentRank = 4)
        val payerB = player(seatIndex = 3, previousScore = 25_000, currentScore = 19_000, previousRank = 4, currentRank = 3)

        val message = buildMessage(result(BuiltInRoundOutcomeIds.TSUMO, players = listOf(winner, unchanged, payerA, payerB)))

        val lines = message.playerLines()
        assertEquals(listOf(winner, unchanged, payerB, payerA).map { it.playerId.take(4) }, lines.map { it.args[0].toString() })
        assertEquals(listOf("1", "1", "→", "25000", "43000"), lines[0].args.drop(1).map { it.toString() })
        assertEquals(listOf("2", "2", "→", "25000", "25000"), lines[1].args.drop(1).map { it.toString() })
        assertEquals(listOf("4", "3", "↑", "25000", "19000"), lines[2].args.drop(1).map { it.toString() })
        assertEquals(listOf("3", "4", "↓", "25000", "19000"), lines[3].args.drop(1).map { it.toString() })
    }

    /** 本局結束時以本局結束為標題；和牌後本局繼續時改以和牌為標題。 */
    @Test
    fun `a continuing win is titled as a win instead of a round end`() {
        assertEquals(MinecraftMessageKeys.ROUND_RESULT_BROADCAST, buildMessage(result(BuiltInRoundOutcomeIds.TSUMO)).titleKey())
        assertEquals(
            MinecraftMessageKeys.CONTINUING_WIN_RESULT_BROADCAST,
            buildMessage(result(BuiltInRoundOutcomeIds.TSUMO, roundContinues = true)).titleKey(),
        )
    }

    /** 自摸使用規則的動作用語，榮和使用結算用語。 */
    @Test
    fun `tsumo and ron use the rule's wording`() {
        val tsumoKey = requireNotNull(actionVocabularyRegistry.find(RULE_MODULE_ID, BuiltInGameActionIds.TSUMO)).messageKey

        assertEquals(tsumoKey, buildMessage(result(BuiltInRoundOutcomeIds.TSUMO)).outcomeKey())
        assertEquals(WinSettlementTextKeys.RON, buildMessage(result(BuiltInRoundOutcomeIds.RON)).outcomeKey())
    }

    /** 其他結果依序查特殊結果與流局原因名稱，都沒有登記時流局使用通用流局文字、和牌顯示結果 ID。 */
    @Test
    fun `other outcomes use registered names before falling back by kind`() {
        roundOutcomeDisplayNameRegistry.register(SPECIAL_OUTCOME_ID, "test.special_outcome")
        exhaustiveDrawReasonDisplayNameRegistry.register(DRAW_REASON_ID, "test.draw_reason")

        assertEquals("test.special_outcome", buildMessage(result(SPECIAL_OUTCOME_ID)).outcomeKey())
        assertEquals("test.draw_reason", buildMessage(result(DRAW_REASON_ID, kind = RoundResultKindDto.DRAW)).outcomeKey())
        assertEquals(
            MinecraftMessageKeys.GAME_ACTION_EXHAUSTIVE_DRAW,
            buildMessage(result(UNKNOWN_OUTCOME_ID, kind = RoundResultKindDto.DRAW)).outcomeKey(),
        )
        assertEquals(UNKNOWN_OUTCOME_ID, buildMessage(result(UNKNOWN_OUTCOME_ID)).outcomeArgument().string)
    }

    /** 以測試的名稱解析組出結算訊息，名稱為玩家 ID 的前四碼。 */
    private fun buildMessage(result: RoundResultPayloadDto): Text = buildRoundResultChatMessage(
        result = result,
        actionVocabularyRegistry = actionVocabularyRegistry,
        roundOutcomeDisplayNameRegistry = roundOutcomeDisplayNameRegistry,
        exhaustiveDrawReasonDisplayNameRegistry = exhaustiveDrawReasonDisplayNameRegistry,
        playerDisplayName = { id, _ -> id.toString().take(4) },
    )

    /** 建立一筆結算結果；沒指定玩家時為兩名分數不變的 AI。 */
    private fun result(
        outcomeId: String,
        kind: RoundResultKindDto = RoundResultKindDto.WIN,
        roundContinues: Boolean = false,
        players: List<RoundResultPlayerDto> = listOf(
            player(seatIndex = 0, previousScore = 25_000, currentScore = 25_000, previousRank = 1, currentRank = 1),
            player(seatIndex = 1, previousScore = 25_000, currentScore = 25_000, previousRank = 2, currentRank = 2),
        ),
    ) = RoundResultPayloadDto(
        gameId = Uuid.random().toString(),
        ruleModuleId = RULE_MODULE_ID,
        outcomeId = outcomeId,
        kind = kind,
        roundContinues = roundContinues,
        players = players,
    )

    /** 建立一名 AI 玩家的結算前後值。 */
    private fun player(
        seatIndex: Int,
        previousScore: Int,
        currentScore: Int,
        previousRank: Int,
        currentRank: Int,
    ) = RoundResultPlayerDto(
        playerId = Uuid.random().toString(),
        seatIndex = seatIndex,
        isAi = true,
        previousScore = previousScore,
        currentScore = currentScore,
        previousRank = previousRank,
        currentRank = currentRank,
    )

    /** 訊息標題的翻譯鍵。 */
    private fun Text.titleKey(): String? = (content as? TranslatableTextContent)?.key

    /** 標題中結算結果文字的參數。 */
    private fun Text.outcomeArgument(): Text = (content as TranslatableTextContent).args[0] as Text

    /** 標題中結算結果文字的翻譯鍵。 */
    private fun Text.outcomeKey(): String? = (outcomeArgument().content as? TranslatableTextContent)?.key

    /** hover 詳情中每位玩家的一行。 */
    private fun Text.playerLines(): List<TranslatableTextContent> = hoverDetails()?.siblings
        .orEmpty()
        .mapNotNull { it.content as? TranslatableTextContent }
        .filter { it.key == MinecraftMessageKeys.ROUND_RESULT_PLAYER_LINE }

    /** 取得簡短 round-result 訊息的 hover 詳情。 */
    private fun Text.hoverDetails(): Text? = style.hoverEvent?.getValue(HoverEvent.Action.SHOW_TEXT)
        ?: siblings.firstNotNullOfOrNull { it.hoverDetails() }

    @Test
    fun `returns null for match result when the action is not match ended`() {
        val snapshot = fakeSnapshot(scores = listOf(25000, 25000))

        assertNull(buildMatchResultChatMessage(GameAction.Tsumo, snapshot, snapshot.allPlayerIds(), module))
    }

    /** 保存尚未完成時也一次建立提示與可點擊入口，不修改原始排行。 */
    @Test
    fun `test match result contains a stable history click and hint immediately`() {
        val command = "/mahjongcraft_client history chat_entry local-token"
        val snapshot = fakeSnapshot(listOf(30000, 20000))
        val message = requireNotNull(
            buildMatchResultChatMessage(GameAction.MatchEnded, snapshot, snapshot.allPlayerIds(), module, historyCommand = command),
        )
        val label = message.siblings.last()
        assertEquals(command, label.style.clickEvent?.value)
        val details = requireNotNull(label.hoverDetails())
        val hintCount = details.descendants().count { (it.content as? TranslatableTextContent)?.key == "mahjongcraft.history_screen.open_hint" }
        assertEquals(1, hintCount)
        assertEquals(2, details.siblings.count { (it.content as? TranslatableTextContent)?.key == "mahjongcraft.message.ranking_line" })
    }

    /**
     * 遞迴列出文字與巢狀說明，不依賴 Minecraft 的實際語系載入。
     *
     * @return 包含目前文字的所有後代節點。
     */
    private fun Text.descendants(): Sequence<Text> = sequence {
        yield(this@descendants)
        siblings.forEach { yieldAll(it.descendants()) }
    }

    @Test
    fun `breaks tied final scores by seat proximity to the original dealer`() {
        val eastId = Uuid.random()
        val southId = Uuid.random()
        val westId = Uuid.random()
        val northId = Uuid.random()
        // 起家第二位跟第三位同分（25000），照固定起家順位第二位名次要在第三位前面；
        // 同時刻意把兩人的本局風位反過來，確認終局同分判準不受 seatWind 影響。
        val snapshot = FakeTableStateFactory.create(
            players = listOf(
                FakeMahjongPlayerFactory.create(id = eastId, initialSeat = Wind.EAST).copy(score = 30000),
                FakeMahjongPlayerFactory.create(id = southId, initialSeat = Wind.SOUTH)
                    .copy(score = 25000, seatWind = Wind.NORTH),
                FakeMahjongPlayerFactory.create(id = northId, initialSeat = Wind.NORTH)
                    .copy(score = 25000, seatWind = Wind.SOUTH),
                FakeMahjongPlayerFactory.create(id = westId, initialSeat = Wind.WEST).copy(score = 20000),
            ),
        ).toSnapshot(visibleHandPlayerIds = emptySet(), setAsideTiles = { emptyList() })

        val message = buildMatchResultChatMessage(
            action = GameAction.MatchEnded,
            newSnapshot = snapshot,
            aiPlayerIds = snapshot.allPlayerIds(),
            module = module,
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

    /** 測試把所有玩家都當成 AI，避免觸發需要真正 client 執行環境的名稱解析分支。 */
    private fun TableStateSnapshot.allPlayerIds(): Set<Uuid> = players.map { it.id }.toSet()

    private fun fakeSnapshot(
        scores: List<Int>,
        ids: List<Uuid> = scores.map { Uuid.random() },
    ) = FakeTableStateFactory.create(
        players = ids.zip(scores).map { (id, score) ->
            FakeMahjongPlayerFactory.create(id = id).copy(score = score)
        },
    ).toSnapshot(visibleHandPlayerIds = emptySet(), setAsideTiles = { emptyList() })

    private companion object {
        /** 測試結算使用的規則模組 ID。 */
        const val RULE_MODULE_ID = "mahjongcraft:riichi"

        /** 測試登記的特殊結果 ID。 */
        const val SPECIAL_OUTCOME_ID = "test:special_outcome"

        /** 測試登記的流局原因 ID。 */
        const val DRAW_REASON_ID = "test:draw_reason"

        /** 沒有登記任何名稱的結果 ID。 */
        const val UNKNOWN_OUTCOME_ID = "test:unknown_outcome"
    }
}
