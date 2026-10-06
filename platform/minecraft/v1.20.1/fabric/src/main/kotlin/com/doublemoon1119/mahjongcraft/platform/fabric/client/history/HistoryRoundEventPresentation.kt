package com.doublemoon1119.mahjongcraft.platform.fabric.client.history

import com.doublemoon1119.mahjongcraft.flow.common.game.history.HistoryFactTypeKeys
import com.doublemoon1119.mahjongcraft.flow.common.game.history.replay.HistoryActionTypeKeys
import com.doublemoon1119.mahjongcraft.flow.common.game.model.BuiltInRoundOutcomeIds
import com.doublemoon1119.mahjongcraft.flow.common.game.model.WinSettlementDetailEntry
import com.doublemoon1119.mahjongcraft.flow.common.game.model.WinSettlementQuantity
import com.doublemoon1119.mahjongcraft.flow.common.game.model.riichi.RiichiRoundOutcomeIds
import com.doublemoon1119.mahjongcraft.flow.network.dto.message.HistoryReplayFactDto
import com.doublemoon1119.mahjongcraft.flow.network.dto.message.HistoryRoundEventsDto
import com.doublemoon1119.mahjongcraft.flow.network.dto.message.HistoryRoundOutcomeDto
import com.doublemoon1119.mahjongcraft.flow.network.dto.message.HistoryWinDetailFieldDto
import com.doublemoon1119.mahjongcraft.flow.network.dto.message.HistoryWinDetailQuantityDto
import com.doublemoon1119.mahjongcraft.flow.network.dto.message.HistoryWinDetailValueDto
import com.doublemoon1119.mahjongcraft.flow.network.dto.model.TileDto
import com.doublemoon1119.mahjongcraft.logic.rules.riichi.RiichiExhaustiveDrawReason
import com.doublemoon1119.mahjongcraft.logic.table.BuiltInMatchEndReasonIds
import com.doublemoon1119.mahjongcraft.logic.table.RoundCompletionClassification
import com.doublemoon1119.mahjongcraft.platform.minecraft.action.GameActionVocabularyRegistry
import com.doublemoon1119.mahjongcraft.platform.minecraft.action.MinecraftKanActionTokenKeys
import com.doublemoon1119.mahjongcraft.platform.minecraft.history.MinecraftHistoryScreenKeys
import com.doublemoon1119.mahjongcraft.platform.minecraft.settlement.ExhaustiveDrawReasonDisplayNameRegistry
import com.doublemoon1119.mahjongcraft.platform.minecraft.settlement.PresentationFieldId
import com.doublemoon1119.mahjongcraft.platform.minecraft.settlement.WinSettlementPresentationTemplateRegistry
import com.doublemoon1119.mahjongcraft.platform.minecraft.settlement.detailTextFormatter
import com.doublemoon1119.mahjongcraft.platform.minecraft.text.MinecraftMessageKeys
import net.minecraft.text.Text
import net.minecraft.util.Formatting
import com.doublemoon1119.mahjongcraft.platform.minecraft.action.BuiltInGameActionIds as MinecraftBuiltInGameActionIds

/** 將已驗證的單局歷史 DTO 轉成畫面可直接使用的事件呈現模型。
 *
 * 這個 presenter 只負責把安全 DTO 映射成文字、玩家座位與牌面參照；不重新判定規則，也不改變事件順序。
 *
 * @property actionVocabulary 動作 ID 的規則專屬顯示名稱來源。
 * @property exhaustiveDrawReasons 流局原因 ID 的顯示名稱來源。
 */
