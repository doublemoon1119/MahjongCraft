package com.doublemoon1119.mahjongcraft.platform.fabric.client.automatic

import com.doublemoon1119.mahjongcraft.flow.network.dto.message.AutomaticControlUpdateRequestDto
import com.doublemoon1119.mahjongcraft.platform.fabric.network.MahjongChannels
import kotlinx.serialization.json.Json

/**
 * 使用 Fabric 客戶端網路傳送本局自動操作更新請求。
 *
 * @property json 請求封包使用的序列化設定。
 */
class FabricClientAutomaticControlRequestSender(
    private val json: Json,
) : ClientAutomaticControlRequestSender {
    /** 將 [request] 編碼並送往伺服器。 */
    override fun send(request: AutomaticControlUpdateRequestDto) {
        MahjongChannels.automaticControlUpdate.sendToServer(json, request)
    }
}
