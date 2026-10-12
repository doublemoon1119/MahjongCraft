package com.doublemoon1119.mahjongcraft.platform.fabric.client.automatic

import com.doublemoon1119.mahjongcraft.platform.minecraft.automatic.BuiltInMinecraftAutomaticControlIds
import org.koin.core.annotation.Single

/**
 * 將通用 client 指令意圖轉成永久偏好更新或本局權威更新。
 *
 * @property autoSortHandPreferenceService 永久自動理牌偏好服務。
 * @property updateCoordinator 本局自動操作更新協調器。
 */
@Single
class ClientAutomaticControlCommandService(
    private val autoSortHandPreferenceService: ClientAutoSortHandPreferenceService,
    private val updateCoordinator: ClientAutomaticControlUpdateCoordinator,
) {
    /** 目前可供補全的控制 ID；永久自動理牌固定存在。 */
    fun availableControlIds(): Set<String> = buildSet {
        add(BuiltInMinecraftAutomaticControlIds.AUTO_SORT_HAND)
        addAll(updateCoordinator.snapshot()?.supportedControlIds.orEmpty())
    }

    /**
     * 依指定模式更新永久偏好或本局控制項。
     *
     * @param controlId 要更新的控制項識別碼。
     * @param mode 明確開關或反轉目前狀態的模式。
     * @return 本機保存或本局請求送出結果；本局提交仍須等待伺服器確認。
     */
    fun execute(controlId: String, mode: AutomaticControlCommandMode): ClientAutomaticControlCommandResult {
        if (controlId == BuiltInMinecraftAutomaticControlIds.AUTO_SORT_HAND) {
            return updateAutoSortHand(mode)
        }
        val snapshot = updateCoordinator.snapshot() ?: return ClientAutomaticControlCommandResult.Unavailable
        if (controlId !in snapshot.supportedControlIds) {
            return ClientAutomaticControlCommandResult.Unsupported(controlId)
        }
        val previousEnabled = controlId in snapshot.enabledControlIds
        val enabled = resolveEnabled(previousEnabled, mode)
        if (enabled == previousEnabled) {
            return ClientAutomaticControlCommandResult.Unchanged(controlId, enabled)
        }
        val updated = snapshot.enabledControlIds.toMutableSet().apply {
            if (enabled) add(controlId) else remove(controlId)
        }
        return when (val result = updateCoordinator.submit(updated)) {
            is ClientAutomaticControlSubmitResult.Submitted -> ClientAutomaticControlCommandResult.Submitted(
                requestId = result.request.requestId,
                controlId = controlId,
                previousEnabled = previousEnabled,
                enabled = enabled,
            )
            ClientAutomaticControlSubmitResult.Pending -> ClientAutomaticControlCommandResult.Pending
            ClientAutomaticControlSubmitResult.Unavailable -> ClientAutomaticControlCommandResult.Unavailable
            is ClientAutomaticControlSubmitResult.Unsupported -> ClientAutomaticControlCommandResult.Unsupported(controlId)
            is ClientAutomaticControlSubmitResult.SendFailed -> ClientAutomaticControlCommandResult.RoundControlSendFailed
        }
    }

    /**
     * 取出指定請求的回覆，並解析該控制項的已確認狀態。
     *
     * @param requestId 要取出的請求識別碼。
     * @param controlId 要從回覆快照查詢的控制項識別碼。
     * @return 該請求的完成結果；尚無回覆或已被取出時為 null。
     */
    fun takeCompletion(requestId: String, controlId: String): ClientAutomaticControlCommandCompletion? {
        val completion = updateCoordinator.takeCompletion(requestId) ?: return null
        return ClientAutomaticControlCommandCompletion(
            result = completion.result.result,
            enabled = completion.authoritativeSnapshot?.enabledControlIds?.contains(controlId),
        )
    }

    /**
     * 檢查是否仍等待指定請求的回覆。
     *
     * @param requestId 要查詢的請求識別碼。
     * @return 目前待處理請求與識別碼相符時為 true。
     */
    fun isPending(requestId: String): Boolean = updateCoordinator.pendingRequest()?.requestId == requestId

    /**
     * 更新永久自動理牌偏好，並轉換成指令處理結果。
     *
     * @param mode 明確開關或反轉目前偏好的模式。
     * @return 偏好保存與同步的結果。
     */
    private fun updateAutoSortHand(mode: AutomaticControlCommandMode): ClientAutomaticControlCommandResult {
        val previousEnabled = autoSortHandPreferenceService.current()
        val enabled = resolveEnabled(previousEnabled, mode)
        return when (val result = autoSortHandPreferenceService.set(enabled)) {
            is ClientAutoSortHandPreferenceUpdateResult.Updated -> ClientAutomaticControlCommandResult.Updated(
                BuiltInMinecraftAutomaticControlIds.AUTO_SORT_HAND,
                previousEnabled,
                result.enabled,
            )
            is ClientAutoSortHandPreferenceUpdateResult.Unchanged -> ClientAutomaticControlCommandResult.Unchanged(
                BuiltInMinecraftAutomaticControlIds.AUTO_SORT_HAND,
                result.enabled,
            )
            is ClientAutoSortHandPreferenceUpdateResult.SaveFailed -> ClientAutomaticControlCommandResult.SaveFailed(result.failure)
            is ClientAutoSortHandPreferenceUpdateResult.SyncFailed -> ClientAutomaticControlCommandResult.PreferenceSyncFailed(
                result.enabled,
            )
        }
    }

    /**
     * 依明確模式或反轉模式計算目標狀態。
     *
     * @param current 目前已確認狀態。
     * @param mode 要套用的開關模式。
     * @return 要求的新狀態。
     */
    private fun resolveEnabled(current: Boolean, mode: AutomaticControlCommandMode): Boolean = when (mode) {
        AutomaticControlCommandMode.ON -> true
        AutomaticControlCommandMode.OFF -> false
        AutomaticControlCommandMode.TOGGLE -> !current
    }
}
