package com.doublemoon1119.mahjongcraft.platform.fabric.client.automatic

import com.doublemoon1119.mahjongcraft.flow.network.dto.message.AutomaticControlSnapshotDto
import com.doublemoon1119.mahjongcraft.flow.network.dto.message.AutomaticControlUpdateRequestDto
import com.doublemoon1119.mahjongcraft.flow.network.dto.message.AutomaticControlUpdateResultDto
import com.doublemoon1119.mahjongcraft.platform.fabric.client.state.ClientAutomaticControlStateStore
import com.doublemoon1119.mahjongcraft.platform.fabric.logging.mahjongCraftLogger
import org.koin.core.annotation.Single
import kotlin.uuid.Uuid

/**
 * 集中管理本局自動操作更新的 request、pending 與 ACK 配對。
 *
 * 這個服務不預測自動操作是否會執行；已確認狀態仍以 [ClientAutomaticControlStateStore] 為唯一來源。
 *
 * @property stateStore 本局自動操作權威狀態儲存。
 * @property requestSender 本局更新請求傳送器。
 */
@Single
class ClientAutomaticControlUpdateCoordinator(
    private val stateStore: ClientAutomaticControlStateStore,
    private val requestSender: ClientAutomaticControlRequestSender,
) {
    /** 記錄要求送不出去的 logger。 */
    private val logger = mahjongCraftLogger(ClientAutomaticControlUpdateCoordinator::class)

    /** 配對成功的完成事件序號；清除對局狀態時不歸零。 */
    private var completionSequence = 0L

    /** 依完成順序保存、可由請求識別碼取出的有限結果集合。 */
    private val completions = linkedMapOf<String, ClientAutomaticControlCompletion>()

    /** 目前已確認的個人權威快照。 */
    fun snapshot(): AutomaticControlSnapshotDto? = stateStore.snapshot()

    /** 目前仍等待伺服器回覆的請求。 */
    fun pendingRequest(): AutomaticControlUpdateRequestDto? = stateStore.pendingRequest()

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
        return ClientAutomaticControlSubmitResult.Submitted(request)
    }

    /**
     * 套用伺服器發布的個人權威快照。
     *
     * @param snapshot 收到的權威快照。
     * @return 狀態儲存接受快照時為 true。
     */
    fun applySnapshot(snapshot: AutomaticControlSnapshotDto): Boolean = stateStore.applySnapshot(snapshot)

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
        completionSequence += 1L
        completions[result.requestId] = ClientAutomaticControlCompletion(
            sequence = completionSequence,
            result = result,
            authoritativeSnapshot = stateStore.snapshot(),
        )
        while (completions.size > MAX_RETAINED_COMPLETIONS) {
            completions.remove(completions.keys.first())
        }
        return true
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
    }

    private companion object {
        /** 防止已離開的提交者未取回結果時無限制累積。 */
        const val MAX_RETAINED_COMPLETIONS = 16
    }
}
