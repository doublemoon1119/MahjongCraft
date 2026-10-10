package com.doublemoon1119.mahjongcraft.flow.common.game.history

import com.doublemoon1119.mahjongcraft.flow.common.game.model.Game

/**
 * 新對局的歷史記錄資格政策；已開始的對局不因 AI 篩選改動重新分類。
 *
 * @property enabled 是否允許新對局記錄歷史。
 * @property includeAiMatches 是否記錄開局座位包含 AI 的對局。
 */
data class HistoryRecordingPolicy(
    val enabled: Boolean = false,
    val includeAiMatches: Boolean = true,
) {
    /**
     * 依開局座位建立整場固定的記錄資格。
     *
     * @param game 剛建立的對局。
     * @return 記錄或排除的原因。
     */
    fun decide(game: Game): HistoryRecordingDecision = when {
        !enabled -> HistoryRecordingDecision.EXCLUDED_CONFIG_DISABLED
        !includeAiMatches && game.aiPlayerIds.isNotEmpty() -> HistoryRecordingDecision.EXCLUDED_AI
        else -> HistoryRecordingDecision.RECORDING
    }
}
