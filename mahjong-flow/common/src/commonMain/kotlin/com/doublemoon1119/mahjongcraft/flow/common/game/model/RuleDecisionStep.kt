package com.doublemoon1119.mahjongcraft.flow.common.game.model

import com.doublemoon1119.mahjongcraft.logic.base.NamespacedId
import kotlin.uuid.Uuid

/**
 * 規則要求的一個選擇步驟；Flow 開啟視窗時才為它配發視窗身分。
 *
 * @property stepId 此選擇步驟的完整 namespaced 識別碼。
 * @property stepIndex 此步驟在同一次選擇流程中的零起算索引。
 * @property inputSpecsByPlayerId 各參與玩家可提交的輸入規格；沒有參與者的步驟由 Flow 自動完成。
 */
data class RuleDecisionStep(
    val stepId: String,
    val stepIndex: Int,
    val inputSpecsByPlayerId: Map<Uuid, RuleDecisionInputSpec>,
) {
    init {
        NamespacedId.requireValid(stepId) {
            "Rule decision step ID must be namespaced: $stepId"
        }
        require(stepIndex >= 0) { "Rule decision step index must not be negative" }
    }

    /** 參與此步驟的玩家。 */
    val participantPlayerIds: Set<Uuid> get() = inputSpecsByPlayerId.keys
}
