package com.doublemoon1119.mahjongcraft.platform.fabric.client.automatic

import com.doublemoon1119.mahjongcraft.flow.network.dto.message.AutomaticControlSnapshotDto
import com.doublemoon1119.mahjongcraft.flow.network.dto.message.AutomaticControlUpdateRequestDto
import com.doublemoon1119.mahjongcraft.flow.network.dto.message.AutomaticControlUpdateResultKindDto

/**
 * 維護一個設定入口的本局自動操作草稿。
 *
 * 這個 session 不送封包，也不讀取 Minecraft widget；呼叫端以 coordinator 的 snapshot、request 與 completion 驅動狀態。
 *
 * @param initialSnapshot 開啟草稿時可用的權威快照；null 表示目前不可編輯。
 */
class ClientAutomaticControlDraftSession(initialSnapshot: AutomaticControlSnapshotDto?) {
    /** 最近收到的權威快照，供復原草稿使用。 */
    private var latestAuthority = initialSnapshot

    /** 目前草稿所依據的權威快照。 */
    private var currentBaseline = initialSnapshot

    /** 草稿中啟用的控制項識別碼。 */
    private var currentEnabledControlIds = initialSnapshot?.enabledControlIds.orEmpty()

    /** 已送出且尚未收到配對結果的請求。 */
    private var currentPendingRequest: AutomaticControlUpdateRequestDto? = null

    /** 草稿依據是否已被較新的權威狀態取代。 */
    private var currentStale = false

    /** 最近一次配對提交失敗的結果種類。 */
    private var currentFailure: AutomaticControlUpdateResultKindDto? = null

    /** 提交成功後是否應關閉編輯入口。 */
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

    /**
     * 取代草稿；等待提交結果、依據已過期、不可編輯或含不支援的控制項時拒絕。
     *
     * @param enabledControlIds 草稿中要啟用的控制項識別碼。
     * @return 草稿已更新時為 true；拒絕編輯時為 false。
     */
    fun replaceDraft(enabledControlIds: Set<String>): Boolean {
        val baseline = currentBaseline ?: return false
        if (currentPendingRequest != null || currentStale) return false
        if (!baseline.supportedControlIds.containsAll(enabledControlIds)) return false
        currentEnabledControlIds = enabledControlIds
        currentFailure = null
        return true
    }

    /**
     * 將草稿中的本局控制全部設為關閉，沿用 [replaceDraft] 的編輯限制。
     *
     * @return 草稿已更新時為 true。
     */
    fun reset(): Boolean = replaceDraft(emptySet())

    /**
     * 放棄未提交草稿，採用目前已知的最新權威快照。
     *
     * @return 已復原時為 true；等待提交結果或沒有權威快照時為 false。
     */
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

    /**
     * 記錄與目前草稿及其依據相符的已送出請求。
     *
     * @param request 呼叫端已送出的更新請求。
     * @param closeAfterAcceptance 請求獲接受後是否關閉編輯入口。
     * @return 已登記時為 true；草稿不可提交或請求與草稿不符時為 false。
     */
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
     *
     * @param snapshot 新權威快照；null 表示目前不可編輯，會清除草稿狀態。
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

    /**
     * 套用與目前待處理請求相符的完成結果；不相符時不改變草稿狀態。
     *
     * @param completion 收到的提交完成事件。
     * @return 配對後的接受與關閉結果；沒有配對請求時為 null。
     */
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

    /**
     * 以新對局的快照取代草稿，並清除前一局的提交狀態。
     *
     * @param snapshot 新對局的權威快照。
     */
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
