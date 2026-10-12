package com.doublemoon1119.mahjongcraft.platform.fabric.client.automatic

import com.doublemoon1119.mahjongcraft.flow.network.dto.message.AutomaticControlUpdateRequestDto

/** 將本局自動操作更新請求送往目前連線的伺服器。 */
fun interface ClientAutomaticControlRequestSender {
    /**
     * 送出請求；呼叫端必須先完成權威快照與待處理狀態驗證。
     *
     * @param request 已通過本機驗證的更新請求。
     */
    fun send(request: AutomaticControlUpdateRequestDto)
}
