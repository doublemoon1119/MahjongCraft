package com.doublemoon1119.mahjongcraft.platform.fabric.client.game

import com.doublemoon1119.mahjongcraft.platform.minecraft.decision.PlayerDecisionActionDto
import com.doublemoon1119.mahjongcraft.platform.minecraft.decision.PlayerDecisionPromptDto
import com.doublemoon1119.mahjongcraft.platform.minecraft.decision.PlayerDecisionSelectionKindDto
import com.doublemoon1119.mahjongcraft.platform.minecraft.decision.RoundPreparationPromptDto

/**
 * 以實體手牌進行選牌時的本機狀態機。
 *
 * 同一時間只會有一種進行中的選牌情境：開局準備（preparation）或帶選牌需求的動作（例如立直宣告後選捨牌），
 * 兩者互斥。另外還有「玩家已明確跳過特殊動作、接下來右鍵手牌就是普通出牌」這個模式。
 *
 * 本類別只操作 DTO 與牌張 ID 字串，不送封包、不碰畫面；轉換方法以 [Outcome] 與 [BeginRequest] 告訴呼叫端
 * 該不該消化這次互動、該不該送出選擇。動作選牌是否仍然有效由 [ClientDecisionPromptStore] 決定，呼叫端以
 * `isActionSelectionActive` 傳入。
 */
internal class DecisionTileSelectionState {
    /** 進行中 preparation 選牌的 decision key。 */
    private var preparationDecisionKey: String? = null

    /** 進行中 preparation 選牌已選取的牌張 ID。 */
    private val selectedPreparationTileIds = linkedSetOf<String>()

    /** 進行中動作選牌的動作 token。 */
    private var actionToken: String? = null

    /** 進行中動作選牌已選取的牌張 ID。 */
    private val selectedActionTileIds = linkedSetOf<String>()

    /** 已允許右鍵手牌直接出牌的 decision key。 */
    private var directDiscardDecisionKey: String? = null

    /** 進行中動作選牌的 token；沒有進行中的動作選牌時為 `null`。 */
    val activeActionToken: String? get() = actionToken

    /**
     * 進入 preparation 選牌模式。
     *
     * 只有 `maxCount > 1` 需要伺服器生成確認面板；`maxCount == 1` 維持右鍵合法牌直接送出。
     *
     * @param prompt 最新的權威決策提示。
     * @return 需要建立多選確認面板時的請求；單選或沒有選牌限制時為 null。
     */
    fun beginPreparation(prompt: PlayerDecisionPromptDto): BeginRequest? {
        selectedPreparationTileIds.clear()
        preparationDecisionKey = prompt.decisionKey
        return BeginRequest(token = null).takeIf { (prompt.preparationConstraint()?.maxCount ?: 1) > 1 }
    }

    /**
     * 進入動作選牌模式；確認面板的判斷同 [beginPreparation]。
     *
     * @param action 欲執行且需要選牌的動作。
     * @return 需要建立多選確認面板時的請求；單選時為 null。
     */
    fun beginAction(action: PlayerDecisionActionDto): BeginRequest? {
        selectedActionTileIds.clear()
        actionToken = action.token
        return BeginRequest(action.token).takeIf { (action.tileSelection?.maxCount ?: 1) > 1 }
    }

    /**
     * 玩家明確跳過特殊動作後，允許右鍵手牌直接出牌。
     *
     * @param decisionKey 允許直接出牌的權威決策鍵。
     */
    fun beginDirectDiscard(decisionKey: String) {
        directDiscardDecisionKey = decisionKey
    }

    /**
     * 切換一張手牌的選取狀態。
     *
     * 已選滿 `maxCount` 時，右鍵其他尚未選取的牌不會有反應，必須先取消一張；非合法候選牌同樣沒有反應。
     * 兩種情況都仍然消化這次互動，避免右鍵穿透成普通出牌。只有 `maxCount == 1` 才選中即送出——
     * `maxCount > 1` 一律要右鍵確認面板，即使剛好選滿也一樣，見 [confirm]。
     *
     * @param prompt 最新的權威決策提示。
     * @param tileId 本次互動的牌張識別碼。
     * @param isActionSelectionActive 動作選牌是否仍有效。
     * @return 是否消化互動，以及單選完成時應送出的選擇。
     */
    fun toggle(prompt: PlayerDecisionPromptDto, tileId: String, isActionSelectionActive: Boolean): Outcome {
        if (preparationDecisionKey == prompt.decisionKey) {
            prompt.preparationConstraint()?.let { constraint ->
                return toggleIn(
                    selected = selectedPreparationTileIds,
                    constraint = constraint,
                    tileId = tileId,
                    kind = PlayerDecisionSelectionKindDto.PREPARATION_TILES,
                    token = null,
                )
            }
        }
        val token = actionToken
        if (token != null && isActionSelectionActive) {
            prompt.actionConstraint(token)?.let { constraint ->
                return toggleIn(
                    selected = selectedActionTileIds,
                    constraint = constraint,
                    tileId = tileId,
                    kind = PlayerDecisionSelectionKindDto.ACTION,
                    token = token,
                )
            }
        }
        return Outcome.Ignored
    }

