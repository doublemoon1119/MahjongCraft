package com.doublemoon1119.mahjongcraft.platform.minecraft.mod.client.history

import com.doublemoon1119.mahjongcraft.flow.network.dto.message.HistoryArchiveStatusRequestDto
import com.doublemoon1119.mahjongcraft.flow.network.dto.rule.NetworkJson
import com.doublemoon1119.mahjongcraft.platform.minecraft.mod.client.history.HistoryArchiveStatusSender
import com.doublemoon1119.mahjongcraft.platform.minecraft.mod.network.MahjongChannels

/**
 * 將有界保存狀態要求交給頻道，不執行本地保存判定。
 *
 * @property json 線路序列化設定。
 */
internal class HistoryArchiveStatusPacketSender(private val json: NetworkJson) : HistoryArchiveStatusSender {
    /**
     * 傳送由狀態控制器配對的保存狀態要求。
     *
     * @param request 具有配對識別與場次的要求。
     */
    override fun send(request: HistoryArchiveStatusRequestDto) = MahjongChannels.historyArchiveStatusRequest.sendToServer(json, request)
}
