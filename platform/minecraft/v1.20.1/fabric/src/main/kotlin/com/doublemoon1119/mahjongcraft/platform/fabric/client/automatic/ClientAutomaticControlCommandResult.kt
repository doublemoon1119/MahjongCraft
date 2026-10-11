package com.doublemoon1119.mahjongcraft.platform.fabric.client.automatic

import com.doublemoon1119.mahjongcraft.platform.fabric.client.config.MahjongClientConfigUpdateResult

/** Client 指令在本地送出階段取得的結果。 */
sealed interface ClientAutomaticControlCommandResult {
    /**
     * 永久自動理牌已保存並同步。
     *
     * @property controlId 控制 ID。
     * @property previousEnabled 更新前狀態。
     * @property enabled 更新後狀態。
     */
    data class Updated(
        val controlId: String,
        val previousEnabled: Boolean,
        val enabled: Boolean,
    ) : ClientAutomaticControlCommandResult

    /**
     * 控制已是要求的狀態，未產生寫入或網路請求。
     *
     * @property controlId 控制 ID。
     * @property enabled 目前狀態。
     */
    data class Unchanged(
        val controlId: String,
        val enabled: Boolean,
    ) : ClientAutomaticControlCommandResult

    /**
     * 本局更新已送出，必須等待相同 [requestId] 的 ACK。
     *
     * @property requestId 請求 ID。
     * @property controlId 控制 ID。
     * @property previousEnabled 已確認的更新前狀態。
     * @property enabled 要求的狀態。
     */
    data class Submitted(
        val requestId: String,
        val controlId: String,
        val previousEnabled: Boolean,
        val enabled: Boolean,
    ) : ClientAutomaticControlCommandResult

    /** 已有另一筆本局更新等待回覆。 */
    data object Pending : ClientAutomaticControlCommandResult

    /** 目前沒有本局權威快照。 */
    data object Unavailable : ClientAutomaticControlCommandResult

    /**
     * 指定 ID 不是永久控制，也不受目前規則支援。
     *
     * @property controlId 不受支援的控制 ID。
     */
    data class Unsupported(val controlId: String) : ClientAutomaticControlCommandResult

    /**
     * 本機設定保存失敗。
     *
     * @property failure 設定存取層回傳的失敗。
     */
    data class SaveFailed(val failure: MahjongClientConfigUpdateResult.Failure) : ClientAutomaticControlCommandResult

    /**
     * 本機已保存，但偏好同步封包送出失敗。
     *
     * @property enabled 已保存的狀態。
     */
    data class PreferenceSyncFailed(val enabled: Boolean) : ClientAutomaticControlCommandResult

    /** 本局更新封包送出失敗。 */
    data object RoundControlSendFailed : ClientAutomaticControlCommandResult
}
