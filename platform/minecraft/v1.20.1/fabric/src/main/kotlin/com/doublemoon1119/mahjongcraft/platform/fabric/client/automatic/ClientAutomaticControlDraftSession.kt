package com.doublemoon1119.mahjongcraft.platform.fabric.client.automatic

import com.doublemoon1119.mahjongcraft.flow.network.dto.message.AutomaticControlSnapshotDto
import com.doublemoon1119.mahjongcraft.flow.network.dto.message.AutomaticControlUpdateRequestDto
import com.doublemoon1119.mahjongcraft.flow.network.dto.message.AutomaticControlUpdateResultKindDto

/** 設定入口目前可觀察的本局自動操作草稿狀態。 */
data class ClientAutomaticControlDraftState(
    /** 開啟或最近確認時採用的權威基準。 */
    val baseline: AutomaticControlSnapshotDto?,
    /** 使用者尚未確認的完整啟用集合。 */
    val enabledControlIds: Set<String>,
    /** 目前等待 ACK 的 request ID。 */
    val pendingRequestId: String?,
    /** 草稿依據的權威 revision 已過期。 */
    val stale: Boolean,
    /** 最近一次未接受的 server 結果。 */
    val failure: AutomaticControlUpdateResultKindDto?,
    /** 目前沒有可編輯的本局權威狀態。 */
    val unavailable: Boolean,
) {
    /** 草稿是否與權威基準不同。 */
    val dirty: Boolean
        get() = baseline != null && enabledControlIds != baseline.enabledControlIds

    /** 是否正等待 server 確認。 */
    val pending: Boolean
        get() = pendingRequestId != null
}

/** 套用一筆 ACK 後，設定入口需要採取的後續動作。 */
data class ClientAutomaticControlDraftCompletion(
    /** ACK 是否接受草稿。 */
    val accepted: Boolean,
    /** 接受後是否應完成先前記住的關閉意圖。 */
    val closeAfterAcceptance: Boolean,
)

/**
 * 維護一個設定入口的本局自動操作草稿。
 *
 * 這個 session 不送封包，也不讀取 Minecraft widget；呼叫端以 coordinator 的 snapshot、request 與 completion 驅動狀態。
 */
class ClientAutomaticControlDraftSession(initialSnapshot: AutomaticControlSnapshotDto?) {
    private var latestAuthority = initialSnapshot
    private var currentBaseline = initialSnapshot
    private var currentEnabledControlIds = initialSnapshot?.enabledControlIds.orEmpty()
    private var currentPendingRequest: AutomaticControlUpdateRequestDto? = null
    private var currentStale = false
    private var currentFailure: AutomaticControlUpdateResultKindDto? = null
    private var closeRequested = false

    /** 取得目前不可變狀態。 */
    fun state(): ClientAutomaticControlDraftState = ClientAutomaticControlDraftState(
        baseline = currentBaseline,
        enabledControlIds = currentEnabledControlIds,
        pendingRequestId = currentPendingRequest?.requestId,
        stale = currentStale,
        failure = currentFailure,
        unavailable = currentBaseline == null,
    )

    /** 以 [enabledControlIds] 取代草稿；pending、stale 或 unavailable 時拒絕編輯。 */
    fun replaceDraft(enabledControlIds: Set<String>): Boolean {
        val baseline = currentBaseline ?: return false
        if (currentPendingRequest != null || currentStale) return false
        if (!baseline.supportedControlIds.containsAll(enabledControlIds)) return false
        currentEnabledControlIds = enabledControlIds
        currentFailure = null
        return true
    }

    /** 將目前規則支援的本局控制全部設為關閉。 */
    fun reset(): Boolean = replaceDraft(emptySet())

    /** 放棄未提交草稿，採用目前已知的最新權威快照。 */
    fun undo(): Boolean {
        if (currentPendingRequest != null) return false
        val authority = latestAuthority ?: return false
        currentBaseline = authority
        currentEnabledControlIds = authority.enabledControlIds
        currentStale = false
        currentFailure = null
        closeRequested = false
        return true
    }

    /** 記錄已送出的 [request] 與成功後是否關閉入口的意圖。 */
    fun markSubmitted(request: AutomaticControlUpdateRequestDto, closeAfterAcceptance: Boolean): Boolean {
        val baseline = currentBaseline ?: return false
        if (currentPendingRequest != null || currentStale) return false
        if (request.gameId != baseline.gameId || request.expectedRevision != baseline.revision) return false
        if (request.enabledControlIds != currentEnabledControlIds) return false
        currentPendingRequest = request
        currentFailure = null
        closeRequested = closeAfterAcceptance
        return true
    }

    /**
     * 接收新的權威快照。
     *
     * 未修改的草稿直接刷新；已修改或等待 ACK 時保留輸入，並在快照與原依據不相容時標記 stale。
     */
    fun applySnapshot(snapshot: AutomaticControlSnapshotDto?) {
        if (snapshot == null) {
            clear()
            return
        }
        val baseline = currentBaseline
        if (baseline == null || baseline.gameId != snapshot.gameId) {
            replaceLifecycle(snapshot)
            return
        }
        if (snapshot.revision <= (latestAuthority?.revision ?: Long.MIN_VALUE)) return
        latestAuthority = snapshot
        val pending = currentPendingRequest
        if (pending != null) {
            if (snapshot.revision > pending.expectedRevision && snapshot.enabledControlIds != pending.enabledControlIds) {
                currentStale = true
            }
            return
        }
        if (currentEnabledControlIds != baseline.enabledControlIds) {
            currentStale = true
            return
        }
        currentBaseline = snapshot
        currentEnabledControlIds = snapshot.enabledControlIds
        currentFailure = null
    }

    /** 套用與目前 pending request 相符的 [completion]；不相符時不改變 session。 */
    fun applyCompletion(completion: ClientAutomaticControlCompletion): ClientAutomaticControlDraftCompletion? {
        val pending = currentPendingRequest ?: return null
        if (completion.result.requestId != pending.requestId || completion.result.gameId != pending.gameId) return null
        completion.authoritativeSnapshot
            ?.takeIf { it.gameId == pending.gameId }
            ?.let { latestAuthority = it }
        currentPendingRequest = null
        val accepted = completion.result.result == AutomaticControlUpdateResultKindDto.ACCEPTED
        if (accepted && completion.authoritativeSnapshot?.gameId == pending.gameId) {
            val snapshot = checkNotNull(completion.authoritativeSnapshot)
            currentBaseline = snapshot
            currentEnabledControlIds = snapshot.enabledControlIds
            currentStale = false
            currentFailure = null
        } else {
            currentFailure = completion.result.result
            currentStale = completion.result.result == AutomaticControlUpdateResultKindDto.STALE ||
                latestAuthority?.revision != currentBaseline?.revision
        }
        val shouldClose = accepted && closeRequested
        closeRequested = false
        return ClientAutomaticControlDraftCompletion(accepted, shouldClose)
    }

    /** 清除上一局的基準、草稿與 pending 狀態。 */
    fun clear() {
        latestAuthority = null
        currentBaseline = null
        currentEnabledControlIds = emptySet()
        currentPendingRequest = null
        currentStale = false
        currentFailure = null
        closeRequested = false
    }

    /** 以新對局 [snapshot] 取代整個 session 生命週期。 */
    private fun replaceLifecycle(snapshot: AutomaticControlSnapshotDto) {
        latestAuthority = snapshot
        currentBaseline = snapshot
        currentEnabledControlIds = snapshot.enabledControlIds
        currentPendingRequest = null
        currentStale = false
        currentFailure = null
        closeRequested = false
    }
}
