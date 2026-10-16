package com.doublemoon1119.mahjongcraft.platform.fabric.client.game

import com.doublemoon1119.mahjongcraft.flow.common.time.MonotonicClock
import com.doublemoon1119.mahjongcraft.platform.minecraft.decision.PlayerDecisionSubmissionResultDto
import com.doublemoon1119.mahjongcraft.platform.minecraft.decision.PlayerDecisionSubmissionResultKindDto
import kotlin.time.Duration.Companion.seconds

/**
 * 純本機追蹤單一最終決策提交，隔離 ACK 亂序與重複提交。
 *
 * 送出後一直沒有收到 ACK 時，依權威決策更新判斷能否解除鎖定：伺服器在玩家的選擇被接受後就不再把他列為決策者，
 * 因此逾時時仍持續收到同一決策的更新，代表伺服器沒有記錄這次選擇，可以讓玩家重新選擇。收到 ACK 後不再逾時，
 * 等權威決策消失或換成另一個決策才解除。
 *
 * @property clock 客戶端的單調時間。
 */
internal class DecisionSubmissionTracker(
    private val clock: MonotonicClock,
) {
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
        pending = PendingSubmission(gameId, decisionKey, submissionId, submittedAtMillis = clock.nowMillis())
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
            -> {
                current.acknowledged = true
                AcknowledgementEffect.KEPT_LOCKED
            }
        }
    }

    /**
     * 權威決策消失或配對鍵改變時清除舊提交；同鍵更新不會提早解鎖，只記錄收到更新的時間。
     *
     * @param decisionKey 最新權威決策的配對鍵；沒有決策時為 null。
     * @param updateSequence 權威決策的更新序號；每收到一次更新就遞增。
     */
    fun applyAuthoritativeDecision(decisionKey: String?, updateSequence: Long) {
        val current = pending ?: return
        if (decisionKey == null || current.decisionKey != decisionKey) {
            pending = null
            return
        }
        if (current.lastSeenUpdateSequence != null && current.lastSeenUpdateSequence != updateSequence) {
            current.lastUpdateReceivedAtMillis = clock.nowMillis()
        }
        current.lastSeenUpdateSequence = updateSequence
    }

    /**
     * 檢查沒有收到 ACK 的提交是否已經等太久。
     *
     * 等待超過 [CONFIRMATION_TIMEOUT] 後，若最近 [RECENT_UPDATE_WINDOW] 內仍收到同一決策的更新就解除鎖定；
     * 收不到任何更新時先回報正在重新同步，再等 [RESYNC_LIMIT] 後一律解除，不無限等待。
     *
     * @return 這次檢查對提交鎖的影響。
     */
    fun checkTimeout(): TimeoutEffect {
        val current = pending ?: return TimeoutEffect.NONE
        if (current.acknowledged) return TimeoutEffect.NONE
        val now = clock.nowMillis()
        val waited = now - current.submittedAtMillis
        if (waited < CONFIRMATION_TIMEOUT.inWholeMilliseconds) return TimeoutEffect.NONE
        val serverStillWaiting = current.lastUpdateReceivedAtMillis
            ?.let { now - it <= RECENT_UPDATE_WINDOW.inWholeMilliseconds } == true
        if (serverStillWaiting || waited >= (CONFIRMATION_TIMEOUT + RESYNC_LIMIT).inWholeMilliseconds) {
            pending = null
            return TimeoutEffect.UNLOCKED
        }
        if (current.resyncReported) return TimeoutEffect.NONE
        current.resyncReported = true
        return TimeoutEffect.RESYNCING
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
     * 一筆最終提交的穩定識別與等待狀態。
     *
     * @property gameId 提交所屬的遊戲識別碼。
     * @property decisionKey 權威決策的配對鍵。
     * @property submissionId 本次提交的識別碼。
     * @property submittedAtMillis 送出時的客戶端時間。
     * @property acknowledged 是否已收到接受或過期的 ACK。
     * @property lastSeenUpdateSequence 最近看到的權威決策更新序號；尚未看到時為 null。
     * @property lastUpdateReceivedAtMillis 送出後最近一次收到同一決策更新的時間；尚未收到時為 null。
     * @property resyncReported 是否已回報正在重新同步。
     */
    private class PendingSubmission(
        val gameId: String,
        val decisionKey: String,
        val submissionId: String,
        val submittedAtMillis: Long,
        var acknowledged: Boolean = false,
        var lastSeenUpdateSequence: Long? = null,
        var lastUpdateReceivedAtMillis: Long? = null,
        var resyncReported: Boolean = false,
    )

    internal companion object {
        /** 送出後等待 ACK 的時間；超過後才開始依權威更新判斷能否重試。 */
        val CONFIRMATION_TIMEOUT = 10.seconds

        /** 判斷伺服器仍在等待這位玩家時，同一決策更新必須在多久之內收到；伺服器約每秒重送一次。 */
        val RECENT_UPDATE_WINDOW = 2.seconds

        /** 收不到任何權威更新時，逾時後再等待多久就解除鎖定。 */
        val RESYNC_LIMIT = 10.seconds
    }
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

/** 提交逾時檢查對目前 client 提交鎖的影響。 */
internal enum class TimeoutEffect {
    /** 沒有變化。 */
    NONE,

    /** 尚未收到確認，正在等待權威更新判斷能否重試；每筆提交只回報一次。 */
    RESYNCING,

    /** 沒有收到確認，已解除鎖定讓玩家重新選擇。 */
    UNLOCKED,
}
