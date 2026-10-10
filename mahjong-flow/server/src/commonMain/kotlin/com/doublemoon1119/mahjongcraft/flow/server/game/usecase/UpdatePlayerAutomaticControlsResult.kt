package com.doublemoon1119.mahjongcraft.flow.server.game.usecase

import com.doublemoon1119.mahjongcraft.flow.common.game.model.PlayerAutomaticControlSnapshot

/** 更新玩家本局自動操作集合的權威結果。 */
sealed interface UpdatePlayerAutomaticControlsResult {
    /**
     * 更新已接受。
     *
     * @property snapshot 接受後的個人權威快照。
     * @property changed 本次是否實際改變集合及 revision。
     */
    data class Accepted(
        val snapshot: PlayerAutomaticControlSnapshot,
        val changed: Boolean,
    ) : UpdatePlayerAutomaticControlsResult

    /**
     * 提交所依據的 revision 已過期，並回傳目前權威快照。
     *
     * @property snapshot 目前權威快照。
     */
    data class Stale(
        val snapshot: PlayerAutomaticControlSnapshot,
    ) : UpdatePlayerAutomaticControlsResult

    /**
     * 提交內容遭拒絕。
     *
     * @property reason 拒絕原因。
     * @property snapshot 驗證身分後可安全回傳的目前權威快照；無法確認成員時為 null。
     */
    data class Rejected(
        val reason: AutomaticControlUpdateRejection,
        val snapshot: PlayerAutomaticControlSnapshot? = null,
    ) : UpdatePlayerAutomaticControlsResult

    /**
     * 指定對局目前無法提供本局自動操作狀態。
     *
     * @property reason 無法提供狀態的原因。
     */
    data class Unavailable(
        val reason: AutomaticControlUpdateUnavailableReason,
    ) : UpdatePlayerAutomaticControlsResult
}