internal class HistoryRoundEventPresenter(
    private val actionVocabulary: GameActionVocabularyRegistry,
    private val exhaustiveDrawReasons: ExhaustiveDrawReasonDisplayNameRegistry,
) {
    /**
     * 將單局事件轉換為保留原始順序的呈現模型。
     *
     * @param events 已通過客戶端驗證的單局事件資料。
     * @param ruleId 對局摘要中的規則模組 ID；未提供時使用中立預設用語。
     * @return 可交給單局畫面分頁與卡片 renderer 使用的呈現模型。
     */
    fun present(events: HistoryRoundEventsDto, ruleId: String?): HistoryRoundPresentation = HistoryRoundPresentation(
        ruleId = ruleId,
        roundNumber = events.roundNumber,
        nextTransactionIndex = events.nextTransactionIndex,
        transactions = events.transactions.map { transaction ->
            HistoryTransactionPresentation(
                index = transaction.index,
                occurredAtEpochMillis = transaction.occurredAtEpochMillis,
                isOpening = transaction.isOpening,
                facts = transaction.facts.map { fact ->
                    fact.present(events.tileCatalog, events.identity.players.map { it.initialSeatIndex }, ruleId)
                },
            )
        },
    )

    /** 將單一語意事實映射成呈現模型。
     * @param tileCatalog 目前回覆可解析的牌目錄。
     * @param identitySeats 對局身份中的所有座位索引。
     * @param ruleId 對局規則模組 ID。
     * @return 單一事實的呈現資料。
     */
    private fun HistoryReplayFactDto.present(tileCatalog: List<TileDto>, identitySeats: List<Int>, ruleId: String?): HistoryFactPresentation = (
        when (this) {
            is HistoryReplayFactDto.KnownAction -> HistoryFactPresentation(
                text = actionText(ruleId, actionType, extensionTypeId),
                actorSeat = actorSeat,
                directTiles = tiles(tileCatalog, directTiles),
                revealedTiles = tiles(tileCatalog, revealedTiles),
                outcome = null,
            )

            is HistoryReplayFactDto.Reaction -> HistoryFactPresentation(
                text = Text.translatable(
                    MinecraftHistoryScreenKeys.ROUND_REACTION,
                    actionType?.let { actionText(ruleId, it, null) } ?: Text.translatable(MinecraftHistoryScreenKeys.ROUND_REACTION_NONE),
                ),
                actorSeat = resolvedActorSeat,
                directTiles = emptyList(),
                revealedTiles = emptyList(),
                outcome = null,
            )

            is HistoryReplayFactDto.Preparation -> HistoryFactPresentation(
                text = Text.translatable(
                    MinecraftHistoryScreenKeys.ROUND_PREPARATION,
                    Text.translatable(MinecraftHistoryScreenKeys.ROUND_PREPARATION_STEP, stepIndex + 1),
                    preparationText(typeKey, nextStepId),
                ),
                actorSeat = null,
                directTiles = emptyList(),
                revealedTiles = emptyList(),
                outcome = null,
            )

            is HistoryReplayFactDto.Completion -> HistoryFactPresentation(
                text = when (typeKey) {
                    HistoryFactTypeKeys.MATCH_COMPLETED -> Text.translatable(MinecraftHistoryScreenKeys.ROUND_MATCH_COMPLETION, outcome?.let { outcomeText(it.reasonId, ruleId) } ?: Text.translatable(MinecraftHistoryScreenKeys.ROUND_OUTCOME_OTHER))
                    HistoryFactTypeKeys.WIN_SETTLED -> Text.translatable(MinecraftHistoryScreenKeys.ROUND_WIN_SETTLEMENT, outcome?.let { outcomeText(it.reasonId, ruleId) } ?: Text.translatable(MinecraftHistoryScreenKeys.ROUND_OUTCOME_OTHER))
                    else -> Text.translatable(MinecraftHistoryScreenKeys.ROUND_COMPLETION, outcome?.let { outcomeText(it.reasonId, ruleId) } ?: Text.translatable(MinecraftHistoryScreenKeys.ROUND_OUTCOME_OTHER), outcome?.classification?.let(::classificationText) ?: "—")
                },
                actorSeat = null,
                directTiles = emptyList(),
                revealedTiles = emptyList(),
                outcome = outcome?.let { it.toPresentation(identitySeats, outcomeText(it.reasonId, ruleId)) },
            )
            is HistoryReplayFactDto.RuleEffect -> HistoryFactPresentation(
                text = Text.translatable(MinecraftHistoryScreenKeys.ROUND_RULE_EFFECT, outcomeText(reasonId, ruleId)),
                actorSeat = null,
                directTiles = emptyList(),
                revealedTiles = emptyList(),
                outcome = outcome?.let { it.toPresentation(identitySeats, outcomeText(it.reasonId, ruleId)) },
            )
            is HistoryReplayFactDto.Opaque -> HistoryFactPresentation(
                text = eventText(typeKey),
                actorSeat = actorSeat,
                directTiles = tiles(tileCatalog, directTiles),
                revealedTiles = tiles(tileCatalog, revealedTiles),
                outcome = null,
            )
        }
        ).copy(identifiers = identifiers())

    /** 收集僅供 tooltip 查閱的格式識別碼，不放進玩家可見摘要。
     * @return 保留順序且去除重複的識別碼。
     */
    private fun HistoryReplayFactDto.identifiers(): List<String> = (
        listOf(typeKey) + when (this) {
            is HistoryReplayFactDto.KnownAction -> listOfNotNull(actionType, extensionTypeId)
            is HistoryReplayFactDto.Reaction -> listOfNotNull(actionType)
            is HistoryReplayFactDto.Preparation -> listOfNotNull(stepId, nextStepId)
            is HistoryReplayFactDto.Completion -> listOfNotNull(outcome?.reasonId, outcome?.classification)
            is HistoryReplayFactDto.RuleEffect -> listOfNotNull(reasonId, outcome?.reasonId, outcome?.classification)
            is HistoryReplayFactDto.Opaque -> emptyList()
        }
        ).distinct()

    /** 內建流程事實的玩家用語；未知擴充只使用通用文字。
     * @param typeKey 保存的事實種類。
     * @return 不包含格式 ID 的玩家用語。
     */
    private fun eventText(typeKey: String): Text = Text.translatable(
        when (typeKey) {
            HistoryFactTypeKeys.WIN_CONTINUATION_RESOLVED -> MinecraftHistoryScreenKeys.ROUND_EVENT_WIN_CONTINUATION
            HistoryFactTypeKeys.RETURNED_TO_ROOM -> MinecraftHistoryScreenKeys.ROUND_EVENT_RETURNED_TO_ROOM
            HistoryFactTypeKeys.MATCH_STARTED -> MinecraftHistoryScreenKeys.ROUND_EVENT_MATCH_STARTED
            HistoryFactTypeKeys.ROUND_STARTED -> MinecraftHistoryScreenKeys.ROUND_EVENT_ROUND_STARTED
            else -> MinecraftHistoryScreenKeys.ROUND_UNKNOWN_FACT
        },
    )

    /** 依記錄種類描述準備狀態，不將尚未開始推進的事件誤判為準備完成。
     * @param typeKey 保存的準備事實種類。
     * @param nextStepId 自動處理後的下一步識別碼；開始／提交事實不以此欄位推斷完成。
     * @return 本地化準備狀態。
     */
    private fun preparationText(typeKey: String, nextStepId: String?): Text = Text.translatable(
        when (typeKey) {
            HistoryFactTypeKeys.ROUND_PREPARATION_STARTED -> MinecraftHistoryScreenKeys.ROUND_PREPARATION_STARTED
            HistoryFactTypeKeys.ROUND_PREPARATION_SUBMITTED -> MinecraftHistoryScreenKeys.ROUND_PREPARATION_SUBMITTED
            HistoryFactTypeKeys.ROUND_PREPARATION_AUTOMATIC_RESOLVED -> if (nextStepId == null) MinecraftHistoryScreenKeys.ROUND_PREPARATION_NONE else MinecraftHistoryScreenKeys.ROUND_PREPARATION_NEXT
            else -> MinecraftHistoryScreenKeys.ROUND_UNKNOWN_FACT
        },
    )

    /** 將保存的結算分類轉成玩家用語，未知分類的原值由事實 tooltip 保留。
     * @param classification 保存的分類名稱。
     * @return 本地化分類。
     */
    fun classificationText(classification: String): Text = Text.translatable(
        when (classification) {
            RoundCompletionClassification.WIN.name -> MinecraftHistoryScreenKeys.ROUND_CLASSIFICATION_WIN
            RoundCompletionClassification.EXHAUSTIVE_DRAW.name -> MinecraftHistoryScreenKeys.ROUND_CLASSIFICATION_EXHAUSTIVE_DRAW
            RoundCompletionClassification.ABORTIVE_DRAW.name -> MinecraftHistoryScreenKeys.ROUND_CLASSIFICATION_ABORTIVE_DRAW
            RoundCompletionClassification.EXTENSION.name -> MinecraftHistoryScreenKeys.ROUND_CLASSIFICATION_EXTENSION
            else -> MinecraftHistoryScreenKeys.ROUND_CLASSIFICATION_OTHER
        },
    )

    /**
     * 將單次權威分數差轉成具正負號與柔和配色的文字，不由絕對分數推測差額。
     * @param change 該次交易前後分數差。
     * @return 可附加於玩家分數列的差額文字。
     */
    fun scoreChangeText(change: Int): Text = Text.literal(
        when {
            change > 0 -> "+$change"
            change < 0 -> "−${-change.toLong()}"
            else -> "±0"
        },
    ).styled { it.withColor(scoreChangeColor(change)) }

    /**
     * 解析已保存的玩家結算身分；一般日麻流局沿用流局面板的聽牌與未聽牌用語。
     * @param outcome 本次結算呈現資料。
     * @param row 玩家結算列。
     * @return 可翻譯的身分文字；沒有身分時為 null。
     */
    fun settlementStatusText(outcome: HistoryOutcomePresentation, row: HistoryOutcomeRowPresentation): Text? = when {
        outcome.classification == RoundCompletionClassification.EXHAUSTIVE_DRAW.name &&
            outcome.reasonId == RiichiExhaustiveDrawReason.Normal.id -> Text.translatable(
            if (row.beneficiary) MinecraftMessageKeys.EXHAUSTIVE_DRAW_SETTLEMENT_STATUS_TENPAI else MinecraftMessageKeys.EXHAUSTIVE_DRAW_SETTLEMENT_STATUS_NOTEN,
        ).styled { it.withColor(0xFFE08A) }
        row.beneficiary -> Text.translatable(MinecraftHistoryScreenKeys.ROUND_BENEFICIARY)
        else -> null
    }

    /**
     * 取得分數變化的柔和文字色。
     * @param change 該次交易前後分數差。
     * @return RGB 配色。
     */
    private fun scoreChangeColor(change: Int): Int = when {
        change > 0 -> 0x91c998
        change < 0 -> 0xde9696
        else -> 0xaaaaaa
    }

    /**
     * 將規則提供的詳情語意值轉為文字列；顯示文字由該欄位登記的格式化器決定。
     * @param fieldId 已驗證的明細欄位識別碼。
     * @param value 保存且已驗證的數值或條目資料；牌面詳情由牌面 renderer 處理。
     * @param templates 正式結算模板註冊表。
     * @return 依保存順序排列的文字列。
     */
    fun detailText(fieldId: String, value: HistoryWinDetailValueDto, templates: WinSettlementPresentationTemplateRegistry): List<Text> {
        val formatter = templates.detailTextFormatter(fieldId)
        return when (value) {
            is HistoryWinDetailValueDto.Quantities -> formatter.quantities(value.quantities.map { it.toQuantity() }).let { text ->
                listOf(Text.translatable(text.translationKey, *text.arguments.toTypedArray()))
            }
            is HistoryWinDetailValueDto.Entries -> formatter.entries(value.entries.map { WinSettlementDetailEntry(it.id, it.quantity?.toQuantity()) }).entries.map { entry ->
                val text = Text.translatable(entry.translationKey)
                val trailing = entry.trailingTranslationKey?.let { key ->
                    entry.trailingTranslationArgument?.let { Text.translatable(key, it) } ?: Text.translatable(key)
                } ?: entry.trailingText.takeIf(String::isNotBlank)?.let(Text::literal)
                trailing?.let { text.append(" • ").append(it.copy().formatted(Formatting.GRAY)) } ?: text
            }
            is HistoryWinDetailValueDto.Tiles -> emptyList()
        }
    }

    /** 依對局規則使用的模板取得欄位標題，不推測未知規則的欄位語意。
     * @param ruleId 對局規則模組 ID；未知時為 null。
     * @param fieldId 已驗證的明細欄位識別碼。
     * @param templates 正式結算模板註冊表。
     * @return 本地化標題；沒有對應模板或標題時為 null。
     */
    fun detailLabel(ruleId: String?, fieldId: String, templates: WinSettlementPresentationTemplateRegistry): Text? = ruleId
        ?.let(templates::findTemplateForRule)?.detailFieldLabelKeys?.get(PresentationFieldId(fieldId))?.let(Text::translatable)

    /** 將網路 DTO 的有單位數值還原為語意值。 */
    private fun HistoryWinDetailQuantityDto.toQuantity(): WinSettlementQuantity = WinSettlementQuantity(unitId, amount)

    /** 將動作 ID 解析成規則化顯示文字；未知 ID 使用通用玩家用語。
     * @param ruleId 對局規則模組 ID。
     * @param actionType 保存格式中的動作種類。
     * @param extensionTypeId 擴充動作 ID；一般動作為 null。
     * @return 動作顯示文字。
     */
    private fun actionText(ruleId: String?, actionType: String, extensionTypeId: String?): Text {
        when (actionType) {
            HistoryActionTypeKeys.KAN -> return Text.translatable(MinecraftHistoryScreenKeys.ROUND_ACTION_KAN)
            HistoryActionTypeKeys.DRAW -> return Text.translatable(MinecraftHistoryScreenKeys.ROUND_ACTION_DRAW)
            HistoryActionTypeKeys.EXHAUSTIVE_DRAW -> return Text.translatable(MinecraftHistoryScreenKeys.ROUND_CLASSIFICATION_EXHAUSTIVE_DRAW)
            HistoryActionTypeKeys.GAME_STARTED -> return Text.translatable(MinecraftHistoryScreenKeys.ROUND_ACTION_GAME_STARTED)
            HistoryActionTypeKeys.ROUND_STARTED -> return Text.translatable(MinecraftHistoryScreenKeys.ROUND_ACTION_ROUND_STARTED)
            HistoryActionTypeKeys.MATCH_ENDED -> return Text.translatable(MinecraftHistoryScreenKeys.ROUND_ACTION_MATCH_ENDED)
            HistoryActionTypeKeys.DICE_ROLLED -> return Text.translatable(MinecraftHistoryScreenKeys.ROUND_ACTION_DICE_ROLLED)
        }
        val actionId = normalizeActionId(actionType, extensionTypeId)
        val translationKey = actionVocabulary.find(ruleId, actionId)?.labelKey
            ?: exhaustiveDrawReasons.find(actionId)
        return Text.translatable(translationKey ?: MinecraftHistoryScreenKeys.ROUND_ACTION_OTHER)
    }

    /** 將結算原因解析成規則提供的文字，未知原因使用通用玩家用語。
     * @param reasonId 結算原因 ID。
     * @param ruleId 對局規則模組 ID。
     * @return 結算原因顯示文字。
     */
    private fun outcomeText(reasonId: String, ruleId: String?): Text = when (reasonId) {
        BuiltInRoundOutcomeIds.TSUMO -> Text.translatable(actionVocabulary.find(ruleId, MinecraftBuiltInGameActionIds.TSUMO)?.labelKey ?: MinecraftHistoryScreenKeys.ROUND_OUTCOME_TSUMO)
        BuiltInRoundOutcomeIds.RON -> Text.translatable(actionVocabulary.find(ruleId, MinecraftBuiltInGameActionIds.RON)?.labelKey ?: MinecraftHistoryScreenKeys.ROUND_OUTCOME_RON)
        RiichiRoundOutcomeIds.NAGASHI_MANGAN -> Text.translatable(MinecraftHistoryScreenKeys.ROUND_OUTCOME_NAGASHI_MANGAN)
        BuiltInMatchEndReasonIds.SCHEDULE_COMPLETED -> Text.translatable(MinecraftHistoryScreenKeys.ROUND_OUTCOME_SCHEDULE_COMPLETED)
        BuiltInMatchEndReasonIds.TARGET_SCORE_REACHED -> Text.translatable(MinecraftHistoryScreenKeys.ROUND_OUTCOME_TARGET_SCORE_REACHED)
        BuiltInMatchEndReasonIds.EXTRA_ROUND_LIMIT_REACHED -> Text.translatable(MinecraftHistoryScreenKeys.ROUND_OUTCOME_EXTRA_ROUND_LIMIT_REACHED)
        BuiltInMatchEndReasonIds.DEALER_TOP_FINISH -> Text.translatable(MinecraftHistoryScreenKeys.ROUND_OUTCOME_DEALER_TOP_FINISH)
        BuiltInMatchEndReasonIds.PLAYER_BUSTED -> Text.translatable(MinecraftHistoryScreenKeys.ROUND_OUTCOME_PLAYER_BUSTED)
        else -> Text.translatable(exhaustiveDrawReasons.find(reasonId) ?: MinecraftHistoryScreenKeys.ROUND_OUTCOME_OTHER)
    }

    /** 依 DTO 的索引取得牌種；驗證外的索引不會讓呈現流程崩潰。
     * @param tileCatalog 目前回覆可解析的牌目錄。
     * @param indexes 牌目錄索引，保留原始順序與重複。
     * @return 可解析的牌種清單。
     */
    private fun tiles(tileCatalog: List<TileDto>, indexes: List<Int>): List<TileDto> = indexes.mapNotNull(tileCatalog::getOrNull)

    /** 將保存格式的短動作名稱映射為呈現 registry 使用的穩定內建 ID。
     * @param actionType 保存格式中的動作種類。
     * @param extensionTypeId 擴充動作 ID；一般動作為 null。
     * @return registry 使用的動作 ID。
     */
    private fun normalizeActionId(actionType: String, extensionTypeId: String?): String = when (actionType) {
        HistoryActionTypeKeys.CHI, MinecraftBuiltInGameActionIds.CHI -> MinecraftBuiltInGameActionIds.CHI
        HistoryActionTypeKeys.PON, MinecraftBuiltInGameActionIds.PON -> MinecraftBuiltInGameActionIds.PON
        MinecraftKanActionTokenKeys.OPEN, MinecraftBuiltInGameActionIds.KAN_OPEN -> MinecraftBuiltInGameActionIds.KAN_OPEN
        MinecraftKanActionTokenKeys.CLOSED, MinecraftBuiltInGameActionIds.KAN_CLOSED -> MinecraftBuiltInGameActionIds.KAN_CLOSED
        MinecraftKanActionTokenKeys.ADDED, MinecraftBuiltInGameActionIds.KAN_ADDED -> MinecraftBuiltInGameActionIds.KAN_ADDED
        HistoryActionTypeKeys.RON, MinecraftBuiltInGameActionIds.RON -> MinecraftBuiltInGameActionIds.RON
        HistoryActionTypeKeys.TSUMO, MinecraftBuiltInGameActionIds.TSUMO -> MinecraftBuiltInGameActionIds.TSUMO
        HistoryActionTypeKeys.PASS, MinecraftBuiltInGameActionIds.PASS -> MinecraftBuiltInGameActionIds.PASS
        HistoryActionTypeKeys.DISCARD, MinecraftBuiltInGameActionIds.DISCARD -> MinecraftBuiltInGameActionIds.DISCARD
        HistoryActionTypeKeys.EXTENSION -> extensionTypeId ?: actionType
        else -> actionType
    }
}

