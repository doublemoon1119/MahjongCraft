package com.doublemoon1119.mahjongcraft.platform.fabric.client.automatic

import com.doublemoon1119.mahjongcraft.flow.common.time.MonotonicClock
import com.doublemoon1119.mahjongcraft.flow.network.dto.message.AutomaticControlSnapshotDto
import com.doublemoon1119.mahjongcraft.flow.network.dto.message.AutomaticControlUpdateRequestDto
import com.doublemoon1119.mahjongcraft.flow.network.dto.message.AutomaticControlUpdateResultDto
import com.doublemoon1119.mahjongcraft.platform.fabric.client.state.ClientAutomaticControlStateStore
import com.doublemoon1119.mahjongcraft.platform.fabric.logging.mahjongCraftLogger
import kotlin.time.Duration.Companion.seconds
import kotlin.uuid.Uuid

/**
 * 集中管理本局自動操作更新的 request、pending 與 ACK 配對。
 *
 * 這個服務不預測自動操作是否會執行；已確認狀態仍以 [ClientAutomaticControlStateStore] 為唯一來源。
 * 送出後超過 [CONFIRMATION_TIMEOUT] 沒有收到回覆時，要求伺服器重新送出一次權威快照，依快照判斷請求有沒有生效；
 * 重新同步再等 [RESYNC_LIMIT] 仍沒有快照時結束等待並回報無法確認，不重複要求。
 *
 * @property stateStore 本局自動操作權威狀態儲存。
 * @property requestSender 本局更新與重新同步請求傳送器。
 * @property clock 判斷請求等待回覆多久的客戶端時間。
 */
