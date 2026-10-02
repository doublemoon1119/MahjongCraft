package com.doublemoon1119.mahjongcraft.platform.fabric.client.history

import com.doublemoon1119.mahjongcraft.flow.network.dto.message.HistoryArchiveStatusRequestDto
import com.doublemoon1119.mahjongcraft.platform.fabric.network.MahjongChannels
import kotlinx.serialization.json.Json
import org.koin.core.annotation.Single

/**
 * 將有界保存狀態要求交給 Fabric 頻道，不執行本地保存判定。
 *
 * @property json 線路序列化設定。
 */
@Single(binds = [HistoryArchiveStatusSender::class])
internal class FabricHistoryArchiveStatusSender(private val json: Json) : HistoryArchiveStatusSender {
    /**
     * 傳送由狀態控制器配對的保存狀態要求。
     *
     * @param request 具有配對識別與場次的要求。
     */
    override fun send(request: HistoryArchiveStatusRequestDto) = MahjongChannels.historyArchiveStatusRequest.sendToServer(json, request)
}
