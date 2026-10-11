package com.doublemoon1119.mahjongcraft.platform.fabric.client.automatic

import com.doublemoon1119.mahjongcraft.platform.fabric.network.MahjongChannels
import kotlinx.serialization.json.Json
import org.koin.core.annotation.Single

/** 透過既有 Fabric channels 同步自動整理手牌偏好。 */
@Single(binds = [ClientAutoSortHandPreferenceSender::class])
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
