package com.doublemoon1119.mahjongcraft.platform.fabric.server.game

import com.doublemoon1119.mahjongcraft.flow.common.error.ExtensionFailurePolicy
import com.doublemoon1119.mahjongcraft.platform.minecraft.decision.PlayerDecisionSubmissionResultKindDto
import kotlinx.coroutines.CancellationException

/**
 * 將 final submission 的所有正常結果與可以隔離的失敗收斂成單一 ACK 結果。
 *
 * @param failurePolicy 判斷提交中丟出的失敗能否收斂為拒絕；協程取消與無法隔離的失敗原樣往外傳。
 * @param onFailure 可以隔離的失敗發生時的通知回呼。
 * @param process 執行提交並取得結果的掛起操作。
 * @return 提交結果；操作發生可以隔離的失敗時為 [PlayerDecisionSubmissionResultKindDto.REJECTED]。
 */
internal suspend fun resolveFinalDecisionSubmission(
    failurePolicy: ExtensionFailurePolicy,
    onFailure: (Throwable) -> Unit = {},
    process: suspend () -> PlayerDecisionSubmissionResultKindDto,
): PlayerDecisionSubmissionResultKindDto = try {
    process()
} catch (cancellation: CancellationException) {
    throw cancellation
} catch (throwable: Throwable) {
    if (!failurePolicy.isIsolatable(throwable)) throw throwable
    onFailure(throwable)
    PlayerDecisionSubmissionResultKindDto.REJECTED
}
