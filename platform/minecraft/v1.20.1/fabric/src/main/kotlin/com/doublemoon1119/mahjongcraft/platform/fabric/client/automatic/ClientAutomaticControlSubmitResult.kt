package com.doublemoon1119.mahjongcraft.platform.fabric.client.automatic

import com.doublemoon1119.mahjongcraft.flow.network.dto.message.AutomaticControlUpdateRequestDto

/** 送出本局自動操作更新時，在 client 端即可判定的結果。 */
sealed interface ClientAutomaticControlSubmitResult {
    /**
     * 請求已送出並等待伺服器回覆。
     *
     * @property request 已送出的完整請求。
     */
    data class Submitted(
        val request: AutomaticControlUpdateRequestDto,
    ) : ClientAutomaticControlSubmitResult

    /** 已有另一筆請求等待回覆。 */
    data object Pending : ClientAutomaticControlSubmitResult

    /** 目前沒有可更新的本局權威快照。 */
    data object Unavailable : ClientAutomaticControlSubmitResult

    /**
     * 草稿包含目前規則未宣告支援的控制 ID。
     *
     * @property controlIds 不受支援的控制 ID。
     */
    data class Unsupported(val controlIds: Set<String>) : ClientAutomaticControlSubmitResult

    /**
     * 封包未能交給 Fabric networking，pending 狀態已回復。
     *
     * @property cause 傳送時發生的錯誤。
     */
    data class SendFailed(val cause: RuntimeException) : ClientAutomaticControlSubmitResult
}