    /**
     * 右鍵多選確認面板。
     *
     * 已選數量落在合法範圍內才送出；不在範圍內時仍然消化互動並維持選取狀態，讓玩家繼續選。
     *
     * @param prompt 最新的權威決策提示。
     * @param isActionSelectionActive 動作選牌是否仍有效。
     * @return 是否消化互動，以及已選數量合法時應送出的選擇。
     */
    fun confirm(prompt: PlayerDecisionPromptDto, isActionSelectionActive: Boolean): Outcome {
        if (progress(prompt, isActionSelectionActive) == null) return Outcome.Ignored
        if (preparationDecisionKey == prompt.decisionKey) {
            val constraint = prompt.preparationConstraint() ?: return Outcome.Consumed(null)
            return Outcome.Consumed(
                finalSelectionIfComplete(
                    selected = selectedPreparationTileIds,
                    constraint = constraint,
                    kind = PlayerDecisionSelectionKindDto.PREPARATION_TILES,
                    token = null,
                ),
            )
        }
        val token = actionToken
        if (token != null && isActionSelectionActive) {
            val constraint = prompt.actionConstraint(token) ?: return Outcome.Consumed(null)
            return Outcome.Consumed(
                finalSelectionIfComplete(
                    selected = selectedActionTileIds,
                    constraint = constraint,
                    kind = PlayerDecisionSelectionKindDto.ACTION,
                    token = token,
                ),
            )
        }
        return Outcome.Ignored
    }

    /**
     * 進行中選牌情境的已選數量與合法範圍；沒有進行中的選牌時為 `null`。
     *
     * @param prompt 最新的權威決策提示。
     * @param isActionSelectionActive 動作選牌是否仍有效。
     * @return 有效選牌的進度；沒有有效情境時為 null。
     */
    fun progress(prompt: PlayerDecisionPromptDto, isActionSelectionActive: Boolean): Progress? {
        if (preparationDecisionKey == prompt.decisionKey) {
            val constraint = prompt.preparationConstraint() ?: return null
            return Progress(selectedPreparationTileIds.size, constraint.range)
        }
        val token = actionToken
        if (token != null && isActionSelectionActive) {
            val constraint = prompt.actionConstraint(token) ?: return null
            return Progress(selectedActionTileIds.size, constraint.range)
        }
        return null
    }

    /** 目前應套用本地選取發光的牌張 ID。 */
    fun highlightedTileIds(): Set<String> = when {
        preparationDecisionKey != null -> selectedPreparationTileIds
        actionToken != null -> selectedActionTileIds
        else -> emptySet()
    }

    /**
     * 右鍵手牌是否可以直接當成普通出牌傳下去。
     *
     * @param decisionKey 本次互動所屬的權威決策鍵。
     * @return 此決策已啟用直接出牌模式時為 true。
     */
    fun allowsDirectDiscard(decisionKey: String): Boolean = directDiscardDecisionKey == decisionKey

    /**
     * 玩家是否已明確進入任何一種實體牌操作模式。
     *
     * @param decisionKey 目前權威決策鍵。
     * @param isActionSelectionActive 動作選牌是否仍有效。
     * @return 動作選牌、開局準備或直接出牌模式生效時為 true。
     */
    fun isPhysicalSelectionActive(decisionKey: String, isActionSelectionActive: Boolean): Boolean = isActionSelectionActive ||
        preparationDecisionKey == decisionKey ||
        directDiscardDecisionKey == decisionKey

    /**
     * 依目前權威 prompt 丟棄已失效的選牌情境。
     *
     * @param decisionKey 最新的權威決策鍵。
     * @param isActionSelectionActive 動作選牌是否仍有效。
     */
    fun syncTo(decisionKey: String, isActionSelectionActive: Boolean) {
        if (preparationDecisionKey != null && preparationDecisionKey != decisionKey) {
            selectedPreparationTileIds.clear()
            preparationDecisionKey = null
        }
        if (actionToken != null && !isActionSelectionActive) {
            selectedActionTileIds.clear()
            actionToken = null
        }
    }

