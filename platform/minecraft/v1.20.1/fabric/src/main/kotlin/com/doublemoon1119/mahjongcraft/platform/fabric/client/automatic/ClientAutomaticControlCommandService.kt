package com.doublemoon1119.mahjongcraft.platform.fabric.client.automatic

import com.doublemoon1119.mahjongcraft.flow.network.dto.message.AutomaticControlUpdateResultKindDto
import com.doublemoon1119.mahjongcraft.platform.fabric.client.config.MahjongClientConfigUpdateResult
import com.doublemoon1119.mahjongcraft.platform.minecraft.automatic.BuiltInMinecraftAutomaticControlIds
import org.koin.core.annotation.Single

/** Client 指令要求套用的自動操作狀態。 */
enum class AutomaticControlCommandMode {
    /** 明確開啟。 */
    ON,

    /** 明確關閉。 */
    OFF,

    /** 依已確認狀態反轉。 */
    TOGGLE,
}

/** Client 指令在本地送出階段取得的結果。 */
sealed interface ClientAutomaticControlCommandResult {
    /** 永久自動理牌已保存並同步。 */
    data class Updated(
        /** 控制 ID。 */
        val controlId: String,
        /** 更新前狀態。 */
        val previousEnabled: Boolean,
        /** 更新後狀態。 */
        val enabled: Boolean,
    ) : ClientAutomaticControlCommandResult

    /** 控制已是要求的狀態，未產生寫入或網路請求。 */
    data class Unchanged(
        /** 控制 ID。 */
        val controlId: String,
        /** 目前狀態。 */
        val enabled: Boolean,
    ) : ClientAutomaticControlCommandResult

    /** 本局更新已送出，必須等待相同 [requestId] 的 ACK。 */
    data class Submitted(
        /** 請求 ID。 */
        val requestId: String,
        /** 控制 ID。 */
        val controlId: String,
        /** 已確認的更新前狀態。 */
        val previousEnabled: Boolean,
        /** 要求的狀態。 */
        val enabled: Boolean,
    ) : ClientAutomaticControlCommandResult

    /** 已有另一筆本局更新等待回覆。 */
    data object Pending : ClientAutomaticControlCommandResult

    /** 目前沒有本局權威快照。 */
    data object Unavailable : ClientAutomaticControlCommandResult

    /** 指定 ID 不是永久控制，也不受目前規則支援。 */
    data class Unsupported(
        /** 不受支援的控制 ID。 */
        val controlId: String,
    ) : ClientAutomaticControlCommandResult

    /** 本機設定保存失敗。 */
    data class SaveFailed(
        /** 設定存取層回傳的失敗。 */
        val failure: MahjongClientConfigUpdateResult.Failure,
    ) : ClientAutomaticControlCommandResult

    /** 本機已保存，但偏好同步封包送出失敗。 */
    data class PreferenceSyncFailed(
        /** 已保存的狀態。 */
        val enabled: Boolean,
    ) : ClientAutomaticControlCommandResult

    /** 本局更新封包送出失敗。 */
    data object RoundControlSendFailed : ClientAutomaticControlCommandResult
}

/** 一筆本局自動操作指令收到 ACK 後的結果。 */
data class ClientAutomaticControlCommandCompletion(
    /** server 處理結果。 */
    val result: AutomaticControlUpdateResultKindDto,
    /** ACK 套用後已確認的控制狀態；沒有可用快照時為 `null`。 */
    val enabled: Boolean?,
)

/** 將通用 client 指令意圖轉成永久偏好更新或本局權威更新。 */
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
