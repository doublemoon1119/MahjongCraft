package com.doublemoon1119.mahjongcraft.flow.common.concurrency

import kotlinx.coroutines.CoroutineDispatcher

/**
 * 提供流程所需的協程調度器，由執行環境決定實作。
 *
 * 測試可替換調度器，控制工作的排程與執行時機。
 */
interface CoroutineDispatchers {
    /**
     * 預設的協程調度器，適用於 CPU 密集型計算任務。
     */
    val default: CoroutineDispatcher

    /**
     * I/O 密集型任務的協程調度器，例如檔案讀寫或網路請求。
     */
    val io: CoroutineDispatcher

    /**
     * 用於權威狀態操作與流程推進的主執行緒調度器。
     */
    val main: CoroutineDispatcher

    /**
     * 計算 AI 決策的調度器。不在 [main] 上，不同對局的 AI 決策可能在這裡同時計算；整個程式共用同一個，同時使用的執行緒
     * 不超過 [aiDecisionParallelism]。
     */
    val aiDecision: CoroutineDispatcher

    /** [aiDecision] 最多同時使用的執行緒數，至少為 1。 */
    val aiDecisionParallelism: Int
}
