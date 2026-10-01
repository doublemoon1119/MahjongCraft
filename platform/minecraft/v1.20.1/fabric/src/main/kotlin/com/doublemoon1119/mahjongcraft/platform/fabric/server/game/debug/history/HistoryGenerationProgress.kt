package com.doublemoon1119.mahjongcraft.platform.fabric.server.game.debug.history

import com.doublemoon1119.mahjongcraft.platform.fabric.server.history.HistoryDiskUsage
import kotlin.time.Duration

/**
 * 一批隔離對局的生成及正式保存進度，不包含玩家或牌面資料。
 *
 * @property scenarioId 此批選用的場長情境；牌局沿用正式隨機初始化。
 * @property requested 要求生成的完整場數。
 * @property generated 已由 Flow 完整完成的場數。
 * @property archived 已證實封存的場數；後續被清理仍保留此成功事實。
 * @property pruned 已由 tombstone 證實清理的場數。
 * @property pending 尚未確認封存或清理的場數。
 * @property failed 生成或保存失敗的場數。
 * @property running 此批是否仍在執行。
 * @property cancelRequested 是否要求目前場結束後停止。
 * @property failure 發生停止或失敗時的安全分類，不包含原始例外。
 * @property elapsed 已經過的單調時間。
 * @property diskBefore 生成前的實際主檔及附屬檔大小；未知時為 null。
 * @property diskAfter 最後成功查詢的實際磁碟大小；未知時為 null。
 * @property replayBytes 已確認的批次 Replay payload 位元組合計，不是磁碟釋放量。
 * @property largestReplayBytes 已確認的最大單場 Replay payload 位元組數。
 * @property replaySampleCount 已量得 Replay payload 大小的場數，作為平均值的分母。
 */
internal data class HistoryGenerationProgress(
    val scenarioId: String,
    val requested: Int,
    val generated: Int = 0,
    val archived: Int = 0,
    val pruned: Int = 0,
    val pending: Int = 0,
    val failed: Int = 0,
    val running: Boolean = true,
    val cancelRequested: Boolean = false,
    val failure: HistoryGenerationFailure? = null,
    val elapsed: Duration = Duration.ZERO,
    val diskBefore: HistoryDiskUsage? = null,
    val diskAfter: HistoryDiskUsage? = null,
    val replayBytes: Long = 0L,
    val largestReplayBytes: Long = 0L,
    val replaySampleCount: Int = 0,
)

/** 生成器回覆使用的固定安全分類，原始例外只記錄於伺服器 log。 */
internal enum class HistoryGenerationFailure {
    /** 設定不允許生成的全 AI 對局。 */
    POLICY,

    /** 儲存端斷線或容量停止。 */
    STORAGE,

    /** 限定時間內未完成生成或保存。 */
    TIMEOUT,

    /** 原存檔 session 已結束。 */
    SESSION,

    /** 對局或保存流程發生其他驗證錯誤。 */
    VALIDATION,
}
