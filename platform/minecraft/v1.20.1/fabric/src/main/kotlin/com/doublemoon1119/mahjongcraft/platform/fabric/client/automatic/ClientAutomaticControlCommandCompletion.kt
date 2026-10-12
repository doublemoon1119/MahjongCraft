package com.doublemoon1119.mahjongcraft.platform.fabric.client.automatic

import com.doublemoon1119.mahjongcraft.flow.network.dto.message.AutomaticControlUpdateResultKindDto

/**
 * 一筆本局自動操作指令收到伺服器回覆後的結果。
 *
 * @property result 伺服器處理結果。
 * @property enabled 回覆套用後已確認的控制狀態；沒有可用快照時為 null。
 */
data class ClientAutomaticControlCommandCompletion(
    val result: AutomaticControlUpdateResultKindDto,
    val enabled: Boolean?,
)
