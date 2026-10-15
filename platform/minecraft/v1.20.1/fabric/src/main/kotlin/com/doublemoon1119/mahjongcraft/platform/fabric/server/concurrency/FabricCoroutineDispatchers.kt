package com.doublemoon1119.mahjongcraft.platform.fabric.server.concurrency

import com.doublemoon1119.mahjongcraft.flow.common.concurrency.CoroutineDispatchers
import com.doublemoon1119.mahjongcraft.platform.fabric.server.FabricServerHolder
import com.doublemoon1119.mahjongcraft.platform.fabric.server.time.FabricTickMonotonicClock
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers

/**
 * [CoroutineDispatchers] 的 Fabric 實作。[main] 實際綁到伺服器主執行緒，見 [ServerThreadCoroutineDispatcher]；
 * 以具體型別公開，關服流程可直接呼叫它的 [ServerThreadCoroutineDispatcher.drainUntil]。
 *
 * [aiDecision] 是 [Dispatchers.Default] 上限制為 [aiDecisionParallelism] 條執行緒的視圖，初始值為可用核心數減一（至少一條）；
 * 它只限制 AI 同時使用的執行緒數，不保證替伺服器主執行緒保留一個核心。
 *
 * @param serverHolder 提供目前 Fabric server session。
 * @param tickClock 以遊戲 tick 排程延遲工作的時鐘。
 */
class FabricCoroutineDispatchers(serverHolder: FabricServerHolder, tickClock: FabricTickMonotonicClock) : CoroutineDispatchers {
    override val default: CoroutineDispatcher = Dispatchers.Default
    override val io: CoroutineDispatcher = Dispatchers.IO
    override val main: ServerThreadCoroutineDispatcher = ServerThreadCoroutineDispatcher(serverHolder, tickClock)
    override val aiDecisionParallelism: Int = (Runtime.getRuntime().availableProcessors() - 1).coerceAtLeast(1)
    override val aiDecision: CoroutineDispatcher = Dispatchers.Default.limitedParallelism(aiDecisionParallelism, name = "MahjongCraft AI")
}
