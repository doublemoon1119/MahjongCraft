package com.doublemoon1119.mahjongcraft.platform.fabric.client.history

import com.doublemoon1119.mahjongcraft.flow.network.dto.message.HistoryReplayFactDto
import com.doublemoon1119.mahjongcraft.flow.network.dto.message.HistoryReplayIdentityDto
import com.doublemoon1119.mahjongcraft.flow.network.dto.message.HistoryReplayPlayerIdentityDto
import com.doublemoon1119.mahjongcraft.flow.network.dto.message.HistoryReplayTransactionDto
import com.doublemoon1119.mahjongcraft.flow.network.dto.message.HistoryRoundEventsDto
import com.doublemoon1119.mahjongcraft.flow.network.dto.message.HistoryRoundOutcomeDto
import com.doublemoon1119.mahjongcraft.flow.network.dto.model.SuitDto
import com.doublemoon1119.mahjongcraft.flow.network.dto.model.TileDto
import com.doublemoon1119.mahjongcraft.logic.rules.riichi.RiichiExhaustiveDrawReason
import com.doublemoon1119.mahjongcraft.logic.table.BuiltInMatchEndReasonIds
import com.doublemoon1119.mahjongcraft.logic.table.RoundCompletionClassification
import com.doublemoon1119.mahjongcraft.platform.minecraft.action.BuiltInGameActionIds
import com.doublemoon1119.mahjongcraft.platform.minecraft.action.GameActionVocabulary
import com.doublemoon1119.mahjongcraft.platform.minecraft.action.GameActionVocabularyRegistryImpl
import com.doublemoon1119.mahjongcraft.platform.minecraft.history.MinecraftHistoryScreenKeys
import com.doublemoon1119.mahjongcraft.platform.minecraft.settlement.ExhaustiveDrawReasonDisplayNameRegistryImpl
import com.doublemoon1119.mahjongcraft.platform.minecraft.text.MinecraftMessageKeys
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import net.minecraft.text.Text
import net.minecraft.text.TranslatableTextContent
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** 驗證歷史事件呈現模型保留事件順序、牌面重複與結算分數。 */
class HistoryRoundEventPresentationTest {
    /** 四語格式參數必須符合呈現呼叫，避免遺漏時間、頁碼或出現未替換的佔位字元。 */
    @Test
    fun `round translations use matching argument counts`() {
        val counts = mapOf(
            MinecraftHistoryScreenKeys.ROUND_TIMING to 3,
            MinecraftHistoryScreenKeys.ROUND_PAGE_RANGE to 3,
            MinecraftHistoryScreenKeys.ROUND_REACTION to 1,
            MinecraftHistoryScreenKeys.ROUND_SCORE to 2,
            MinecraftHistoryScreenKeys.ROUND_COMPLETION to 2,
            MinecraftHistoryScreenKeys.ROUND_PREPARATION to 2,
            MinecraftHistoryScreenKeys.ROUND_MATCH_COMPLETION to 1,
            MinecraftHistoryScreenKeys.ROUND_WIN_SETTLEMENT to 1,
            MinecraftHistoryScreenKeys.ROUND_UNKNOWN_FACT to 0,
            MinecraftHistoryScreenKeys.ROUND_PREPARATION_NEXT to 0,
            MinecraftHistoryScreenKeys.ROUND_PREPARATION_STEP to 1,
        )
        listOf("en_us", "zh_tw", "zh_cn", "ja_jp").forEach { language ->
            val resource = checkNotNull(javaClass.getResourceAsStream("/assets/mahjongcraft/lang/$language.json"))
            val entries = resource.bufferedReader().use { Json.parseToJsonElement(it.readText()).jsonObject }
            counts.forEach { (key, expected) ->
                val value = checkNotNull(entries[key]).jsonPrimitive.content
                assertEquals(expected, Regex("%s").findAll(value).count(), "$language argument count for $key")
            }
        }
    }

    /** 內建動作應經 registry 解析，且直接牌與公開牌應保留順序及重複。 */
    @Test
    fun `known actions resolve wording and preserve tile references`() {
        val presenter = presenter()
        val tile = TileDto.Numeric(SuitDto.CHARACTER, 5)
        val events = events(
            HistoryReplayFactDto.KnownAction(
                typeKey = "action_accepted",
                actorSeat = 2,
                actionType = "discard",
                directTiles = listOf(0, 0),
                revealedTiles = listOf(1),
                extensionTypeId = null,
            ),
            tileCatalog = listOf(tile, TileDto.Honor.Red),
        )

        val fact = presenter.present(events, "example:rule").transactions.single().facts.single()

        assertEquals("test.discard", fact.text.string)
        assertEquals(2, fact.actorSeat)
        assertEquals(listOf(tile, tile), fact.directTiles)
        assertEquals(listOf(TileDto.Honor.Red), fact.revealedTiles)
    }

