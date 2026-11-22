package com.doublemoon1119.mahjongcraft.platform.minecraft.mod.client.automatic

import com.doublemoon1119.mahjongcraft.flow.network.dto.message.AutomaticControlUpdateRequestDto
import com.doublemoon1119.mahjongcraft.flow.network.dto.rule.NetworkJson
import com.doublemoon1119.mahjongcraft.platform.minecraft.mod.network.MahjongChannels

/**
 * 使用客戶端網路傳送本局自動操作的更新與重新同步請求。
 *
 * @property json 請求封包使用的序列化設定。
 */
class ClientAutomaticControlRequestPacketSender(
    private val json: NetworkJson,
) : ClientAutomaticControlRequestSender {
    /** 將 [request] 編碼並送往伺服器。 */
    override fun send(request: AutomaticControlUpdateRequestDto) {
        MahjongChannels.automaticControlUpdate.sendToServer(json, request)
    }

    /** 送出重新同步自動操作狀態的要求。 */
    override fun requestSnapshot() {
        MahjongChannels.requestAutomaticControlSnapshot.sendToServer(json, Unit)
    }
}
