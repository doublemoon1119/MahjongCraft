package com.doublemoon1119.mahjongcraft.flow.network.dto.message

import kotlinx.serialization.Serializable

/** HUD 可呈現的單一權威動作候選。 */
@Serializable
data class PlayerDecisionActionDto(
    val token: String,
    val actionId: String,
    val referenceTileAssetKey: String? = null,
    val previewTileAssetKeys: List<String> = emptyList(),
    val claimedTileIndex: Int? = null,
    val claimedTileOrientation: DecisionTileOrientationDto = DecisionTileOrientationDto.UPRIGHT,
)

/** 操作預覽中牌面的受控方向。 */
@Serializable
enum class DecisionTileOrientationDto {
    UPRIGHT,
    ROTATED_LEFT,
    ROTATED_RIGHT,
}

/** 觸發他家反應的玩家相對位置。 */
@Serializable
enum class DecisionPlayerRelationDto {
    LEFT,
    ACROSS,
    RIGHT,
}

/** 一張等待牌及依玩家可見資訊推算的剩餘張數。 */
@Serializable
data class WaitingTileAvailabilityDto(
    val tileAssetKey: String,
    val remainingCount: Int,
)

/** 打出指定實體手牌後的聽牌分析。 */
@Serializable
data class DiscardReadinessAnalysisDto(
    val discardTileId: String,
    val waitingTiles: List<WaitingTileAvailabilityDto>,
    val statusIndicatorId: String? = null,
)

/** 開局準備輸入在操作 HUD 使用的受控網路表示。 */
@Serializable
sealed interface RoundPreparationPromptDto {
    /** 只需要確認的準備步驟。 */
    @Serializable
    data object Confirmation : RoundPreparationPromptDto

    /** 從穩定 option ID 清單中選擇一項的準備步驟。 */
    @Serializable
    data class SingleChoice(val optionIds: List<String>) : RoundPreparationPromptDto

    /** 使用實體手牌選擇指定數量牌張的準備步驟。 */
    @Serializable
    data class TileSelection(
        val eligibleTileIds: List<String>,
        /** 與 [eligibleTileIds] 相同順序、只公開給本人的牌面資產。 */
        val eligibleTileAssetKeys: List<String> = emptyList(),
        val minCount: Int,
        val maxCount: Int,
    ) : RoundPreparationPromptDto
}

/**
 * 只傳給取得決策權玩家的 HUD prompt。
 *
 * 所有牌張 ID 都是該玩家已知的實體手牌；分析結果只包含自身與公開資訊，不包含暗手或牌山內容。
 */
@Serializable
data class PlayerDecisionPromptDto(
    val decisionKey: String,
    val actions: List<PlayerDecisionActionDto> = emptyList(),
    val triggerTileAssetKey: String? = null,
    val triggerPlayerId: String? = null,
    val triggerPlayerName: String? = null,
    val triggerPlayerRelation: DecisionPlayerRelationDto? = null,
    val triggerActionId: String? = null,
    val riichiTileIds: List<String> = emptyList(),
    val riichiTileAssetKeys: List<String> = emptyList(),
    val preparation: RoundPreparationPromptDto? = null,
    val discardAnalyses: List<DiscardReadinessAnalysisDto> = emptyList(),
)

/** 客戶端提交 prompt 選擇時使用的受控操作種類。 */
@Serializable
enum class PlayerDecisionSelectionKindDto {
    ACTION,
    BEGIN_RIICHI,
    PREPARATION_CONFIRM,
    PREPARATION_CHOICE,
    PREPARATION_TILES,
}

/** 客戶端操作 HUD 提交的權威候選 token。 */
@Serializable
data class PlayerDecisionSelectionDto(
    val gameId: String,
    val decisionKey: String,
    val kind: PlayerDecisionSelectionKindDto,
    val token: String? = null,
    val tileIds: List<String> = emptyList(),
)
