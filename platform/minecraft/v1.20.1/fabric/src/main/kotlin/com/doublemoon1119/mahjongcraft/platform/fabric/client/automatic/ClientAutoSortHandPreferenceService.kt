package com.doublemoon1119.mahjongcraft.platform.fabric.client.automatic

import com.doublemoon1119.mahjongcraft.platform.fabric.client.config.MahjongClientConfigState
import com.doublemoon1119.mahjongcraft.platform.fabric.client.config.MahjongClientConfigStore
import com.doublemoon1119.mahjongcraft.platform.fabric.client.config.MahjongClientConfigUpdateResult
import com.doublemoon1119.mahjongcraft.platform.fabric.logging.mahjongCraftLogger
import org.koin.core.annotation.Single

/**
 * 統一管理永久自動整理手牌偏好的原子保存與同步順序。
 *
 * @property configStore 讀取與保存本機設定的來源。
 * @property sender 將已保存偏好送往伺服器的元件。
 */
@Single
class ClientAutoSortHandPreferenceService(
    private val configStore: MahjongClientConfigStore,
    private val sender: ClientAutoSortHandPreferenceSender,
) {
    /** 目前本機已保存的偏好。 */
    fun current(): Boolean = configStore.current.autoSortHandEnabled

    /** 將偏好切換為目前相反狀態。 */
    fun toggle(): ClientAutoSortHandPreferenceUpdateResult = set(!current())

    /**
     * 先保存偏好，再送出玩家主動變更通知；相同偏好仍可能重試先前失敗的同步。
     *
     * @param enabled 要保存並同步的自動理牌偏好。
     * @return 保存或同步的結果，包含未變更及失敗情況。
     */
    fun set(enabled: Boolean): ClientAutoSortHandPreferenceUpdateResult {
        if (enabled == current()) {
            if (!syncRetryPending) return ClientAutoSortHandPreferenceUpdateResult.Unchanged(enabled)
            return sendUserChange(enabled)
        }
        return when (val result = configStore.setAutoSortHandEnabled(enabled)) {
            is MahjongClientConfigUpdateResult.Failure -> ClientAutoSortHandPreferenceUpdateResult.SaveFailed(result)
            is MahjongClientConfigUpdateResult.Success -> sendUserChange(enabled)
        }
    }

    /**
     * 保存完整設定草稿，並在已連線且需要時同步自動理牌偏好；同步失敗可由下一次呼叫重試。
     *
     * @param config 要保存的完整設定草稿。
     * @param connected 是否已連線至伺服器；false 時只保存本機設定並清除同步重試標記。
     * @return 保存或偏好同步的結果。
     */
    fun saveDraft(config: MahjongClientConfigState, connected: Boolean): ClientAutoSortHandPreferenceUpdateResult {
        val previous = current()
        val needsSave = configStore.current != config
        if (needsSave) {
            when (val result = configStore.save(config)) {
                is MahjongClientConfigUpdateResult.Failure -> return ClientAutoSortHandPreferenceUpdateResult.SaveFailed(result)
                is MahjongClientConfigUpdateResult.Success -> Unit
            }
        }
        if (!connected) {
            syncRetryPending = false
            return ClientAutoSortHandPreferenceUpdateResult.Unchanged(config.autoSortHandEnabled)
        }
        if (previous == config.autoSortHandEnabled && !syncRetryPending) {
            return ClientAutoSortHandPreferenceUpdateResult.Unchanged(config.autoSortHandEnabled)
        }
        return sendUserChange(config.autoSortHandEnabled)
    }

    /** 前次已保存但尚未成功同步的偏好仍須重送。 */
    val hasPendingSync: Boolean
        get() = syncRetryPending

    /** 偏好已保存，但玩家主動變更封包尚未成功送出。 */
    private var syncRetryPending = false

    /** 記錄偏好同步送不出去的 logger。 */
    private val logger = mahjongCraftLogger(ClientAutoSortHandPreferenceService::class)

    /**
     * 目前這一連串 USER_CHANGE 失敗是否已記錄過警告；任何一次偏好成功送出（含 RESTORE）後重設。
     *
     * 與 [syncRetryPending] 分開：重試標記會跨連線保留，警告則在偏好成功送達後的下一次失敗重新記錄。
     */
    internal var syncFailureReported = false
        private set

    /**
     * 送出玩家主動變更通知；成功後清除重試標記，連續失敗只在第一次記錄警告。
     *
     * @param enabled 已保存且要同步的偏好。
     * @return 已同步或需要重試的結果。
     */
    private fun sendUserChange(enabled: Boolean): ClientAutoSortHandPreferenceUpdateResult = try {
        sender.sendUserChange(enabled)
        syncRetryPending = false
        syncFailureReported = false
        ClientAutoSortHandPreferenceUpdateResult.Updated(enabled)
    } catch (exception: RuntimeException) {
        if (!syncFailureReported) {
            logger.warn("Auto sort hand preference change could not be sent: enabled={}", enabled, exception)
            syncFailureReported = true
        }
        syncRetryPending = true
        ClientAutoSortHandPreferenceUpdateResult.SyncFailed(enabled, exception)
    }

    /** 將本機已保存值以 RESTORE 語意同步至剛加入的伺服器；送不出去時記錄警告，伺服器維持預設偏好。 */
    fun restoreToServer() {
        val enabled = current()
        try {
            sender.sendRestore(enabled)
            syncFailureReported = false
        } catch (exception: RuntimeException) {
            logger.warn("Auto sort hand preference restore could not be sent: enabled={}", enabled, exception)
        }
    }
}
