package com.doublemoon1119.mahjongcraft.platform.fabric.client.game

import com.doublemoon1119.mahjongcraft.platform.minecraft.decision.PlayerDecisionSubmissionResultDto
import com.doublemoon1119.mahjongcraft.platform.minecraft.decision.PlayerDecisionSubmissionResultKindDto

/** 純本機追蹤單一最終決策提交，隔離 ACK 亂序與重複提交。 */
internal class DecisionSubmissionTracker {
    /** 目前尚未由權威 decision 更新清除的提交。 */
    private var pending: PendingSubmission? = null

    /**
     * 沒有既有提交時開始追蹤本次決策提交。
     *
     * @param gameId 提交所屬的遊戲識別碼。
     * @param decisionKey 權威決策的配對鍵。
     * @param submissionId 本次提交的識別碼。
     * @return 已開始追蹤時為 true；已有等待中的提交時為 false。
     */
    fun begin(gameId: String, decisionKey: String, submissionId: String): Boolean {
        if (pending != null) return false
        pending = PendingSubmission(gameId, decisionKey, submissionId)
        return true
    }

    /**
     * 套用與目前提交配對的伺服器確認。
     *
     * @param result 伺服器回傳的提交結果。
     * @return 客戶端應維持鎖定、解除鎖定或忽略此確認。
     */
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

    /**
     * 權威決策消失或配對鍵改變時清除舊提交；同鍵更新不會提早解鎖。
     *
     * @param decisionKey 最新權威決策的配對鍵；沒有決策時為 null。
     */
    fun applyAuthoritativeDecision(decisionKey: String?) {
        if (decisionKey == null || pending?.decisionKey != decisionKey) pending = null
    }

    /** 清除離線或世界切換前的所有提交狀態。 */
    fun clear() {
        pending = null
    }

    /** 是否有等待中的提交。 */
    fun isPending(): Boolean = pending != null

    /**
     * 判斷是否正在等待指定決策的提交結果。
     *
     * @param decisionKey 欲比對的決策配對鍵。
     * @return 等待中的提交屬於此決策時為 true。
     */
    fun isPending(decisionKey: String): Boolean = pending?.decisionKey == decisionKey

    /**
     * 一筆最終提交的穩定識別。
     *
     * @property gameId 提交所屬的遊戲識別碼。
     * @property decisionKey 權威決策的配對鍵。
     * @property submissionId 本次提交的識別碼。
     */
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
