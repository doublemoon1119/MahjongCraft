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

    /** 依 [mode] 更新 [controlId]。 */
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

    /** 取回 [requestId] 的 ACK，並解析該控制的最終已確認狀態。 */
    fun takeCompletion(requestId: String, controlId: String): ClientAutomaticControlCommandCompletion? {
        val completion = updateCoordinator.takeCompletion(requestId) ?: return null
        return ClientAutomaticControlCommandCompletion(
            result = completion.result.result,
            enabled = completion.authoritativeSnapshot?.enabledControlIds?.contains(controlId),
        )
    }

    /** 目前是否仍等待 [requestId] 的 ACK。 */
    fun isPending(requestId: String): Boolean = updateCoordinator.pendingRequest()?.requestId == requestId

    /** 更新永久自動理牌偏好。 */
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

    /** 依明確模式或反轉模式計算目標狀態。 */
    private fun resolveEnabled(current: Boolean, mode: AutomaticControlCommandMode): Boolean = when (mode) {
        AutomaticControlCommandMode.ON -> true
        AutomaticControlCommandMode.OFF -> false
        AutomaticControlCommandMode.TOGGLE -> !current
    }
}
