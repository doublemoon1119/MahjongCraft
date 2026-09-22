package com.doublemoon1119.mahjongcraft.platform.fabric.client.automatic

import com.doublemoon1119.mahjongcraft.flow.network.dto.message.AutomaticControlSnapshotDto
import com.doublemoon1119.mahjongcraft.flow.network.dto.message.AutomaticControlUpdateRequestDto
import com.doublemoon1119.mahjongcraft.flow.network.dto.message.AutomaticControlUpdateResultDto
import com.doublemoon1119.mahjongcraft.platform.fabric.client.state.ClientAutomaticControlStateStore
import org.koin.core.annotation.Single
import kotlin.uuid.Uuid

/** 送出本局自動操作更新時，在 client 端即可判定的結果。 */
sealed interface ClientAutomaticControlSubmitResult {
    /** 請求已送出並等待伺服器回覆。 */
    data class Submitted(
        /** 已送出的完整請求。 */
        val request: AutomaticControlUpdateRequestDto,
    ) : ClientAutomaticControlSubmitResult

    /** 已有另一筆請求等待回覆。 */
    data object Pending : ClientAutomaticControlSubmitResult

    /** 目前沒有可更新的本局權威快照。 */
    data object Unavailable : ClientAutomaticControlSubmitResult

    /** 草稿包含目前規則未宣告支援的控制 ID。 */
    data class Unsupported(
        /** 不受支援的控制 ID。 */
        val controlIds: Set<String>,
    ) : ClientAutomaticControlSubmitResult

    /** 封包未能交給 Fabric networking，pending 狀態已回復。 */
    data class SendFailed(
        /** 傳送時發生的錯誤。 */
        val cause: RuntimeException,
    ) : ClientAutomaticControlSubmitResult
}

/** 一筆已配對請求的伺服器完成結果。 */
data class ClientAutomaticControlCompletion(
    /** client 端單調遞增的完成序號。 */
    val sequence: Long,
    /** 伺服器回傳的權威結果。 */
    val result: AutomaticControlUpdateResultDto,
    /** 套用結果後 store 中可用的最新權威快照。 */
    val authoritativeSnapshot: AutomaticControlSnapshotDto?,
)

/**
 * 集中管理本局自動操作更新的 request、pending 與 ACK 配對。
 *
 * 這個服務不預測自動操作是否會執行；已確認狀態仍以 [ClientAutomaticControlStateStore] 為唯一來源。
 */
@Single
class ClientAutomaticControlUpdateCoordinator(
    private val stateStore: ClientAutomaticControlStateStore,
    private val requestSender: ClientAutomaticControlRequestSender,
) {
    private var completionSequence = 0L
    private val completions = linkedMapOf<String, ClientAutomaticControlCompletion>()

    /** 目前已確認的個人權威快照。 */
    fun snapshot(): AutomaticControlSnapshotDto? = stateStore.snapshot()

    /** 目前仍等待伺服器回覆的請求。 */
    fun pendingRequest(): AutomaticControlUpdateRequestDto? = stateStore.pendingRequest()

    /**
     * 以 [enabledControlIds] 完整替換本人本局控制集合。
     *
     * 只有通過本地快照與支援集合驗證的請求才會被保存為 pending 並送出。
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
            return ClientAutomaticControlSubmitResult.SendFailed(exception)
        }
        return ClientAutomaticControlSubmitResult.Submitted(request)
    }

    /** 套用 server 發布的個人權威快照。 */
    fun applySnapshot(snapshot: AutomaticControlSnapshotDto): Boolean = stateStore.applySnapshot(snapshot)

    /**
     * 配對並套用 server 更新結果。
     *
     * 回傳值表示結果是否屬於目前 pending request；無關或延遲結果不會建立 completion。
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

    /** 取出並移除 [requestId] 對應的完成結果；其他提交者不會誤取這筆結果。 */
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
