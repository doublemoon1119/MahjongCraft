package com.doublemoon1119.mahjongcraft.platform.fabric.server.game

import com.doublemoon1119.mahjongcraft.platform.minecraft.decision.PlayerDecisionSubmissionResultKindDto
import kotlinx.coroutines.CancellationException

/**
 * 將 final submission 的所有正常結果與非取消例外收斂成單一 ACK 結果。
 *
 * @param onFailure 非取消例外發生時的通知回呼。
 * @param process 執行提交並取得結果的掛起操作。
 * @return 提交結果；操作發生非取消例外時為 [PlayerDecisionSubmissionResultKindDto.REJECTED]。
 */
internal suspend fun resolveFinalDecisionSubmission(
    onFailure: (Throwable) -> Unit = {},
    process: suspend () -> PlayerDecisionSubmissionResultKindDto,
): PlayerDecisionSubmissionResultKindDto = try {
    process()
} catch (cancellation: CancellationException) {
    throw cancellation
} catch (throwable: Throwable) {
    onFailure(throwable)
    PlayerDecisionSubmissionResultKindDto.REJECTED
}
