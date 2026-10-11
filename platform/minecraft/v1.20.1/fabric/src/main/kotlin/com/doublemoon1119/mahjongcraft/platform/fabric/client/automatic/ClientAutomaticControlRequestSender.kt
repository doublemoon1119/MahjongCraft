package com.doublemoon1119.mahjongcraft.platform.fabric.client.automatic

import com.doublemoon1119.mahjongcraft.flow.network.dto.message.AutomaticControlUpdateRequestDto

/** 將本局自動操作更新請求送往目前連線的伺服器。 */
fun interface ClientAutomaticControlRequestSender {
    /** 送出 [request]；呼叫端必須先完成權威快照與 pending 狀態驗證。 */
    fun send(request: AutomaticControlUpdateRequestDto)
}
