package com.doublemoon1119.mahjongcraft.flow.network.dto.message

import kotlinx.serialization.Serializable

/**
 * 呈現層可顯示的單一權威動作候選（在 Minecraft 平台上對應操作 HUD 的一張卡片）。
 *
 * @property claimedTileIndex [previewTileAssetKeys] 中要額外標記強調的牌索引；目前只有吃會給值
 * （三張牌花色/數值不同，標出來才有辨識意義），碰／槓一律為 `null`——牌面彼此完全相同，標哪一張都
 * 沒有實質資訊。卡片預覽一律直立顯示，不套用鳴牌後最終桌面朝向。
 * @property tileSelection 這個動作若非 `null`，代表宣告後還需要玩家從立牌中額外選出牌張才算完成
 * （例如立直宣告後還要另外指定打哪張牌）；點擊卡片後改為進入實體牌選取模式，不直接送出。
 */
@Serializable
data class PlayerDecisionActionDto(
    val token: String,
    val actionId: String,
    val referenceTileAssetKey: String? = null,
    val previewTileAssetKeys: List<String> = emptyList(),
    val claimedTileIndex: Int? = null,
    val tileSelection: PlayerDecisionActionTileSelectionDto? = null,
)

/**
 * [PlayerDecisionActionDto.tileSelection] 的候選牌、選牌數量限制，以及選擇該動作後才適用的呈現分析。
 *
 * @property discardAnalyses 以該動作已成立的假設狀態計算之捨牌分析；空清單代表沿用 prompt 的一般分析。
 */
@Serializable
data class PlayerDecisionActionTileSelectionDto(
    val eligibleTileIds: List<String>,
    val minCount: Int,
    val maxCount: Int,
    val discardAnalyses: List<DiscardReadinessAnalysisDto> = emptyList(),
)

/**
 * 牌面的受控方向，供平台在世界空間呈現鳴牌 popup 時沿用；決策提示本身的牌面預覽不使用此列舉，
 * 一律直立顯示。
 */
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

/**
 * 一張等待牌及依玩家可見資訊推算的剩餘張數。
 *
 * [winAvailability] 是規則模組自訂的命名字串，比照 [DiscardReadinessAnalysisDto.statusIndicatorId] 的
 * 慣例：預設值 [WIN_AVAILABLE_ID] 代表「沒有任何和牌資格上的特殊限制」，這是所有規則模組共通的中立
 * 預設；有更細分和牌可用性概念的規則模組（例如日麻的自摸限定、無役、未達最低翻符）另外提供各自的
 * 命名字串，client 端依 namespaced ID 映射顯示文字，查不到時安全 fallback 顯示原始字串（同
 * [DiscardReadinessAnalysisDto.statusIndicatorId] 與 `RoundInfoLine`／`PublicPlayerIndicator` 的
 * 既有慣例）。
 */
@Serializable
data class WaitingTileAvailabilityDto(
    val tileAssetKey: String,
    val remainingCount: Int,
    val winAvailability: String = WIN_AVAILABLE_ID,
)

/** [WaitingTileAvailabilityDto.winAvailability] 的中立預設值，代表這張等待牌沒有和牌資格上的特殊限制。 */
const val WIN_AVAILABLE_ID = "mahjongcraft:win_available"

/** 打出指定實體手牌後的聽牌分析。 */
@Serializable
data class DiscardReadinessAnalysisDto(
    val discardTileId: String,
    val waitingTiles: List<WaitingTileAvailabilityDto>,
    val statusIndicatorId: String? = null,
)

/** 開局準備輸入供呈現層顯示的受控網路表示。 */
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
 * 只傳給取得決策權玩家的決策提示。
 *
 * 所有牌張 ID 都是該玩家已知的實體手牌；分析結果只包含自身與公開資訊，不包含暗手或牌山內容。
 *
 * @property ruleModuleId 這局採用的規則模組 ID，決定動作與狀態的用語；規則未知時為 null，呈現層改用中立預設。
 */
@Serializable
data class PlayerDecisionPromptDto(
    val decisionKey: String,
    val ruleModuleId: String? = null,
    val actions: List<PlayerDecisionActionDto> = emptyList(),
    val triggerTileAssetKey: String? = null,
    val triggerPlayerId: String? = null,
    val triggerPlayerName: String? = null,
    val triggerPlayerRelation: DecisionPlayerRelationDto? = null,
    val triggerActionId: String? = null,
    val preparation: RoundPreparationPromptDto? = null,
    val discardAnalyses: List<DiscardReadinessAnalysisDto> = emptyList(),
)

/** 客戶端提交 prompt 選擇時使用的受控操作種類。 */
@Serializable
enum class PlayerDecisionSelectionKindDto {
    ACTION,
    PREPARATION_CONFIRM,
    PREPARATION_CHOICE,
    PREPARATION_TILES,

    /**
     * 玩家明確進入「需要選超過一張牌」的實體牌選取模式（`tileSelection`／`preparation` 的
     * `maxCount > 1`）——只有這種情境才需要通知伺服器生成確認面板呈現；`maxCount == 1` 維持右鍵
     * 合法牌直接自動送出，不使用這個種類。[PlayerDecisionSelectionDto.token] 為 `null` 代表
     * preparation 的 `TileSelection`，非 `null` 代表帶 `tileSelection` 的動作候選。
     */
    BEGIN_TILE_SELECTION,
}

/** 客戶端提交的權威候選 token。 */
@Serializable
data class PlayerDecisionSelectionDto(
    val gameId: String,
    val decisionKey: String,
    val kind: PlayerDecisionSelectionKindDto,
    val token: String? = null,
    val tileIds: List<String> = emptyList(),
    val submissionId: String = "",
)

/** 最終決策提交的伺服器權威處理結果。 */
@Serializable
data class PlayerDecisionSubmissionResultDto(
    val gameId: String,
    val decisionKey: String,
    val submissionId: String,
    val result: PlayerDecisionSubmissionResultKindDto,
)

/** 最終決策提交可能得到的權威結果種類。 */
@Serializable
enum class PlayerDecisionSubmissionResultKindDto {
    /** 命令已由對局流程接受；client 等待後續 timer 更新清除或替換 prompt。 */
    ACCEPTED,

    /** 提交內容無效或當下無法執行；目前 prompt 若仍有效，client 可恢復操作。 */
    REJECTED,

    /** Decision key 或對局已過期；client 不得用這個回覆復活舊 prompt。 */
    STALE,
}
