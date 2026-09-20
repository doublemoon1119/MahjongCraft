package com.doublemoon1119.mahjongcraft.platform.fabric.client.game

import com.doublemoon1119.mahjongcraft.flow.network.dto.message.PlayerDecisionActionDto
import com.doublemoon1119.mahjongcraft.flow.network.dto.message.PlayerDecisionPromptDto
import com.doublemoon1119.mahjongcraft.flow.network.dto.message.RoundPreparationPromptDto
import net.minecraft.text.Text

/** 點擊一張操作卡後要執行的事。 */
internal sealed interface DecisionEntryIntent {
    /** 直接送出這個動作候選。 */
    data class SubmitAction(val token: String) : DecisionEntryIntent

    /** 進入這個動作的實體牌選取模式。 */
    data class BeginActionTileSelection(val action: PlayerDecisionActionDto) : DecisionEntryIntent

    /** 送出開局準備的確認。 */
    data object ConfirmPreparation : DecisionEntryIntent

    /** 送出開局準備的單一選項。 */
    data class ChoosePreparationOption(val optionId: String) : DecisionEntryIntent

    /** 進入開局準備的實體牌選取模式。 */
    data object BeginPreparationTileSelection : DecisionEntryIntent
}

/**
 * 一張可點擊的操作卡。
 *
 * @property label 卡片按鈕的文字。
 * @property previewTileAssetKeys 卡片內要顯示的預覽牌面。
 * @property claimedTileIndex [previewTileAssetKeys] 中要額外標記的牌索引；沒有要標記時為 `null`。
 * @property intent 點擊後要執行的事。
 */
internal data class DecisionEntry(
    val label: Text,
    val previewTileAssetKeys: List<String>,
    val claimedTileIndex: Int?,
    val intent: DecisionEntryIntent,
) {
    /** 這張卡片影響版面的特徵。 */
    fun layoutCard(): DecisionCard = DecisionCard(previewTileAssetKeys.size, claimedTileIndex != null)
}

/** 未登記順序的動作排在所有已登記之後，並維持原始相對順序。 */
private const val UNREGISTERED_ACTION_ORDER: Int = Int.MAX_VALUE

/**
 * 由權威 prompt 組出由左至右的操作卡清單。
 *
 * 動作卡在前並依固定顯示順序排列，「跳過」不佔卡片位置（由 header 右側的專屬按鈕負責）；開局準備的三種
 * 型態各自展開成確認、每個選項一張，或單一張選牌卡。
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
                ),
            )
        }
    when (val preparation = prompt.preparation) {
        RoundPreparationPromptDto.Confirmation -> add(
            DecisionEntry(
                label = Text.translatable("mahjongcraft.hud.action.confirm"),
                previewTileAssetKeys = emptyList(),
                claimedTileIndex = null,
                intent = DecisionEntryIntent.ConfirmPreparation,
            ),
        )
        is RoundPreparationPromptDto.SingleChoice -> preparation.optionIds.forEach { option ->
            add(
                DecisionEntry(
                    label = texts.actionLabel(prompt.ruleModuleId, option),
                    previewTileAssetKeys = emptyList(),
                    claimedTileIndex = null,
                    intent = DecisionEntryIntent.ChoosePreparationOption(option),
                ),
            )
        }
        is RoundPreparationPromptDto.TileSelection -> add(
            DecisionEntry(
                label = Text.translatable("mahjongcraft.hud.action.select_tiles", preparation.maxCount),
                previewTileAssetKeys = preparation.eligibleTileAssetKeys,
                claimedTileIndex = null,
                intent = DecisionEntryIntent.BeginPreparationTileSelection,
            ),
        )
        null -> Unit
    }
}

/** 不佔卡片位置的跳過動作。 */
internal const val PASS_ACTION_ID = "mahjongcraft:pass"