    /** 未知動作使用通用文字，但識別碼保留供 tooltip 查閱。 */
    @Test
    fun `unknown actions preserve their identifier`() {
        val fact = presenter().present(
            events(HistoryReplayFactDto.KnownAction("action", null, "custom:spell", emptyList(), emptyList(), null)),
            null,
        ).transactions.single().facts.single()

        assertEquals(MinecraftHistoryScreenKeys.ROUND_ACTION_OTHER, fact.text.string)
        assertTrue("custom:spell" in fact.identifiers)
    }

    /** 各種非動作事實應使用相異分類文字並保留原始識別碼。 */
    @Test
    fun `non action facts retain their category and identifiers`() {
        val presenter = presenter()
        val facts = listOf(
            HistoryReplayFactDto.Reaction("reaction_resolved", "custom:reaction", 0),
            HistoryReplayFactDto.Preparation("round_preparation_started", "custom:step", 1, null),
            HistoryReplayFactDto.RuleEffect("rule_effect_resolved", "custom:effect", null),
            HistoryReplayFactDto.Opaque("custom:opaque", 0, emptyList(), emptyList()),
        )
        val presentation = presenter.present(
            events(facts = facts),
            null,
        )

        assertEquals(4, presentation.transactions.single().facts.size)
        assertTrue(presentation.transactions.single().facts.all { it.outcome == null })
        val result = presentation.transactions.single().facts
        assertEquals(listOf(MinecraftHistoryScreenKeys.ROUND_REACTION, MinecraftHistoryScreenKeys.ROUND_PREPARATION, MinecraftHistoryScreenKeys.ROUND_RULE_EFFECT, MinecraftHistoryScreenKeys.ROUND_UNKNOWN_FACT), result.map { (it.text.content as TranslatableTextContent).key })
        listOf("custom:reaction", "custom:step", "custom:effect", "custom:opaque").forEachIndexed { index, id ->
            assertTrue(id in result[index].identifiers, "Raw identifier must remain available in tooltip")
            assertTrue(!result[index].text.string.contains(id), "Raw identifier must not leak into player wording")
        }
    }

    /** 內建流程事件即使經由 opaque DTO 傳送，也不能誤標為未知事件。 */
    @Test
    fun `built in opaque events have player wording`() {
        val mappings = mapOf("win_continuation_resolved" to MinecraftHistoryScreenKeys.ROUND_EVENT_WIN_CONTINUATION, "returned_to_room" to MinecraftHistoryScreenKeys.ROUND_EVENT_RETURNED_TO_ROOM, "match_started" to MinecraftHistoryScreenKeys.ROUND_EVENT_MATCH_STARTED, "round_started" to MinecraftHistoryScreenKeys.ROUND_EVENT_ROUND_STARTED)
        mappings.forEach { (id, key) ->
            val fact = presenter().present(events(HistoryReplayFactDto.Opaque(id, null, emptyList(), emptyList())), null).transactions.single().facts.single()
            assertEquals(key, fact.text.string)
            assertEquals(listOf(id), fact.identifiers)
        }
    }

    /** 系統操作與流局均使用玩家用語，不顯示保存格式名稱。 */
    @Test
    fun `system actions have localized wording`() {
        val mappings = mapOf("game_started" to MinecraftHistoryScreenKeys.ROUND_ACTION_GAME_STARTED, "round_started" to MinecraftHistoryScreenKeys.ROUND_ACTION_ROUND_STARTED, "match_ended" to MinecraftHistoryScreenKeys.ROUND_ACTION_MATCH_ENDED, "dice_rolled" to MinecraftHistoryScreenKeys.ROUND_ACTION_DICE_ROLLED, "draw" to MinecraftHistoryScreenKeys.ROUND_ACTION_DRAW, "kan" to MinecraftHistoryScreenKeys.ROUND_ACTION_KAN, "exhaustive_draw" to MinecraftHistoryScreenKeys.ROUND_CLASSIFICATION_EXHAUSTIVE_DRAW)
        mappings.forEach { (id, key) ->
            val fact = presenter().present(events(HistoryReplayFactDto.KnownAction("action_accepted", null, id, emptyList(), emptyList(), null)), null).transactions.single().facts.single()
            assertEquals(key, fact.text.string)
            assertTrue(id in fact.identifiers)
        }
    }

    /** 沒有鳴牌裁定結果時使用中立說明，不猜測玩家是否跳過或逾時。 */
    @Test
    fun `empty reaction uses neutral wording`() {
        val fact = presenter().present(events(HistoryReplayFactDto.Reaction("reaction_resolved", null, null)), null).transactions.single().facts.single()
        val argument = (fact.text.content as TranslatableTextContent).args.single() as Text
        assertEquals(MinecraftHistoryScreenKeys.ROUND_REACTION_NONE, argument.string)
    }

