package com.doublemoon1119.mahjongcraft.platform.fabric.client.automatic

import com.doublemoon1119.mahjongcraft.flow.network.dto.message.AutomaticControlUpdateRequestDto

/** 將本局自動操作的請求送往目前連線的伺服器。 */
interface ClientAutomaticControlRequestSender {
    /**
     * 送出更新請求；呼叫端必須先完成權威快照與待處理狀態驗證。
     *
     * @param request 已通過本機驗證的更新請求。
     */
    fun send(request: AutomaticControlUpdateRequestDto)

    /** 要求伺服器重新送出本人目前的自動操作狀態。 */
    fun requestSnapshot()
}