/** 單局歷史事件的完整呈現模型。
 * @property ruleId 對局摘要中的規則模組 ID。
 * @property roundNumber 局序號。
 * @property transactions 保留交易順序的呈現資料。
 * @property nextTransactionIndex 下一頁交易索引；null 表示已到末端。
 */
internal data class HistoryRoundPresentation(
    val ruleId: String?,
    val roundNumber: Int,
    val transactions: List<HistoryTransactionPresentation>,
    val nextTransactionIndex: Int?,
)

/** 單一歷史交易的呈現資料。
 * @property index 交易索引。
 * @property occurredAtEpochMillis 發生時間。
 * @property isOpening 是否為開局交易。
 * @property facts 交易內依原始順序排列的語意事實。
 */
internal data class HistoryTransactionPresentation(
    val index: Int,
    val occurredAtEpochMillis: Long,
    val isOpening: Boolean,
    val facts: List<HistoryFactPresentation>,
)

/** 單一歷史事實的呈現資料。
 * @property text 事實的本地化玩家用語，不包含未知格式識別碼。
 * @property actorSeat 可取得時的發起玩家座位。
 * @property directTiles 直接涉及的牌種，保留重複項目與原始順序。
 * @property revealedTiles 新公開的牌種，保留重複項目與原始順序。
 * @property outcome 結算資料；沒有結算事實或保存結果為 null 時為 null。
 * @property identifiers 僅供 tooltip 顯示的原始識別碼。
 */