    /** 丟棄兩種選牌情境，保留直接出牌模式。 */
    fun clearSelections() {
        selectedPreparationTileIds.clear()
        preparationDecisionKey = null
        selectedActionTileIds.clear()
        actionToken = null
    }

    /** 丟棄全部只屬於上一個決策的本機互動狀態。 */
    fun clear() {
        clearSelections()
        directDiscardDecisionKey = null
    }

    /**
     * 切換單一牌張，並在只需選一張時產生最終選擇。
     *
     * @param selected 目前已選牌張集合，會直接更新。
     * @param constraint 合法牌張與可選張數。
     * @param tileId 本次互動的牌張識別碼。
     * @param kind 提交的選擇種類。
     * @param token 動作令牌；開局準備時為 null。
     * @return 已消化互動的結果；合法單選完成時攜帶最終選擇。
     */
    private fun toggleIn(
        selected: MutableSet<String>,
        constraint: Constraint,
        tileId: String,
        kind: PlayerDecisionSelectionKindDto,
        token: String?,
    ): Outcome {
        if (tileId !in constraint.eligibleTileIds) return Outcome.Consumed(null)
        if (!selected.remove(tileId) && selected.size < constraint.maxCount) {
            selected.add(tileId)
        }
        val autoSubmit = constraint.maxCount == 1 && selected.size == 1
        return Outcome.Consumed(if (autoSubmit) FinalSelection(kind, token, selected.toList()) else null)
    }

    /**
     * 已選數量落在合法範圍內時的最終選擇。
     *
     * @param selected 目前已選牌張。
     * @param constraint 合法牌張與可選張數。
     * @param kind 提交的選擇種類。
     * @param token 動作令牌；開局準備時為 null。
     * @return 已選數量合法時的最終選擇，否則為 null。
     */
    private fun finalSelectionIfComplete(
        selected: Set<String>,
        constraint: Constraint,
        kind: PlayerDecisionSelectionKindDto,
        token: String?,
    ): FinalSelection? = FinalSelection(kind, token, selected.toList()).takeIf { selected.size in constraint.range }

    /** 目前 prompt 的 preparation 選牌需求；不是選牌型態的 preparation 為 `null`。 */
    private fun PlayerDecisionPromptDto.preparationConstraint(): Constraint? = (preparation as? RoundPreparationPromptDto.TileSelection)
        ?.let { Constraint(it.eligibleTileIds, it.minCount, it.maxCount) }

    /**
     * 指定動作 token 的選牌需求；找不到動作或該動作不需選牌時為 `null`。
     *
     * @param token 欲查詢的動作令牌。
     * @return 選牌限制；找不到動作或不需選牌時為 null。
     */
    private fun PlayerDecisionPromptDto.actionConstraint(token: String): Constraint? = actions.firstOrNull { it.token == token }
        ?.tileSelection
        ?.let { Constraint(it.eligibleTileIds, it.minCount, it.maxCount) }

    /**
     * 開局準備與動作共用的選牌限制。
     *
     * @property eligibleTileIds 可選牌張識別碼。
     * @property minCount 至少須選取的張數。
     * @property maxCount 最多可選取的張數。
     */
    private data class Constraint(
        val eligibleTileIds: List<String>,
        val minCount: Int,
        val maxCount: Int,
    ) {
        /** 合法的已選數量範圍。 */
        val range: IntRange get() = minCount..maxCount
    }

    /**
     * 需要通知伺服器生成確認面板的開始選牌請求。
     *
     * @property token 動作令牌；開局準備時為 null。
     */
    data class BeginRequest(val token: String?)

    /**
     * 送出後必須等待伺服器確認的最終選擇。
     *
     * @property kind 本次提交的選擇種類。
     * @property token 動作令牌；開局準備時為 null。
     * @property tileIds 依選取順序排列的牌張識別碼。
     */
    data class FinalSelection(
        val kind: PlayerDecisionSelectionKindDto,
        val token: String?,
        val tileIds: List<String>,
    )

    /** 一次實體牌互動的處理結果。 */
    sealed interface Outcome {
        /** 不屬於任何進行中的選牌情境；呼叫端應讓事件繼續往下傳遞。 */
        data object Ignored : Outcome

        /**
         * 互動已被選牌情境消化。
         *
         * @property selection 呼叫端應送出的最終選擇；尚未完成或候選無效時為 null。
         */
        data class Consumed(val selection: FinalSelection?) : Outcome
    }

    /**
     * 進行中選牌情境的進度。
     *
     * @property selectedCount 已選取的牌張數。
     * @property validRange 可提交的張數範圍，包含兩端。
     */
    data class Progress(val selectedCount: Int, val validRange: IntRange)
}
