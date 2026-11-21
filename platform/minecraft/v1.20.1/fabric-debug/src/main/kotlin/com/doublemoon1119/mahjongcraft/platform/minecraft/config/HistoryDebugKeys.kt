package com.doublemoon1119.mahjongcraft.platform.minecraft.config

/** 正式歷史生成 debug 指令共用的本地化翻譯鍵。 */
object HistoryDebugKeys {
    /** 尚未建立生成批次。 */
    const val NO_BATCH: String = "mahjongcraft.debug.history.no_batch"

    /** 已有生成工作執行中。 */
    const val BUSY: String = "mahjongcraft.debug.history.busy"

    /** 生成情境不存在或不合法。 */
    const val INVALID_SCENARIO: String = "mahjongcraft.debug.history.invalid_scenario"

    /** 政策拒絕生成。 */
    const val POLICY_REJECTED: String = "mahjongcraft.debug.history.policy_rejected"

    /** 歷史儲存端不可用。 */
    const val STORAGE_UNAVAILABLE: String = "mahjongcraft.debug.history.storage_unavailable"

    /** 已要求取消目前批次。 */
    const val CANCEL_REQUESTED: String = "mahjongcraft.debug.history.cancel_requested"

    /** 已開始生成批次。 */
    const val STARTED: String = "mahjongcraft.debug.history.started"

    /** 生成批次進度標題。 */
    const val PROGRESS: String = "mahjongcraft.debug.history.progress"

    /** 生成情境欄位。 */
    const val SCENARIO: String = "mahjongcraft.debug.history.scenario"

    /** 要求生成的場數欄位。 */
    const val REQUESTED: String = "mahjongcraft.debug.history.requested"

    /** 已完成 Flow 的場數欄位。 */
    const val GENERATED: String = "mahjongcraft.debug.history.generated"

    /** 已證實封存的場數欄位。 */
    const val ARCHIVED: String = "mahjongcraft.debug.history.archived"

    /** 已證實清理的場數欄位。 */
    const val PRUNED: String = "mahjongcraft.debug.history.pruned"

    /** 尚待確認保存的場數欄位。 */
    const val PENDING: String = "mahjongcraft.debug.history.pending"

    /** 失敗場數欄位。 */
    const val FAILED: String = "mahjongcraft.debug.history.failed"

    /** 經過時間欄位。 */
    const val ELAPSED_SECONDS: String = "mahjongcraft.debug.history.elapsed_seconds"

    /** 取消狀態欄位。 */
    const val CANCEL_STATE: String = "mahjongcraft.debug.history.cancel_state"

    /** 生成前磁碟欄位。 */
    const val DISK_BEFORE: String = "mahjongcraft.debug.history.disk_before"

    /** 生成後磁碟欄位。 */
    const val DISK_AFTER: String = "mahjongcraft.debug.history.disk_after"

    /** Replay 總量欄位。 */
    const val REPLAY_TOTAL: String = "mahjongcraft.debug.history.replay_total"

    /** Replay 平均值欄位。 */
    const val REPLAY_AVERAGE: String = "mahjongcraft.debug.history.replay_average"

    /** Replay 最大值欄位。 */
    const val REPLAY_MAXIMUM: String = "mahjongcraft.debug.history.replay_maximum"

    /** 生成仍在執行中的狀態文字。 */
    const val RUNNING: String = "mahjongcraft.debug.history.running"

    /** 生成已完成的狀態文字。 */
    const val COMPLETED: String = "mahjongcraft.debug.history.completed"

    /** 資格政策拒絕的分類文字。 */
    const val FAILURE_POLICY: String = "mahjongcraft.debug.history.failure.policy"

    /** 儲存端不可用的分類文字。 */
    const val FAILURE_STORAGE: String = "mahjongcraft.debug.history.failure.storage"

    /** 超過時間限制的分類文字。 */
    const val FAILURE_TIMEOUT: String = "mahjongcraft.debug.history.failure.timeout"

    /** 存檔 session 已切換的分類文字。 */
    const val FAILURE_SESSION: String = "mahjongcraft.debug.history.failure.session"

    /** 流程驗證失敗的分類文字。 */
    const val FAILURE_VALIDATION: String = "mahjongcraft.debug.history.failure.validation"
}
