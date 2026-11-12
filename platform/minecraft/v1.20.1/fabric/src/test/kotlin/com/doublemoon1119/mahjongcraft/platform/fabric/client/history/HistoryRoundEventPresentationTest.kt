package com.doublemoon1119.mahjongcraft.platform.fabric.client.history

import com.doublemoon1119.mahjongcraft.flow.common.game.history.HistoryFactTypeKeys
import com.doublemoon1119.mahjongcraft.flow.common.game.history.replay.HistoryActionTypeKeys
import com.doublemoon1119.mahjongcraft.flow.network.dto.message.HistoryReplayFactDto
import com.doublemoon1119.mahjongcraft.flow.network.dto.message.HistoryReplayIdentityDto
import com.doublemoon1119.mahjongcraft.flow.network.dto.message.HistoryReplayPlayerIdentityDto
import com.doublemoon1119.mahjongcraft.flow.network.dto.message.HistoryReplayTransactionDto
import com.doublemoon1119.mahjongcraft.flow.network.dto.message.HistoryRoundEventsDto
import com.doublemoon1119.mahjongcraft.flow.network.dto.message.HistoryRoundOutcomeDto
import com.doublemoon1119.mahjongcraft.flow.network.dto.model.RuleDecisionTimingDto
import com.doublemoon1119.mahjongcraft.flow.network.dto.model.SuitDto
import com.doublemoon1119.mahjongcraft.flow.network.dto.model.TileDto
import com.doublemoon1119.mahjongcraft.flow.network.dto.model.TileFaceViewDto
import com.doublemoon1119.mahjongcraft.logic.rules.riichi.RiichiExhaustiveDrawReason
import com.doublemoon1119.mahjongcraft.logic.table.BuiltInMatchEndReasonIds
import com.doublemoon1119.mahjongcraft.logic.table.RoundCompletionClassification
import com.doublemoon1119.mahjongcraft.platform.minecraft.action.BuiltInGameActionIds
import com.doublemoon1119.mahjongcraft.platform.minecraft.action.GameActionVocabulary
import com.doublemoon1119.mahjongcraft.platform.minecraft.action.GameActionVocabularyDefaultDefinition
import com.doublemoon1119.mahjongcraft.platform.minecraft.action.GameActionVocabularyRegistryImpl
import com.doublemoon1119.mahjongcraft.platform.minecraft.action.MinecraftKanActionTokenKeys
import com.doublemoon1119.mahjongcraft.platform.minecraft.decision.RuleDecisionDisplayNameRegistryImpl
import com.doublemoon1119.mahjongcraft.platform.minecraft.history.HistoryTileReplacementDisplayDefinition
import com.doublemoon1119.mahjongcraft.platform.minecraft.history.HistoryTileSetAsideDisplayRegistryImpl
import com.doublemoon1119.mahjongcraft.platform.minecraft.history.MinecraftHistoryScreenKeys
import com.doublemoon1119.mahjongcraft.platform.minecraft.rule.riichi.RiichiTranslationKeys
import com.doublemoon1119.mahjongcraft.platform.minecraft.settlement.ExhaustiveDrawReasonDisplayNameDefinition
import com.doublemoon1119.mahjongcraft.platform.minecraft.settlement.ExhaustiveDrawReasonDisplayNameRegistryImpl
import com.doublemoon1119.mahjongcraft.platform.minecraft.settlement.ExhaustiveDrawSettlementStatusLabels
import com.doublemoon1119.mahjongcraft.platform.minecraft.settlement.ExhaustiveDrawSettlementStatusLabelsDefinition
import com.doublemoon1119.mahjongcraft.platform.minecraft.settlement.RoundOutcomeDisplayNameDefinition
import com.doublemoon1119.mahjongcraft.platform.minecraft.settlement.RoundOutcomeDisplayNameRegistryImpl
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
            MinecraftHistoryScreenKeys.ROUND_ACTION_SCORED to 1,
            MinecraftHistoryScreenKeys.ROUND_ACTION_SCORED_POT_DEPOSIT to 2,
            MinecraftHistoryScreenKeys.ROUND_ACTION_SCORED_POT_WITHDRAWAL to 2,
            MinecraftHistoryScreenKeys.ROUND_SCORE to 2,
            MinecraftHistoryScreenKeys.ROUND_COMPLETION to 2,
            MinecraftHistoryScreenKeys.RULE_DECISION to 2,
            MinecraftHistoryScreenKeys.ROUND_MATCH_COMPLETION to 1,
            MinecraftHistoryScreenKeys.ROUND_MATCH_ABORTED to 1,
            MinecraftHistoryScreenKeys.ROUND_WIN_SETTLEMENT to 1,
            MinecraftHistoryScreenKeys.ROUND_EVENT_DICE_ROLLED to 2,
            MinecraftHistoryScreenKeys.ROUND_UNKNOWN_FACT to 0,
            MinecraftHistoryScreenKeys.RULE_DECISION_NEXT to 0,
            MinecraftHistoryScreenKeys.RULE_DECISION_STEP to 1,
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
        val tile = TileFaceViewDto(TileDto.Numeric(SuitDto.CHARACTER, 5), emptyList(), null)
        val events = events(
            HistoryReplayFactDto.KnownAction(
                typeKey = HistoryFactTypeKeys.ACTION_ACCEPTED,
                actorSeat = 2,
                actionType = HistoryActionTypeKeys.DISCARD,
                directTiles = listOf(0, 0),
                revealedTiles = listOf(1),
                extensionTypeId = null,
            ),
            tileCatalog = listOf(tile, TileFaceViewDto(TileDto.Honor.Red, emptyList(), null)),
        )

        val fact = presenter.present(events, "example:rule").transactions.single().facts.single()

        assertEquals("test.discard", fact.text.string)
        assertEquals(2, fact.actorSeat)
        assertEquals(listOf(tile, tile), fact.directTiles)
        assertEquals(listOf(TileFaceViewDto(TileDto.Honor.Red, emptyList(), null)), fact.revealedTiles)
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
            HistoryReplayFactDto.Reaction(HistoryFactTypeKeys.REACTION_RESOLVED, "custom:reaction", 0),
            HistoryReplayFactDto.RuleDecision(HistoryFactTypeKeys.RULE_DECISION_STARTED, RuleDecisionTimingDto.ROUND_START, "custom:step", 1, null, null, null, false),
            HistoryReplayFactDto.RuleEffect(HistoryFactTypeKeys.RULE_EFFECT_RESOLVED, "custom:effect", null),
            HistoryReplayFactDto.Opaque("custom:opaque", 0, emptyList(), emptyList()),
        )
        val presentation = presenter.present(
            events(facts = facts),
            null,
        )

        assertEquals(4, presentation.transactions.single().facts.size)
        assertTrue(presentation.transactions.single().facts.all { it.outcome == null })
        val result = presentation.transactions.single().facts
        assertEquals(listOf(MinecraftHistoryScreenKeys.ROUND_REACTION, MinecraftHistoryScreenKeys.RULE_DECISION, MinecraftHistoryScreenKeys.ROUND_RULE_EFFECT, MinecraftHistoryScreenKeys.ROUND_UNKNOWN_FACT), result.map { (it.text.content as TranslatableTextContent).key })
        listOf("custom:reaction", "custom:step", "custom:effect", "custom:opaque").forEachIndexed { index, id ->
            assertTrue(id in result[index].identifiers, "Raw identifier must remain available in tooltip")
            assertTrue(!result[index].text.string.contains(id), "Raw identifier must not leak into player wording")
        }
    }

    /** 動作即時收付依點數池方向選擇文字，並保留行為者與各座位的增減。 */
    @Test
    fun `action scored facts keep seats and describe the pot`() {
        val expectations = mapOf(
            0 to MinecraftHistoryScreenKeys.ROUND_ACTION_SCORED,
            1000 to MinecraftHistoryScreenKeys.ROUND_ACTION_SCORED_POT_DEPOSIT,
            -1000 to MinecraftHistoryScreenKeys.ROUND_ACTION_SCORED_POT_WITHDRAWAL,
        )
        expectations.forEach { (potDeposit, key) ->
            val fact = presenter().present(
                events(
                    HistoryReplayFactDto.ActionScored(
                        typeKey = HistoryFactTypeKeys.ACTION_SCORED,
                        actorSeat = 1,
                        actionType = "kan",
                        extensionTypeId = null,
                        scoreDeltasBySeat = mapOf(2 to -1000, 1 to 2000 - potDeposit, 0 to -1000),
                        potDeposit = potDeposit,
                    ),
                ),
                null,
            ).transactions.single().facts.single()

            assertEquals(key, (fact.text.content as TranslatableTextContent).key)
            assertEquals(1, fact.actorSeat)
            assertEquals(listOf(0, 1, 2), fact.scoreChanges.keys.toList())
            assertEquals(2000 - potDeposit, fact.scoreChanges.getValue(1))
            assertTrue("kan" in fact.identifiers)
        }
    }

    /** 擲骰事實使用一般動作文字，並保留實際擲骰者座位。 */
    @Test
    fun `dice roll facts use player wording and actor seat`() {
        val fact = presenter().present(
            events(
                HistoryReplayFactDto.DiceRolled(
                    typeKey = HistoryFactTypeKeys.DICE_ROLLED,
                    rollIndex = 1,
                    actorSeat = 2,
                    dice = listOf(2, 5),
                ),
            ),
            null,
        ).transactions.single().facts.single()

        assertEquals("2", (fact.text.content as TranslatableTextContent).args[0].toString())
        assertEquals("2 + 5", (fact.text.content as TranslatableTextContent).args[1].toString())
        assertEquals(2, fact.actorSeat)
        assertEquals(listOf(HistoryFactTypeKeys.DICE_ROLLED), fact.identifiers)
    }

    /** 內建流程事件即使經由 opaque DTO 傳送，也不能誤標為未知事件。 */
    @Test
    fun `built in opaque events have player wording`() {
        val mappings = mapOf(HistoryFactTypeKeys.WIN_CONTINUATION_RESOLVED to MinecraftHistoryScreenKeys.ROUND_EVENT_WIN_CONTINUATION, HistoryFactTypeKeys.RETURNED_TO_ROOM to MinecraftHistoryScreenKeys.ROUND_EVENT_RETURNED_TO_ROOM, HistoryFactTypeKeys.MATCH_STARTED to MinecraftHistoryScreenKeys.ROUND_EVENT_MATCH_STARTED, HistoryFactTypeKeys.ROUND_STARTED to MinecraftHistoryScreenKeys.ROUND_EVENT_ROUND_STARTED)
        mappings.forEach { (id, key) ->
            val fact = presenter().present(events(HistoryReplayFactDto.Opaque(id, null, emptyList(), emptyList())), null).transactions.single().facts.single()
            assertEquals(key, fact.text.string)
            assertEquals(listOf(id), fact.identifiers)
        }
    }

    /** 系統操作與流局均使用玩家用語，不顯示保存格式名稱。 */
    @Test
    fun `system actions have localized wording`() {
        val mappings = mapOf(HistoryActionTypeKeys.GAME_STARTED to MinecraftHistoryScreenKeys.ROUND_ACTION_GAME_STARTED, HistoryActionTypeKeys.ROUND_STARTED to MinecraftHistoryScreenKeys.ROUND_ACTION_ROUND_STARTED, HistoryActionTypeKeys.MATCH_ENDED to MinecraftHistoryScreenKeys.ROUND_ACTION_MATCH_ENDED, HistoryActionTypeKeys.DICE_ROLLED to MinecraftHistoryScreenKeys.ROUND_ACTION_DICE_ROLLED, HistoryActionTypeKeys.DRAW to MinecraftHistoryScreenKeys.ROUND_ACTION_DRAW, HistoryActionTypeKeys.KAN to MinecraftHistoryScreenKeys.ROUND_ACTION_KAN, HistoryActionTypeKeys.EXHAUSTIVE_DRAW to MinecraftHistoryScreenKeys.ROUND_CLASSIFICATION_EXHAUSTIVE_DRAW)
        mappings.forEach { (id, key) ->
            val fact = presenter().present(events(HistoryReplayFactDto.KnownAction(HistoryFactTypeKeys.ACTION_ACCEPTED, null, id, emptyList(), emptyList(), null)), null).transactions.single().facts.single()
            assertEquals(key, fact.text.string)
            assertTrue(id in fact.identifiers)
        }
    }

    /** 槓牌簡寫與 namespaced 動作 ID 都應解析至相同的槓牌用語。 */
    @Test
    fun `kan action aliases resolve to the registered vocabulary`() {
        val aliases = mapOf(
            MinecraftKanActionTokenKeys.OPEN to BuiltInGameActionIds.KAN_OPEN,
            MinecraftKanActionTokenKeys.CLOSED to BuiltInGameActionIds.KAN_CLOSED,
            MinecraftKanActionTokenKeys.ADDED to BuiltInGameActionIds.KAN_ADDED,
        )
        val vocabulary = GameActionVocabularyRegistryImpl().apply {
            aliases.values.forEach { actionId -> register(GameActionVocabularyDefaultDefinition(actionId, GameActionVocabulary("test.$actionId"))) }
        }
        val presenter = HistoryRoundEventPresenter(vocabulary, ExhaustiveDrawReasonDisplayNameRegistryImpl(), RoundOutcomeDisplayNameRegistryImpl(), RuleDecisionDisplayNameRegistryImpl(), HistoryTileSetAsideDisplayRegistryImpl())

        aliases.forEach { (alias, actionId) ->
            listOf(alias, actionId).forEach { actionType ->
                val fact = presenter.present(
                    events(HistoryReplayFactDto.KnownAction(HistoryFactTypeKeys.ACTION_ACCEPTED, null, actionType, emptyList(), emptyList(), null)),
                    null,
                ).transactions.single().facts.single()
                assertEquals("test.$actionId", fact.text.string)
            }
        }
    }

    /** 沒有鳴牌裁定結果時使用中立說明，不猜測玩家是否跳過或逾時。 */
    @Test
    fun `empty reaction uses neutral wording`() {
        val fact = presenter().present(events(HistoryReplayFactDto.Reaction(HistoryFactTypeKeys.REACTION_RESOLVED, null, null)), null).transactions.single().facts.single()
        val argument = (fact.text.content as TranslatableTextContent).args.single() as Text
        assertEquals(MinecraftHistoryScreenKeys.ROUND_REACTION_NONE, argument.string)
    }

    /** 整場結束使用獨立標題，所有內建終止原因均能本地化。 */
    @Test
    fun `match completion localizes built in end reasons`() {
        val reasons = mapOf(BuiltInMatchEndReasonIds.SCHEDULE_COMPLETED to MinecraftHistoryScreenKeys.ROUND_OUTCOME_SCHEDULE_COMPLETED, BuiltInMatchEndReasonIds.TARGET_SCORE_REACHED to MinecraftHistoryScreenKeys.ROUND_OUTCOME_TARGET_SCORE_REACHED, BuiltInMatchEndReasonIds.EXTRA_ROUND_LIMIT_REACHED to MinecraftHistoryScreenKeys.ROUND_OUTCOME_EXTRA_ROUND_LIMIT_REACHED, BuiltInMatchEndReasonIds.PLAYER_BUSTED to MinecraftHistoryScreenKeys.ROUND_OUTCOME_PLAYER_BUSTED)
        reasons.forEach { (reason, key) ->
            val outcome = HistoryRoundOutcomeDto(reason, emptyList(), emptyMap(), null, emptyList(), null, nextDealerSeat = null, scoreChangesBySeat = emptyMap(), winnerDetails = emptyList(), hasEarlierWinSettlement = false)
            val fact = presenter().present(events(HistoryReplayFactDto.Completion(HistoryFactTypeKeys.MATCH_COMPLETED, outcome)), null).transactions.single().facts.single()
            assertEquals(MinecraftHistoryScreenKeys.ROUND_MATCH_COMPLETION, (fact.text.content as TranslatableTextContent).key)
            assertEquals(key, checkNotNull(fact.outcome).reasonText.string)
            assertTrue(reason in fact.identifiers)
        }
    }

    /** 規則自訂的整場終止原因使用規則登記的特殊結果名稱。 */
    @Test
    fun `match completion uses registered rule end reasons`() {
        val outcome = HistoryRoundOutcomeDto("example:special", emptyList(), emptyMap(), null, emptyList(), null, nextDealerSeat = null, scoreChangesBySeat = emptyMap(), winnerDetails = emptyList(), hasEarlierWinSettlement = false)
        val fact = presenter().present(events(HistoryReplayFactDto.Completion(HistoryFactTypeKeys.MATCH_COMPLETED, outcome)), null).transactions.single().facts.single()

        assertEquals("test.special", checkNotNull(fact.outcome).reasonText.string)
    }

    /** 中途終止顯示已知平台原因，未知原因則回退為通用結算文字。 */
    @Test
    fun `match abortion localizes known and unknown reasons`() {
        val reasons = mapOf(
            "mahjongcraft:table_missing" to MinecraftHistoryScreenKeys.ROUND_ABORT_REASON_TABLE_MISSING,
            "mahjongcraft:table_broken_by_player" to MinecraftHistoryScreenKeys.ROUND_ABORT_REASON_TABLE_BROKEN_BY_PLAYER,
        )
        reasons.forEach { (reason, key) ->
            val outcome = HistoryRoundOutcomeDto(
                reason,
                emptyList(),
                emptyMap(),
                null,
                emptyList(),
                null,
                nextDealerSeat = null,
                scoreChangesBySeat = emptyMap(),
                winnerDetails = emptyList(),
                hasEarlierWinSettlement = false,
            )
            val fact = presenter().present(
                events(HistoryReplayFactDto.Completion(HistoryFactTypeKeys.MATCH_ABORTED, outcome)),
                null,
            ).transactions.single().facts.single()
            assertEquals(MinecraftHistoryScreenKeys.ROUND_MATCH_ABORTED, (fact.text.content as TranslatableTextContent).key)
            val reasonText = ((fact.text.content as TranslatableTextContent).args.single() as Text)
            assertEquals(key, (reasonText.content as TranslatableTextContent).key)
            assertEquals(null, fact.outcome)
            assertTrue(reason in fact.identifiers)
        }

        val unknown = HistoryRoundOutcomeDto(
            "custom:abort",
            emptyList(),
            emptyMap(),
            null,
            emptyList(),
            null,
            nextDealerSeat = null,
            scoreChangesBySeat = emptyMap(),
            winnerDetails = emptyList(),
            hasEarlierWinSettlement = false,
        )
        val fact = presenter().present(
            events(HistoryReplayFactDto.Completion(HistoryFactTypeKeys.MATCH_ABORTED, unknown)),
            null,
        ).transactions.single().facts.single()
        val reasonText = ((fact.text.content as TranslatableTextContent).args.single() as Text)
        assertEquals(MinecraftHistoryScreenKeys.ROUND_OUTCOME_OTHER, (reasonText.content as TranslatableTextContent).key)
        assertEquals(null, fact.outcome)
        assertTrue("custom:abort" in fact.identifiers)
    }

    /** 開始或提交規則選擇時，沒有下一步 ID 不代表規則選擇已完成。 */
    @Test
    fun `rule decision wording respects the recorded stage`() {
        val stages = mapOf(HistoryFactTypeKeys.RULE_DECISION_STARTED to MinecraftHistoryScreenKeys.RULE_DECISION_STARTED, HistoryFactTypeKeys.RULE_DECISION_SUBMITTED to MinecraftHistoryScreenKeys.RULE_DECISION_SUBMITTED, HistoryFactTypeKeys.RULE_DECISION_AUTOMATIC_RESOLVED to MinecraftHistoryScreenKeys.RULE_DECISION_NONE)
        stages.forEach { (type, key) ->
            val fact = presenter().present(events(HistoryReplayFactDto.RuleDecision(type, RuleDecisionTimingDto.ROUND_START, "custom:step", 0, null, null, null, false)), null).transactions.single().facts.single()
            val stage = (fact.text.content as TranslatableTextContent).args[1] as Text
            assertEquals(key, stage.string)
            assertTrue("custom:step" in fact.identifiers)
        }
        val fact = presenter().present(events(HistoryReplayFactDto.RuleDecision(HistoryFactTypeKeys.RULE_DECISION_AUTOMATIC_RESOLVED, RuleDecisionTimingDto.ROUND_START, "custom:step", 0, "custom:next", null, null, false)), null).transactions.single().facts.single()
        assertEquals(MinecraftHistoryScreenKeys.RULE_DECISION_NEXT, ((fact.text.content as TranslatableTextContent).args[1] as Text).string)
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
            nextDealerSeat = null,
            scoreChangesBySeat = emptyMap(),
            winnerDetails = emptyList(),
            hasEarlierWinSettlement = false,
        )
        val fact = presenter().present(
            events(HistoryReplayFactDto.Completion(HistoryFactTypeKeys.ROUND_COMPLETED, outcome)),
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
        val outcome = HistoryRoundOutcomeDto("custom:draw", listOf(1), emptyMap(), null, emptyList(), null, nextDealerSeat = null, scoreChangesBySeat = emptyMap(), winnerDetails = emptyList(), hasEarlierWinSettlement = false)
        val fact = presenter().present(events(HistoryReplayFactDto.Completion(HistoryFactTypeKeys.ROUND_COMPLETED, outcome)), null)
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
            register(GameActionVocabularyDefaultDefinition("custom:spell", GameActionVocabulary("test.spell")))
        }
        val presenter = HistoryRoundEventPresenter(vocabulary, ExhaustiveDrawReasonDisplayNameRegistryImpl(), RoundOutcomeDisplayNameRegistryImpl(), RuleDecisionDisplayNameRegistryImpl(), HistoryTileSetAsideDisplayRegistryImpl())
        val fact = presenter.present(
            events(HistoryReplayFactDto.KnownAction("action", 0, HistoryActionTypeKeys.EXTENSION, emptyList(), emptyList(), "custom:spell")),
            "custom:rule",
        ).transactions.single().facts.single()
        assertEquals("test.spell", fact.text.string)
    }

    /** 局首換牌依輪次列出標題，每位玩家一列移出的牌；標題使用規則登記的用語。 */
    @Test
    fun `opening replacement rounds list each player's tiles under the rule's wording`() {
        val presenter = replacementPresenter()
        val catalog = listOf(extensionFace("test:autumn"), extensionFace("test:plum"), extensionFace("test:bamboo"))
        val fact = HistoryReplayFactDto.TileReplacementRound(
            typeKey = HistoryFactTypeKeys.TILE_REPLACEMENT_ROUND_RESOLVED,
            timing = RuleDecisionTimingDto.ROUND_START,
            round = 1,
            removals = listOf(
                HistoryReplayFactDto.TileReplacementRound.Removal(seat = 0, tiles = listOf(0, 1)),
                HistoryReplayFactDto.TileReplacementRound.Removal(seat = 1, tiles = listOf(2)),
            ),
        )

        val presented = presenter.present(events(fact, tileCatalog = catalog), "test:rule").transactions.single().facts.single()

        val title = presented.text.content as TranslatableTextContent
        assertEquals(MinecraftHistoryScreenKeys.ROUND_REPLACEMENT_ROUND, title.key)
        assertEquals(1, title.args[0])
        assertEquals("test.flower_replacement", (title.args[1] as Text).string)
        assertEquals(null, presented.actorSeat)
        assertEquals(listOf(0, 1), presented.seatLines.map { it.seat })
        assertEquals(listOf(catalog.take(2), catalog.drop(2)), presented.seatLines.map { it.tiles })
    }

    /** 牌局中的換牌只顯示用語不帶輪次；規則沒有登記用語時使用中立的「換牌」。 */
    @Test
    fun `mid game replacements show only the wording and fall back to neutral wording`() {
        val presenter = replacementPresenter()
        val fact = HistoryReplayFactDto.TileReplacementRound(
            typeKey = HistoryFactTypeKeys.TILE_REPLACEMENT_ROUND_RESOLVED,
            timing = RuleDecisionTimingDto.DRAW_FOLLOW_UP,
            round = 2,
            removals = listOf(HistoryReplayFactDto.TileReplacementRound.Removal(seat = 0, tiles = listOf(0))),
        )
        val catalog = listOf(extensionFace("test:autumn"))

        assertEquals("test.flower_replacement", presenter.present(events(fact, tileCatalog = catalog), "test:rule").transactions.single().facts.single().text.string)
        assertEquals(MinecraftHistoryScreenKeys.ROUND_REPLACEMENT, presenter.present(events(fact, tileCatalog = catalog), "test:other").transactions.single().facts.single().text.string)
    }

    /** 換牌紀錄已說明結果的規則選擇步驟：開始改顯示「開始補花」，自動完成不再列出，即使開始記在較早的交易；每筆交易都保留。 */
    @Test
    fun `replacement records replace the bookkeeping of their rule decision step`() {
        val started = ruleDecision(HistoryFactTypeKeys.RULE_DECISION_STARTED, stepIndex = 0)
        val resolved = ruleDecision(HistoryFactTypeKeys.RULE_DECISION_AUTOMATIC_RESOLVED, stepIndex = 0)
        val otherStep = ruleDecision(HistoryFactTypeKeys.RULE_DECISION_STARTED, stepIndex = 1)
        val replacement = HistoryReplayFactDto.TileReplacementRound(
            typeKey = HistoryFactTypeKeys.TILE_REPLACEMENT_ROUND_RESOLVED,
            timing = RuleDecisionTimingDto.ROUND_START,
            round = 1,
            removals = listOf(HistoryReplayFactDto.TileReplacementRound.Removal(seat = 0, tiles = listOf(0))),
        )

        val presentation = replacementPresenter().present(
            transactions(listOf(started), listOf(resolved, replacement), listOf(otherStep), emptyList(), tileCatalog = listOf(extensionFace("test:autumn"))),
            "test:rule",
        )

        assertEquals(listOf(0, 1, 2, 3), presentation.transactions.map { it.index }, "Every transaction keeps its place.")
        val start = presentation.transactions[0].facts.single().text.content as TranslatableTextContent
        assertEquals(MinecraftHistoryScreenKeys.ROUND_REPLACEMENT_STARTED, start.key)
        assertEquals("test.flower_replacement", (start.args.single() as Text).string)
        assertEquals(replacement, presentation.transactions[1].facts.single().source)
        assertEquals(otherStep, presentation.transactions[2].facts.single().source, "A step without replacement records keeps its generic line.")
        assertEquals(emptyList(), presentation.transactions[3].facts, "A transaction recorded without facts is still listed.")
    }

    /** 玩家的選擇以動作用語顯示在玩家名下；選了代表跳過的選項時顯示跳過。該步驟的開始與其他事實同在一筆交易時不再另列，單獨一筆時保留一般文字。 */
    @Test
    fun `player choices use action wording and replace the step start`() {
        val presenter = replacementPresenter()
        val started = ruleDecision(HistoryFactTypeKeys.RULE_DECISION_STARTED, stepIndex = 0)
        val win = ruleDecision(HistoryFactTypeKeys.RULE_DECISION_SUBMITTED, stepIndex = 0, actorSeat = 0, optionId = BuiltInGameActionIds.TSUMO)
        val passed = ruleDecision(HistoryFactTypeKeys.RULE_DECISION_SUBMITTED, stepIndex = 0, actorSeat = 0, optionId = "test:pass_win", passed = true)

        val other = HistoryReplayFactDto.Opaque("custom:opaque", 0, emptyList(), emptyList())
        val winPresentation = presenter.present(transactions(listOf(other, started), listOf(win)), "test:rule")
        val passPresentation = presenter.present(transactions(listOf(started), listOf(passed)), "test:rule")

        assertEquals(listOf(other), winPresentation.transactions[0].facts.map { it.source })
        assertEquals("test.tsumo", winPresentation.transactions[1].facts.single().text.string)
        assertEquals(0, winPresentation.transactions[1].facts.single().actorSeat)
        assertEquals(MinecraftHistoryScreenKeys.RULE_DECISION, (passPresentation.transactions[0].facts.single().text.content as TranslatableTextContent).key)
        assertEquals("test.pass", passPresentation.transactions[1].facts.single().text.string)
    }

    /** 查不到名稱的選項保留一般的規則選擇文字，步驟的開始也照常列出。 */
    @Test
    fun `unnamed choices keep the generic rule decision line`() {
        val started = ruleDecision(HistoryFactTypeKeys.RULE_DECISION_STARTED, stepIndex = 0)
        val chosen = ruleDecision(HistoryFactTypeKeys.RULE_DECISION_SUBMITTED, stepIndex = 0, actorSeat = 0, optionId = "test:unnamed")

        val presentation = replacementPresenter().present(transactions(listOf(started), listOf(chosen)), "test:rule")

        assertEquals(MinecraftHistoryScreenKeys.RULE_DECISION, (presentation.transactions[0].facts.single().text.content as TranslatableTextContent).key)
        val generic = presentation.transactions[1].facts.single()
        assertEquals(MinecraftHistoryScreenKeys.RULE_DECISION, (generic.text.content as TranslatableTextContent).key)
        assertEquals(0, generic.actorSeat)
        assertTrue("test:unnamed" in generic.identifiers)
    }

    /**
     * 建立登記換牌用語與自摸、跳過用語的 presenter。
     *
     * @return 測試用 presenter。
     */
    private fun replacementPresenter(): HistoryRoundEventPresenter = HistoryRoundEventPresenter(
        actionVocabulary = GameActionVocabularyRegistryImpl().apply {
            register(GameActionVocabularyDefaultDefinition(BuiltInGameActionIds.TSUMO, GameActionVocabulary("test.tsumo")))
            register(GameActionVocabularyDefaultDefinition(BuiltInGameActionIds.PASS, GameActionVocabulary("test.pass")))
        },
        exhaustiveDrawReasons = ExhaustiveDrawReasonDisplayNameRegistryImpl(),
        roundOutcomes = RoundOutcomeDisplayNameRegistryImpl(),
        ruleDecisionNames = RuleDecisionDisplayNameRegistryImpl(),
        setAsideDisplays = HistoryTileSetAsideDisplayRegistryImpl().apply {
            register(HistoryTileReplacementDisplayDefinition("test:rule", "test.flower_replacement"))
        },
    )

    /**
     * 建立一筆規則選擇事實。
     *
     * @param typeKey 事實種類。
     * @param stepIndex 步驟順序。
     * @param actorSeat 提交的玩家座位。
     * @param optionId 選擇的選項。
     * @param passed 是否選了代表跳過的選項。
     * @return 規則選擇事實。
     */
    private fun ruleDecision(
        typeKey: String,
        stepIndex: Int,
        actorSeat: Int? = null,
        optionId: String? = null,
        passed: Boolean = false,
    ): HistoryReplayFactDto.RuleDecision = HistoryReplayFactDto.RuleDecision(
        typeKey = typeKey,
        timing = RuleDecisionTimingDto.ROUND_START,
        stepId = "test:step",
        stepIndex = stepIndex,
        nextStepId = null,
        actorSeat = actorSeat,
        optionId = optionId,
        passed = passed,
    )

    /**
     * 建立擴充牌面。
     *
     * @param typeId 牌種識別碼。
     * @return 沒有外觀的牌面。
     */
    private fun extensionFace(typeId: String): TileFaceViewDto = TileFaceViewDto(TileDto.Extension(typeId), emptyList(), null)

    /**
     * 建立多筆交易的單局事件，每筆交易依序放入一組事實。
     *
     * @param facts 每筆交易的事實。
     * @param tileCatalog 牌目錄。
     * @return 單局事件資料。
     */
    private fun transactions(vararg facts: List<HistoryReplayFactDto>, tileCatalog: List<TileFaceViewDto> = emptyList()): HistoryRoundEventsDto = HistoryRoundEventsDto(
        identity = HistoryReplayIdentityDto(
            matchId = "match",
            venueId = "table",
            players = listOf(HistoryReplayPlayerIdentityDto(0, null, null), HistoryReplayPlayerIdentityDto(1, null, null)),
        ),
        roundNumber = 1,
        transactions = facts.mapIndexed { index, transactionFacts -> HistoryReplayTransactionDto(index, 100L + index, false, tileCatalog.size, transactionFacts) },
        nextTransactionIndex = null,
        tileCatalog = tileCatalog,
    )

    /**
     * 建立測試所需的事件 presenter。
     *
     * @return 已註冊測試用動作詞彙的 presenter。
     */
    private fun presenter(): HistoryRoundEventPresenter {
        val vocabulary = GameActionVocabularyRegistryImpl().apply {
            register(GameActionVocabularyDefaultDefinition(BuiltInGameActionIds.DISCARD, GameActionVocabulary("test.discard")))
        }
        val reasons = ExhaustiveDrawReasonDisplayNameRegistryImpl().apply {
            register(ExhaustiveDrawReasonDisplayNameDefinition("example:registered_draw", "test.registered_draw"))
            register(
                definition = ExhaustiveDrawSettlementStatusLabelsDefinition(
                    reasonId = RiichiExhaustiveDrawReason.Normal.id,
                    labels = ExhaustiveDrawSettlementStatusLabels(
                        beneficiaryTranslationKey = RiichiTranslationKeys.EXHAUSTIVE_DRAW_TENPAI,
                        othersTranslationKey = RiichiTranslationKeys.EXHAUSTIVE_DRAW_NOTEN,
                    ),
                ),
            )
        }
        val outcomes = RoundOutcomeDisplayNameRegistryImpl().apply { register(RoundOutcomeDisplayNameDefinition("example:special", "test.special")) }
        return HistoryRoundEventPresenter(vocabulary, reasons, outcomes, RuleDecisionDisplayNameRegistryImpl(), HistoryTileSetAsideDisplayRegistryImpl())
    }

    /** 本局結果名稱依序查詢規則登記的特殊結果、流局原因，都沒有登記時使用通用的「其他」。 */
    @Test
    fun `outcome names come from registered outcomes then exhaustive draw reasons`() {
        val names = listOf("example:special", "example:registered_draw", "example:unknown").map { reasonId ->
            val outcome = HistoryRoundOutcomeDto(reasonId, listOf(1), emptyMap(), null, emptyList(), null, nextDealerSeat = null, scoreChangesBySeat = emptyMap(), winnerDetails = emptyList(), hasEarlierWinSettlement = false)
            val fact = presenter().present(events(HistoryReplayFactDto.Completion(HistoryFactTypeKeys.ROUND_COMPLETED, outcome)), null)
                .transactions.single().facts.single()
            checkNotNull(fact.outcome).reasonText.string
        }

        assertEquals(listOf("test.special", "test.registered_draw", MinecraftHistoryScreenKeys.ROUND_OUTCOME_OTHER), names)
    }

    /** 登記了結算身分用語的流局原因使用該用語；沒有登記的原因只顯示得分者的通用「受益」。 */
    @Test
    fun `normal draw status matches settlement panel without changing extension labels`() {
        val presenter = presenter()
        val outcome = HistoryOutcomePresentation(
            reasonId = RiichiExhaustiveDrawReason.Normal.id,
            reasonText = Text.literal("draw"),
            classification = RoundCompletionClassification.EXHAUSTIVE_DRAW.name,
            transitionDirective = null,
            nextDealerSeat = null,
            rows = emptyList(),
        )
        val tenpai = HistoryOutcomeRowPresentation(0, 28000, true, false, 3000)
        val noten = HistoryOutcomeRowPresentation(1, 24000, false, false, -1000)
        assertEquals(RiichiTranslationKeys.EXHAUSTIVE_DRAW_TENPAI, presenter.settlementStatusText(outcome, tenpai)?.string)
        assertEquals(RiichiTranslationKeys.EXHAUSTIVE_DRAW_NOTEN, presenter.settlementStatusText(outcome, noten)?.string)
        assertEquals(MinecraftHistoryScreenKeys.ROUND_BENEFICIARY, presenter.settlementStatusText(outcome.copy(reasonId = "custom:draw"), tenpai)?.string)
        assertEquals(null, presenter.settlementStatusText(outcome.copy(reasonId = "custom:draw"), noten))
        assertEquals("+3000", presenter.scoreChangeText(3000).string)
        assertEquals("−1000", presenter.scoreChangeText(-1000).string)
        assertEquals("±0", presenter.scoreChangeText(0).string)
    }

    /**
     * 建立含單一交易的測試事件頁。
     *
     * @param fact 單一事實便捷輸入。
     * @param facts 依序保存的事實。
     * @param tileCatalog 事實參照的牌目錄。
     * @return 單一交易事件頁。
     */
    private fun events(
        fact: HistoryReplayFactDto? = null,
        facts: List<HistoryReplayFactDto> = listOfNotNull(fact),
        tileCatalog: List<TileFaceViewDto> = emptyList(),
    ): HistoryRoundEventsDto = HistoryRoundEventsDto(
        identity = HistoryReplayIdentityDto(
            matchId = "match",
            venueId = "table",
            players = listOf(HistoryReplayPlayerIdentityDto(0, null, null)),
        ),
        roundNumber = 1,
        transactions = listOf(HistoryReplayTransactionDto(0, 100L, false, tileCatalog.size, facts)),
        nextTransactionIndex = null,
        tileCatalog = tileCatalog,
    )
}
