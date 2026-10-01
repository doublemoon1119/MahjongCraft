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
        !includeAiMatches && game.tableState.players.any { it.isAi } -> HistoryRecordingDecision.EXCLUDED_AI
        else -> HistoryRecordingDecision.RECORDING
    }
}

/** 每場固定的歷史記錄結果，名稱亦為持久化識別值。 */
enum class HistoryRecordingDecision {
    /** 允許依權威交易追加事件。 */
    RECORDING,

    /** 開局時總開關關閉，整場不建立事件。 */
    EXCLUDED_CONFIG_DISABLED,

    /** 開局包含被政策排除的 AI 座位。 */
    EXCLUDED_AI,

    /** 已開始的對局沒有可確認的開局記錄，不從中途建立歷史。 */
    EXCLUDED_NO_OPENING,

    /** 原本正在記錄，因總開關關閉而永久停止該場追加。 */
    STOPPED_CONFIG_DISABLED,

    /** 歷史儲存端容量不足或不可用，該場次保留缺口並停止追加。 */
    STOPPED_STORAGE_UNAVAILABLE,

    /** 已由封存清理標記為不可再追加的場次。 */
    STOPPED_PRUNED,

    /** 隔離歷史來源已中止，保留部分事件而不重啟生成。 */
    STOPPED_TRANSFER_INTERRUPTED,
}
