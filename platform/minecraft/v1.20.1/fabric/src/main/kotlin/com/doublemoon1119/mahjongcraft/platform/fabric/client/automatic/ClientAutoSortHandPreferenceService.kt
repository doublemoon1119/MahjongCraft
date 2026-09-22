package com.doublemoon1119.mahjongcraft.platform.fabric.client.automatic

import com.doublemoon1119.mahjongcraft.platform.fabric.client.config.MahjongClientConfigStore
import com.doublemoon1119.mahjongcraft.platform.fabric.client.config.MahjongClientConfigUpdateResult
import com.doublemoon1119.mahjongcraft.platform.fabric.network.MahjongChannels
import kotlinx.serialization.json.Json
import org.koin.core.annotation.Single

/** 自動整理手牌偏好同步至伺服器時使用的兩種語意。 */
interface ClientAutoSortHandPreferenceSender {
    /** 玩家主動修改偏好，伺服器可立即重新整理目前手牌。 */
    fun sendUserChange(enabled: Boolean)

    /** 加入世界時恢復偏好，不視為本次玩家操作。 */
    fun sendRestore(enabled: Boolean)
}

/** 透過既有 Fabric channels 同步自動整理手牌偏好。 */
@Single(binds = [ClientAutoSortHandPreferenceSender::class])
class FabricClientAutoSortHandPreferenceSender(
    private val json: Json,
) : ClientAutoSortHandPreferenceSender {
    /** 傳送玩家主動變更。 */
    override fun sendUserChange(enabled: Boolean) {
        MahjongChannels.setAutoSortHand.sendToServer(json, enabled)
    }

    /** 傳送加入世界時的偏好恢復。 */
    override fun sendRestore(enabled: Boolean) {
        MahjongChannels.restoreAutoSortHand.sendToServer(json, enabled)
    }
}

/** 自動整理手牌偏好的本機保存與伺服器同步結果。 */
sealed interface ClientAutoSortHandPreferenceUpdateResult {
    /** 本機保存及 USER_CHANGE 同步皆已完成。 */
    data class Updated(
        /** 更新後的狀態。 */
        val enabled: Boolean,
    ) : ClientAutoSortHandPreferenceUpdateResult

    /** 目前已是要求的狀態，沒有重寫檔案或送出封包。 */
    data class Unchanged(
        /** 目前狀態。 */
        val enabled: Boolean,
    ) : ClientAutoSortHandPreferenceUpdateResult

    /** 本機設定保存失敗，未傳送 USER_CHANGE。 */
    data class SaveFailed(
        /** 設定存取層回傳的失敗。 */
        val failure: MahjongClientConfigUpdateResult.Failure,
    ) : ClientAutoSortHandPreferenceUpdateResult

    /** 本機已保存，但 USER_CHANGE 封包未能交給 Fabric networking。 */
    data class SyncFailed(
        /** 已保存的本機狀態。 */
        val enabled: Boolean,
        /** 傳送時發生的錯誤。 */
        val cause: RuntimeException,
    ) : ClientAutoSortHandPreferenceUpdateResult
}

/** 統一管理永久自動整理手牌偏好的原子保存與同步順序。 */
@Single
class ClientAutoSortHandPreferenceService(
    private val configStore: MahjongClientConfigStore,
    private val sender: ClientAutoSortHandPreferenceSender,
) {
    /** 目前本機已保存的偏好。 */
    fun current(): Boolean = configStore.current.autoSortHandEnabled

    /** 將偏好切換為目前相反狀態。 */
    fun toggle(): ClientAutoSortHandPreferenceUpdateResult = set(!current())

    /** 先原子保存 [enabled]，成功後才送出 USER_CHANGE。 */
    fun set(enabled: Boolean): ClientAutoSortHandPreferenceUpdateResult {
        if (enabled == current()) return ClientAutoSortHandPreferenceUpdateResult.Unchanged(enabled)
        return when (val result = configStore.setAutoSortHandEnabled(enabled)) {
            is MahjongClientConfigUpdateResult.Failure -> ClientAutoSortHandPreferenceUpdateResult.SaveFailed(result)
            is MahjongClientConfigUpdateResult.Success -> try {
                sender.sendUserChange(enabled)
                ClientAutoSortHandPreferenceUpdateResult.Updated(enabled)
            } catch (exception: RuntimeException) {
                ClientAutoSortHandPreferenceUpdateResult.SyncFailed(enabled, exception)
            }
        }
    }

    /** 將本機已保存值以 RESTORE 語意同步至剛加入的伺服器。 */
    fun restoreToServer() {
        sender.sendRestore(current())
    }
}
