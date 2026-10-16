package com.doublemoon1119.mahjongcraft.platform.fabric.client.automatic

/**
 * 一筆本局自動操作指令完成後的結果。
 *
 * @property result 請求最後的結果。
 * @property enabled 回覆套用後已確認的控制狀態；沒有可用快照時為 null。
 */
data class ClientAutomaticControlCommandCompletion(
    val result: ClientAutomaticControlResultKind,
    val enabled: Boolean?,
)