    /** 整場結束使用獨立標題，所有內建終止原因均能本地化。 */
    @Test
    fun `match completion localizes built in end reasons`() {
        val reasons = mapOf(BuiltInMatchEndReasonIds.SCHEDULE_COMPLETED to MinecraftHistoryScreenKeys.ROUND_OUTCOME_SCHEDULE_COMPLETED, BuiltInMatchEndReasonIds.TARGET_SCORE_REACHED to MinecraftHistoryScreenKeys.ROUND_OUTCOME_TARGET_SCORE_REACHED, BuiltInMatchEndReasonIds.EXTRA_ROUND_LIMIT_REACHED to MinecraftHistoryScreenKeys.ROUND_OUTCOME_EXTRA_ROUND_LIMIT_REACHED, BuiltInMatchEndReasonIds.DEALER_TOP_FINISH to MinecraftHistoryScreenKeys.ROUND_OUTCOME_DEALER_TOP_FINISH, BuiltInMatchEndReasonIds.PLAYER_BUSTED to MinecraftHistoryScreenKeys.ROUND_OUTCOME_PLAYER_BUSTED)
        reasons.forEach { (reason, key) ->
            val outcome = HistoryRoundOutcomeDto(reason, emptyList(), emptyMap(), null, emptyList(), null)
            val fact = presenter().present(events(HistoryReplayFactDto.Completion("match_completed", outcome)), null).transactions.single().facts.single()
            assertEquals(MinecraftHistoryScreenKeys.ROUND_MATCH_COMPLETION, (fact.text.content as TranslatableTextContent).key)
            assertEquals(key, checkNotNull(fact.outcome).reasonText.string)
            assertTrue(reason in fact.identifiers)
        }
    }

    /** 開始或提交準備時，沒有下一步 ID 不代表準備已完成。 */
    @Test
    fun `preparation wording respects the recorded stage`() {
        val stages = mapOf("round_preparation_started" to MinecraftHistoryScreenKeys.ROUND_PREPARATION_STARTED, "round_preparation_submitted" to MinecraftHistoryScreenKeys.ROUND_PREPARATION_SUBMITTED, "round_preparation_automatic_resolved" to MinecraftHistoryScreenKeys.ROUND_PREPARATION_NONE)
        stages.forEach { (type, key) ->
            val fact = presenter().present(events(HistoryReplayFactDto.Preparation(type, "custom:step", 0, null)), null).transactions.single().facts.single()
            val stage = (fact.text.content as TranslatableTextContent).args[1] as Text
            assertEquals(key, stage.string)
            assertTrue("custom:step" in fact.identifiers)
        }
        val fact = presenter().present(events(HistoryReplayFactDto.Preparation("round_preparation_automatic_resolved", "custom:step", 0, "custom:next")), null).transactions.single().facts.single()
        assertEquals(MinecraftHistoryScreenKeys.ROUND_PREPARATION_NEXT, ((fact.text.content as TranslatableTextContent).args[1] as Text).string)
        assertTrue("custom:next" in fact.identifiers)
    }

    /** 保存的內建分類使用本地化標籤，未知分類使用安全通用文字。 */
    @Test
    fun `outcome classifications use player wording`() {
        val classifications = mapOf(
            RoundCompletionClassification.WIN to MinecraftHistoryScreenKeys.ROUND_CLASSIFICATION_WIN,
            RoundCompletionClassification.EXHAUSTIVE_DRAW to MinecraftHistoryScreenKeys.ROUND_CLASSIFICATION_EXHAUSTIVE_DRAW,
            RoundCompletionClassification.ABORTIVE_DRAW to MinecraftHistoryScreenKeys.ROUND_CLASSIFICATION_ABORTIVE_DRAW,
            RoundCompletionClassification.EXTENSION to MinecraftHistoryScreenKeys.ROUND_CLASSIFICATION_EXTENSION,
        )
        classifications.forEach { (classification, key) ->
            assertEquals(key, presenter().classificationText(classification.name).string)
        }
        assertEquals(MinecraftHistoryScreenKeys.ROUND_CLASSIFICATION_OTHER, presenter().classificationText("custom:unknown").string)
    }

    /** 結算事實應保留每個座位的絕對分數與角色標記。 */
    @Test
    fun `outcome rows preserve scores and participant roles`() {
        val outcome = HistoryRoundOutcomeDto(
            reasonId = "custom:win",
            beneficiarySeats = listOf(1),
            scoresBySeat = linkedMapOf(0 to 24000, 1 to 36000),
            classification = "WIN",
            responsibleSeats = listOf(0),
            transitionDirective = null,
        )
        val fact = presenter().present(
            events(HistoryReplayFactDto.Completion("round_completed", outcome)),
            null,
        ).transactions.single().facts.single()

        val rows = checkNotNull(fact.outcome).rows
        assertEquals(listOf(0, 1), rows.map { it.seatIndex })
        assertEquals(listOf(24000, 36000), rows.map { it.score })
        assertTrue(rows[0].responsible)
        assertTrue(rows[1].beneficiary)
    }

