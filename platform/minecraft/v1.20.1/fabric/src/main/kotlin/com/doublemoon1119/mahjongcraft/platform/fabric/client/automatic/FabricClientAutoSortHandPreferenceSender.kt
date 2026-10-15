package com.doublemoon1119.mahjongcraft.platform.fabric.client.automatic

import com.doublemoon1119.mahjongcraft.platform.fabric.network.MahjongChannels
import kotlinx.serialization.json.Json

/**
 * 透過 Fabric 封包通道同步自動整理手牌偏好。
 *
 * @property json 偏好封包使用的序列化設定。
 */
class FabricClientAutoSortHandPreferenceSender(
    private val json: Json,
) : ClientAutoSortHandPreferenceSender {
    /** 傳送玩家主動變更。 */
    override fun sendUserChange(enabled: Boolean) {
        MahjongChannels.setAutoSortHand.sendToServer(json, enabled)
    }

    /** 傳送加入世界時的偏好恢復。 */
    override fun sendRestore(enabled: Boolean) {
        MahjongChannels.restoreAutoSortHand.sendToServer(json, enabled)
    }
}
