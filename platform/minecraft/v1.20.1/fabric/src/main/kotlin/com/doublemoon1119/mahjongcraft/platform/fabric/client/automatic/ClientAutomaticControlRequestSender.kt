package com.doublemoon1119.mahjongcraft.platform.fabric.client.automatic

import com.doublemoon1119.mahjongcraft.flow.network.dto.message.AutomaticControlUpdateRequestDto
import com.doublemoon1119.mahjongcraft.platform.fabric.network.MahjongChannels
import kotlinx.serialization.json.Json
import org.koin.core.annotation.Single

/** 將本局自動操作更新請求送往目前連線的伺服器。 */
fun interface ClientAutomaticControlRequestSender {
    /** 送出 [request]；呼叫端必須先完成權威快照與 pending 狀態驗證。 */
    fun send(request: AutomaticControlUpdateRequestDto)
}

/** 使用 Fabric client networking 傳送本局自動操作更新請求。 */
@Single(binds = [ClientAutomaticControlRequestSender::class])
class FabricClientAutomaticControlRequestSender(
    private val json: Json,
) : ClientAutomaticControlRequestSender {
    /** 將 [request] 編碼並送往伺服器。 */
    override fun send(request: AutomaticControlUpdateRequestDto) {
        MahjongChannels.automaticControlUpdate.sendToServer(json, request)
    }
}
