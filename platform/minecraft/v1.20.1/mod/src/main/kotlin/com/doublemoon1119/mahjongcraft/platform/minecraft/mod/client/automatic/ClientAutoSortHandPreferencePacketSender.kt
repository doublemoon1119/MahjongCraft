package com.doublemoon1119.mahjongcraft.platform.minecraft.mod.client.automatic

import com.doublemoon1119.mahjongcraft.flow.network.dto.rule.NetworkJson
import com.doublemoon1119.mahjongcraft.platform.minecraft.mod.client.automatic.ClientAutoSortHandPreferenceSender
import com.doublemoon1119.mahjongcraft.platform.minecraft.mod.network.MahjongChannels

/**
 * 透過封包通道同步自動整理手牌偏好。
 *
 * @property json 偏好封包使用的序列化設定。
 */
class ClientAutoSortHandPreferencePacketSender(
    private val json: NetworkJson,
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