internal data class HistoryFactPresentation(
    val text: Text,
    val actorSeat: Int?,
    val directTiles: List<TileDto>,
    val revealedTiles: List<TileDto>,
    val outcome: HistoryOutcomePresentation?,
    val identifiers: List<String> = emptyList(),
)

/** 單一結算事實的保存結果呈現資料。
 * @property reasonId 原始結算原因 ID。
 * @property reasonText 結算原因顯示文字。
 * @property classification 保存的結算分類；缺少時為 null。
 * @property transitionDirective 保存的莊家推進指令；缺少時為 null。
 * @property rows 身分與結算涉及的玩家列；缺少分數時仍保留玩家及角色資訊。
 * @property hasEarlierWinSettlement 同局先前已有獨立和牌結算記錄，不將後續摘要誤標為缺少明細。
 */
internal data class HistoryOutcomePresentation(
    val reasonId: String,
    val reasonText: Text,
    val classification: String?,
    val transitionDirective: String?,
    val rows: List<HistoryOutcomeRowPresentation>,
    val hasEarlierWinSettlement: Boolean = false,
)

/** 單一玩家的結算呈現資料。
 * @property seatIndex 玩家座位。
 * @property score 結算後的絕對分數；沒有保存該玩家分數時為 null。
 * @property scoreChange 該筆交易的權威分數差；缺少可信的前後資料時為 null。
 * @property detailFields 該玩家已保存的和牌明細；缺少時為空，不重新計分。
 * @property beneficiary 是否屬於結算受益玩家。
 * @property responsible 是否屬於結算責任玩家。
 */
