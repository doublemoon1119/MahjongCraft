package com.doublemoon1119.mahjongcraft.platform.fabric.client.automatic

import com.doublemoon1119.mahjongcraft.flow.network.dto.message.AutomaticControlUpdateResultKindDto

/** 一筆本局自動操作指令收到 ACK 後的結果。 */
data class ClientAutomaticControlCommandCompletion(
    /** server 處理結果。 */
    val result: AutomaticControlUpdateResultKindDto,
    /** ACK 套用後已確認的控制狀態；沒有可用快照時為 `null`。 */
    val enabled: Boolean?,
)
