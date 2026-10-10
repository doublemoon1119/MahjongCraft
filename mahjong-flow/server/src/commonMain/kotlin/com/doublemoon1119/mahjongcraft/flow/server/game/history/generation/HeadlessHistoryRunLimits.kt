package com.doublemoon1119.mahjongcraft.flow.server.game.history.generation

import kotlinx.coroutines.flow.flow
import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds

/**
 * 單場生成與逐批交付的有界限制。
 *
 * @property maxSteps 最多允許的權威流程步數。
 * @property maxDuration 單場包含保存等待的最大時間。
 * @property maxTransferBatchSize 單批最多事件數；不得切斷權威交易。
 * @property transferWaitTimeout 收集端每批保存及確認的最大時間。
 */
data class HeadlessHistoryRunLimits(
    val maxSteps: Int = 10_000,
    val maxDuration: Duration = 5.minutes,
    val maxTransferBatchSize: Int = 64,
    val transferWaitTimeout: Duration = 60.seconds,
) {
    init {
        require(maxSteps > 0) { "Maximum runner steps must be positive" }
        require(maxDuration.isPositive()) { "Maximum runner duration must be positive" }
        require(maxTransferBatchSize in 1..64) { "Transfer batch size must be between 1 and 64" }
        require(transferWaitTimeout.isPositive()) { "Transfer wait timeout must be positive" }
    }
}