internal data class HistoryOutcomeRowPresentation(
    val seatIndex: Int,
    val score: Int?,
    val beneficiary: Boolean,
    val responsible: Boolean,
    val scoreChange: Int? = null,
    val detailFields: List<HistoryWinDetailFieldDto> = emptyList(),
)

/** 將結算 DTO 轉成保留結算存在性的呈現資料。
 * @param identitySeats 對局身份中的所有座位索引。
 * @param reasonText 已解析的結算原因顯示文字。
 * @return 結算呈現資料。
 */
private fun HistoryRoundOutcomeDto.toPresentation(identitySeats: List<Int>, reasonText: Text): HistoryOutcomePresentation = HistoryOutcomePresentation(
    reasonId = reasonId,
    reasonText = reasonText,
    classification = classification,
    transitionDirective = transitionDirective,
    rows = (identitySeats + beneficiarySeats + responsibleSeats + scoresBySeat.keys).distinct().sorted().map { seat ->
        HistoryOutcomeRowPresentation(
            seat,
            scoresBySeat[seat],
            seat in beneficiarySeats,
            seat in responsibleSeats,
            scoreChangesBySeat[seat],
            winnerDetails.firstOrNull { it.seatIndex == seat }?.detailFields.orEmpty(),
        )
    },
    hasEarlierWinSettlement = hasEarlierWinSettlement,
)
