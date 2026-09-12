package com.doublemoon1119.mahjongcraft.platform.fabric.client.game

import com.doublemoon1119.mahjongcraft.flow.network.dto.message.PlayerDecisionSubmissionResultDto
import com.doublemoon1119.mahjongcraft.flow.network.dto.message.PlayerDecisionSubmissionResultKindDto

/** 純本機追蹤單一最終決策提交，隔離 ACK 亂序與重複提交。 */
internal class DecisionSubmissionTracker {
    /** 目前尚未由權威 decision 更新清除的提交。 */
    private var pending: PendingSubmission? = null

    /** 沒有既有提交時開始追蹤 [submissionId]；已有提交則回傳 false。 */
    fun begin(gameId: String, decisionKey: String, submissionId: String): Boolean {
        if (pending != null) return false
        pending = PendingSubmission(gameId, decisionKey, submissionId)
        return true
    }

    /** 套用可配對的 ACK，回傳 client 應維持鎖定、解除鎖定或忽略。 */
    fun acknowledge(result: PlayerDecisionSubmissionResultDto): AcknowledgementEffect {
        val current = pending ?: return AcknowledgementEffect.IGNORED
        if (
            result.gameId != current.gameId ||
            result.decisionKey != current.decisionKey ||
            result.submissionId != current.submissionId
        ) {
            return AcknowledgementEffect.IGNORED
        }
        return when (result.result) {
            PlayerDecisionSubmissionResultKindDto.REJECTED -> {
                pending = null
                AcknowledgementEffect.UNLOCKED
            }
            PlayerDecisionSubmissionResultKindDto.ACCEPTED,
            PlayerDecisionSubmissionResultKindDto.STALE,
            -> AcknowledgementEffect.KEPT_LOCKED
        }
    }

    /** 權威 prompt 消失或換 key 時清除舊提交；同 key 的週期更新不應提早解鎖。 */
    fun applyAuthoritativeDecision(decisionKey: String?) {
        if (decisionKey == null || pending?.decisionKey != decisionKey) pending = null
    }

    /** 清除離線或世界切換前的所有提交狀態。 */
    fun clear() {
        pending = null
    }

    /** 是否有等待中的提交。 */
    fun isPending(): Boolean = pending != null

    /** 是否正等待指定 [decisionKey] 的提交。 */
    fun isPending(decisionKey: String): Boolean = pending?.decisionKey == decisionKey

    /** 一筆最終提交的穩定識別。 */
    private data class PendingSubmission(val gameId: String, val decisionKey: String, val submissionId: String)
}

/** Server ACK 對目前 client 提交鎖的影響。 */
internal enum class AcknowledgementEffect {
    /** ACK 不屬於目前提交。 */
    IGNORED,

    /** ACK 已配對，但仍須等待權威 decision 更新。 */
    KEPT_LOCKED,

    /** 同一 decision 的提交遭拒，允許玩家原地重試。 */
    UNLOCKED,
}
