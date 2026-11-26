package com.doublemoon1119.mahjongcraft.flow.persistence.format.history.replay

/**
 * Replay 讀取作業的資源統計。
 *
 * @property workUnits 消耗的工作單位數。
 * @property expandedNodes 展開處理的節點總數。
 * @property expandedStringBytes 展開字串的 UTF-8 位元組總數。
 * @property transactionsRebuilt 重建的交易數。
 */
data class ReplayReadDiagnostics(
    val workUnits: Long,
    val expandedNodes: Long,
    val expandedStringBytes: Long,
    val transactionsRebuilt: Int,
)