    /** 結算存在但缺少分數時，仍保留玩家角色且不把未知分數補為零。 */
    @Test
    fun `missing outcome scores remain unknown`() {
        val outcome = HistoryRoundOutcomeDto("custom:draw", listOf(1), emptyMap(), null, emptyList(), null)
        val fact = presenter().present(events(HistoryReplayFactDto.Completion("round_completed", outcome)), null)
            .transactions.single().facts.single()
        val rows = checkNotNull(fact.outcome).rows
        assertEquals(listOf(0, 1), rows.map { it.seatIndex })
        assertTrue(rows.all { it.score == null })
        assertTrue(rows[1].beneficiary)
    }

    /** 擴充動作直接使用規則註冊的識別碼，不新增地區規則分支。 */
    @Test
    fun `extension action resolves registered vocabulary`() {
        val vocabulary = GameActionVocabularyRegistryImpl().apply {
            registerDefault("custom:spell", GameActionVocabulary("test.spell"))
        }
        val presenter = HistoryRoundEventPresenter(vocabulary, ExhaustiveDrawReasonDisplayNameRegistryImpl())
        val fact = presenter.present(
            events(HistoryReplayFactDto.KnownAction("action", 0, "extension", emptyList(), emptyList(), "custom:spell")),
            "custom:rule",
        ).transactions.single().facts.single()
        assertEquals("test.spell", fact.text.string)
    }

    /** 建立測試所需的事件 presenter。
     * @return 已註冊測試用動作詞彙的 presenter。
     */
    private fun presenter(): HistoryRoundEventPresenter {
        val vocabulary = GameActionVocabularyRegistryImpl().apply {
            registerDefault(BuiltInGameActionIds.DISCARD, GameActionVocabulary("test.discard"))
        }
        val reasons = ExhaustiveDrawReasonDisplayNameRegistryImpl()
        return HistoryRoundEventPresenter(vocabulary, reasons)
    }

    /** 一般流局使用聽牌與未聽牌身分；擴充結算保留原有受益身分而不假設規則。 */
    @Test
    fun `normal draw status matches settlement panel without changing extension labels`() {
        val presenter = presenter()
        val outcome = HistoryOutcomePresentation(
            reasonId = RiichiExhaustiveDrawReason.Normal.id,
            reasonText = Text.literal("draw"),
            classification = RoundCompletionClassification.EXHAUSTIVE_DRAW.name,
            transitionDirective = null,
            rows = emptyList(),
        )
        val tenpai = HistoryOutcomeRowPresentation(0, 28000, true, false, 3000)
        val noten = HistoryOutcomeRowPresentation(1, 24000, false, false, -1000)
        assertEquals(MinecraftMessageKeys.EXHAUSTIVE_DRAW_SETTLEMENT_STATUS_TENPAI, presenter.settlementStatusText(outcome, tenpai)?.string)
        assertEquals(MinecraftMessageKeys.EXHAUSTIVE_DRAW_SETTLEMENT_STATUS_NOTEN, presenter.settlementStatusText(outcome, noten)?.string)
        assertEquals(MinecraftHistoryScreenKeys.ROUND_BENEFICIARY, presenter.settlementStatusText(outcome.copy(reasonId = "custom:draw"), tenpai)?.string)
        assertEquals(null, presenter.settlementStatusText(outcome.copy(reasonId = "custom:draw"), noten))
        assertEquals("+3000", presenter.scoreChangeText(3000).string)
        assertEquals("−1000", presenter.scoreChangeText(-1000).string)
        assertEquals("±0", presenter.scoreChangeText(0).string)
    }

    /** 建立含單一交易的測試事件頁。
     * @param fact 單一事實便捷輸入。
     * @param facts 依序保存的事實。
     * @param tileCatalog 事實參照的牌目錄。
     * @return 單一交易事件頁。
     */
    private fun events(
        fact: HistoryReplayFactDto? = null,
        facts: List<HistoryReplayFactDto> = listOfNotNull(fact),
        tileCatalog: List<TileDto> = emptyList(),
    ): HistoryRoundEventsDto = HistoryRoundEventsDto(
        identity = HistoryReplayIdentityDto(
            matchId = "match",
            tableId = "table",
            players = listOf(HistoryReplayPlayerIdentityDto(0, null, null)),
        ),
        roundNumber = 1,
        transactions = listOf(HistoryReplayTransactionDto(0, 100L, false, tileCatalog.size, facts)),
        nextTransactionIndex = null,
        tileCatalog = tileCatalog,
    )
}
