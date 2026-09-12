package com.doublemoon1119.mahjongcraft.platform.fabric.server.game

import com.doublemoon1119.mahjongcraft.flow.network.dto.message.PlayerDecisionSubmissionResultKindDto
import kotlinx.coroutines.CancellationException

/** 將 final submission 的所有正常結果與非取消例外收斂成單一 ACK 結果。 */
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