class ClientAutomaticControlUpdateCoordinator(
    private val stateStore: ClientAutomaticControlStateStore,
    private val requestSender: ClientAutomaticControlRequestSender,
    private val clock: MonotonicClock,
) {
    /** 記錄要求送不出去的 logger。 */
    private val logger = mahjongCraftLogger(ClientAutomaticControlUpdateCoordinator::class)

    /** 配對成功的完成事件序號；清除對局狀態時不歸零。 */
    private var completionSequence = 0L

    /** 依完成順序保存、可由請求識別碼取出的有限結果集合。 */
    private val completions = linkedMapOf<String, ClientAutomaticControlCompletion>()

    /** 目前待處理請求的等待狀態；與 store 的待處理請求識別碼不同時視為不存在。 */
    private var wait: PendingWait? = null

    /** 目前已確認的個人權威快照。 */
    fun snapshot(): AutomaticControlSnapshotDto? = stateStore.snapshot()

    /** 目前仍等待伺服器回覆的請求。 */
    fun pendingRequest(): AutomaticControlUpdateRequestDto? = stateStore.pendingRequest()

    /**
     * 判斷指定請求是否因為等不到回覆而正在重新同步。
     *
     * @param requestId 要查詢的請求識別碼。
     * @return 該請求仍待處理且已要求重新同步時為 true。
     */
    fun isResyncing(requestId: String): Boolean = currentWait()?.takeIf { it.requestId == requestId }?.resyncRequestedAtMillis != null

    /**
     * 以 [enabledControlIds] 完整替換本人本局控制集合。
     *
     * 只有通過本地快照與支援集合驗證的請求才會被保存為 pending 並送出。
     *
     * @param enabledControlIds 本人本局要啟用的完整控制項集合。
     * @return 請求送出或本機驗證失敗的結果；送出不代表伺服器已接受。
     */
    fun submit(enabledControlIds: Set<String>): ClientAutomaticControlSubmitResult {
        if (stateStore.pendingRequest() != null) return ClientAutomaticControlSubmitResult.Pending
        val snapshot = stateStore.snapshot() ?: return ClientAutomaticControlSubmitResult.Unavailable
        val unsupported = enabledControlIds - snapshot.supportedControlIds
        if (unsupported.isNotEmpty()) return ClientAutomaticControlSubmitResult.Unsupported(unsupported)
        val request = AutomaticControlUpdateRequestDto(
            requestId = Uuid.random().toString(),
            gameId = snapshot.gameId,
            expectedRevision = snapshot.revision,
            enabledControlIds = enabledControlIds,
        )
        check(stateStore.trySetPendingRequest(request)) { "Pending request changed during client submission" }
        try {
            requestSender.send(request)
        } catch (exception: RuntimeException) {
            check(stateStore.discardPendingRequest(request.requestId)) { "Failed request was no longer pending" }
            logger.warn(
                "Automatic control update could not be sent: requestId={}, gameId={}, expectedRevision={}",
                request.requestId,
                request.gameId,
                request.expectedRevision,
                exception,
            )
            return ClientAutomaticControlSubmitResult.SendFailed(exception)
        }
        wait = PendingWait(request.requestId, submittedAtMillis = clock.nowMillis())
        return ClientAutomaticControlSubmitResult.Submitted(request)
    }

    /**
     * 套用伺服器發布的個人權威快照；正在重新同步時，用來判斷待處理請求有沒有生效。
     *
     * 只有同一局、版本不低於目前已知狀態的快照才算重新同步的回應，版本相同代表狀態沒有改變；較舊的快照不結束等待。
     * 判斷依據是套用後的最新權威狀態：版本沒有前進代表請求沒有生效；版本前進且內容與請求相同代表請求已生效；
     * 版本前進但內容不同代表已被其他變更取代。
     *
     * @param snapshot 收到的權威快照。
     * @return 狀態儲存接受快照時為 true。
     */
    fun applySnapshot(snapshot: AutomaticControlSnapshotDto): Boolean {
        val pending = stateStore.pendingRequest()
        val known = stateStore.snapshot()
        val resyncResponse = pending != null &&
            currentWait()?.resyncRequestedAtMillis != null &&
            pending.gameId == snapshot.gameId &&
            (known?.gameId != snapshot.gameId || snapshot.revision >= known.revision)
        val accepted = stateStore.applySnapshot(snapshot)
        if (resyncResponse) {
            val request = checkNotNull(pending)
            val authority = checkNotNull(stateStore.snapshot())
            val result = when {
                authority.revision <= request.expectedRevision -> ClientAutomaticControlResultKind.NOT_APPLIED
                authority.enabledControlIds == request.enabledControlIds -> ClientAutomaticControlResultKind.ACCEPTED
                else -> ClientAutomaticControlResultKind.STALE
            }
            complete(request, result)
        }
        return accepted
    }

    /**
     * 配對並套用 server 更新結果。
     *
     * 回傳值表示結果是否屬於目前 pending request；無關或延遲結果不會建立 completion。
     *
     * @param result 收到的伺服器更新結果。
     * @return 結果與待處理請求配對並已保存完成事件時為 true。
     */
    fun applyResult(result: AutomaticControlUpdateResultDto): Boolean {
        if (!stateStore.applyResult(result)) return false
        wait = null
        record(
            requestId = result.requestId,
            gameId = result.gameId,
            result = result.result.toClientResultKind(),
        )
        return true
    }

    /** 每個客戶端 tick 檢查待處理請求是否等太久，依序要求重新同步或結束等待。 */
    fun checkTimeout() {
        val pending = stateStore.pendingRequest() ?: return
        val current = currentWait() ?: return
        val now = clock.nowMillis()
        val resyncRequestedAt = current.resyncRequestedAtMillis
        if (resyncRequestedAt == null) {
            if (now - current.submittedAtMillis < CONFIRMATION_TIMEOUT.inWholeMilliseconds) return
            current.resyncRequestedAtMillis = now
            try {
                requestSender.requestSnapshot()
            } catch (exception: RuntimeException) {
                logger.warn("Automatic control resync could not be requested: requestId={}", pending.requestId, exception)
            }
            return
        }
        if (now - resyncRequestedAt < RESYNC_LIMIT.inWholeMilliseconds) return
        complete(pending, ClientAutomaticControlResultKind.UNCONFIRMED)
    }

    /**
     * 取出並移除指定請求的完成結果。
     *
     * @param requestId 要取出的請求識別碼。
     * @return 保存的完成結果；尚未完成、已被取出或已超過保留上限時為 null。
     */
    fun takeCompletion(requestId: String): ClientAutomaticControlCompletion? = completions.remove(requestId)

    /** 清除目前對局的權威快照、pending request 與已完成結果。 */
    fun clear() {
        stateStore.clear()
        completions.clear()
        wait = null
    }

    /**
     * 清除指定桌子的自動操作狀態；其他桌子的待確認要求與完成結果不受影響。
     *
     * @param tableId 已移除的桌子／對局 UUID。
     */
    fun clearTable(tableId: Uuid) {
        stateStore.clearTable(tableId)
        completions.entries.removeIf { (_, completion) -> completion.gameId == tableId.toString() }
    }

    /**
     * 以本機判斷的結果結束待處理請求。
     *
     * @param request 要結束的待處理請求。
     * @param result 本機判斷的結果。
     */
    private fun complete(request: AutomaticControlUpdateRequestDto, result: ClientAutomaticControlResultKind) {
        check(stateStore.discardPendingRequest(request.requestId)) { "Completed request was no longer pending" }
        wait = null
        record(requestId = request.requestId, gameId = request.gameId, result = result)
    }

    /**
     * 保存一筆完成結果，附上目前的權威快照。
     *
     * @param requestId 完成的請求識別碼。
     * @param gameId 請求所屬的對局識別碼。
     * @param result 請求最後的結果。
     */
    private fun record(requestId: String, gameId: String, result: ClientAutomaticControlResultKind) {
        completionSequence += 1L
        completions[requestId] = ClientAutomaticControlCompletion(
            sequence = completionSequence,
            requestId = requestId,
            gameId = gameId,
            result = result,
            authoritativeSnapshot = stateStore.snapshot(),
        )
        while (completions.size > MAX_RETAINED_COMPLETIONS) {
            completions.remove(completions.keys.first())
        }
    }

    /** 屬於目前待處理請求的等待狀態；待處理請求已被清除或取代時為 null。 */
    private fun currentWait(): PendingWait? = wait?.takeIf { it.requestId == stateStore.pendingRequest()?.requestId }

    /**
     * 一筆待處理請求的等待時間。
     *
     * @property requestId 等待中的請求識別碼。
     * @property submittedAtMillis 送出時的客戶端時間。
     * @property resyncRequestedAtMillis 要求重新同步時的客戶端時間；尚未要求時為 null。
     */
    private class PendingWait(
        val requestId: String,
        val submittedAtMillis: Long,
        var resyncRequestedAtMillis: Long? = null,
    )

    internal companion object {
        /** 防止已離開的提交者未取回結果時無限制累積。 */
        const val MAX_RETAINED_COMPLETIONS = 16

        /** 送出後等待回覆的時間；超過後要求重新同步。 */
        val CONFIRMATION_TIMEOUT = 10.seconds

        /** 要求重新同步後等待快照的時間；超過後結束等待並回報無法確認。 */
        val RESYNC_LIMIT = 10.seconds
    }
}
