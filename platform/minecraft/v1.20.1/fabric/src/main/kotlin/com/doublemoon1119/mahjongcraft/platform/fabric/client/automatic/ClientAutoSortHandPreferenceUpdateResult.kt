package com.doublemoon1119.mahjongcraft.platform.fabric.client.automatic

import com.doublemoon1119.mahjongcraft.platform.fabric.client.config.MahjongClientConfigUpdateResult

/** 自動整理手牌偏好的本機保存與伺服器同步結果。 */
sealed interface ClientAutoSortHandPreferenceUpdateResult {
    /**
     * 本機保存及 USER_CHANGE 同步皆已完成。
     *
     * @property enabled 更新後的狀態。
     */
    data class Updated(val enabled: Boolean) : ClientAutoSortHandPreferenceUpdateResult

    /**
     * 目前已是要求的狀態，沒有重寫檔案或送出封包。
     *
     * @property enabled 目前狀態。
     */
    data class Unchanged(val enabled: Boolean) : ClientAutoSortHandPreferenceUpdateResult

    /**
     * 本機設定保存失敗，未傳送 USER_CHANGE。
     *
     * @property failure 設定存取層回傳的失敗。
     */
    data class SaveFailed(val failure: MahjongClientConfigUpdateResult.Failure) : ClientAutoSortHandPreferenceUpdateResult

    /**
     * 本機已保存，但 USER_CHANGE 封包未能交給 Fabric networking。
     *
     * @property enabled 已保存的本機狀態。
     * @property cause 傳送時發生的錯誤。
     */
    data class SyncFailed(
        val enabled: Boolean,
        val cause: RuntimeException,
    ) : ClientAutoSortHandPreferenceUpdateResult
}
