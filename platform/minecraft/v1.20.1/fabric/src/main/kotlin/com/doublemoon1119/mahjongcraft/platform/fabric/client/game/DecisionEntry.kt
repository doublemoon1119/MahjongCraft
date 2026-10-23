package com.doublemoon1119.mahjongcraft.platform.fabric.client.game

import com.doublemoon1119.mahjongcraft.platform.minecraft.decision.PlayerDecisionActionDto
import com.doublemoon1119.mahjongcraft.platform.minecraft.decision.PlayerDecisionPromptDto
import com.doublemoon1119.mahjongcraft.platform.minecraft.decision.RuleDecisionPromptDto
import net.minecraft.text.Text

/** 點擊一張操作卡後要執行的事。 */
internal sealed interface DecisionEntryIntent {
    /** 直接送出這個動作候選。 */
    data class SubmitAction(val token: String) : DecisionEntryIntent

    /** 進入這個動作的實體牌選取模式。 */
    data class BeginActionTileSelection(val action: PlayerDecisionActionDto) : DecisionEntryIntent

    /** 送出規則選擇的確認。 */
    data object ConfirmRuleDecision : DecisionEntryIntent

    /** 送出規則選擇的單一選項。 */
    data class ChooseRuleDecisionOption(val optionId: String) : DecisionEntryIntent

    /** 進入規則選擇的實體牌選取模式。 */
    data object BeginRuleDecisionTileSelection : DecisionEntryIntent
}

/**
 * 一張可點擊的操作卡。
 *
 * @property label 卡片按鈕的文字。
 * @property previewTileAssetKeys 卡片內要顯示的預覽牌面。
 * @property claimedTileIndex [previewTileAssetKeys] 中要額外標記的牌索引；沒有要標記時為 `null`。
 * @property intent 點擊後要執行的事。
 * @property description 滑鼠移到卡片上時顯示的說明；沒有說明時為 `null`。
 * @property cardBody 卡片中央的簡短說明；沒有說明時為 `null`。
 * @property cardHint 按鈕附近的補充提示；沒有提示時為 `null`。
 */
internal data class DecisionEntry(
    val label: Text,
    val previewTileAssetKeys: List<String>,
    val claimedTileIndex: Int?,
    val intent: DecisionEntryIntent,
    val description: Text? = null,
    val cardBody: Text? = null,
    val cardHint: Text? = null,
) {
    /** 這張卡片影響版面的特徵。 */
    fun layoutCard(): DecisionCard = DecisionCard(previewTileAssetKeys.size, claimedTileIndex != null)
}

/** 未登記順序的動作排在所有已登記之後，並維持原始相對順序。 */
private const val UNREGISTERED_ACTION_ORDER: Int = Int.MAX_VALUE

/**
 * 由權威 prompt 組出由左至右的操作卡清單。
 *
 * 動作卡在前並依固定顯示順序排列，「跳過」不佔卡片位置（由 header 右側的專屬按鈕負責）；規則選擇的三種
 * 型態各自展開成確認、每個選項一張，或單一張選牌卡。
 *
 * @param texts 動作名稱、排序與說明的解析器。
 * @param prompt 伺服器提供的目前決策提示。
 * @return 依顯示順序排列、包含規則選擇項目的卡片。
 */
internal fun decisionEntriesFrom(texts: DecisionTextResolver, prompt: PlayerDecisionPromptDto): List<DecisionEntry> = buildList {
    prompt.actions.filterNot { it.actionId == PASS_ACTION_ID }
        .sortedBy { texts.actionOrder(prompt.ruleModuleId, it.actionId) ?: UNREGISTERED_ACTION_ORDER }
        .forEach { action ->
            add(
                DecisionEntry(
                    label = texts.actionLabel(prompt.ruleModuleId, action.actionId),
                    previewTileAssetKeys = action.previewTileAssetKeys,
                    claimedTileIndex = action.claimedTileIndex,
                    intent = if (action.tileSelection != null) {
                        DecisionEntryIntent.BeginActionTileSelection(action)
                    } else {
                        DecisionEntryIntent.SubmitAction(action.token)
                    },
                    description = texts.actionDescription(prompt.ruleModuleId, action.actionId),
                    cardBody = texts.actionCardBody(prompt.ruleModuleId, action.actionId),
                    cardHint = texts.actionCardHint(prompt.ruleModuleId, action.actionId),
                ),
            )
        }
    when (val ruleDecision = prompt.ruleDecision) {
        RuleDecisionPromptDto.Confirmation -> add(
            DecisionEntry(
                label = Text.translatable("mahjongcraft.hud.action.confirm"),
                previewTileAssetKeys = emptyList(),
                claimedTileIndex = null,
                intent = DecisionEntryIntent.ConfirmRuleDecision,
            ),
        )
        is RuleDecisionPromptDto.SingleChoice -> ruleDecision.optionIds.forEach { option ->
            add(
                DecisionEntry(
                    label = texts.actionLabel(prompt.ruleModuleId, option),
                    previewTileAssetKeys = emptyList(),
                    claimedTileIndex = null,
                    intent = DecisionEntryIntent.ChooseRuleDecisionOption(option),
                ),
            )
        }
        is RuleDecisionPromptDto.TileSelection -> add(
            DecisionEntry(
                label = Text.translatable("mahjongcraft.hud.action.select_tiles", ruleDecision.maxCount),
                previewTileAssetKeys = ruleDecision.eligibleTileAssetKeys,
                claimedTileIndex = null,
                intent = DecisionEntryIntent.BeginRuleDecisionTileSelection,
            ),
        )
        null -> Unit
    }
}

/** 不佔卡片位置的跳過動作。 */
internal const val PASS_ACTION_ID = "mahjongcraft:pass"
