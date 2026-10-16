package com.doublemoon1119.mahjongcraft.platform.fabric.client.automatic

import com.doublemoon1119.mahjongcraft.flow.network.dto.message.AutomaticControlSnapshotDto

/**
 * 一筆已配對請求的完成結果。
 *
 * @property sequence 客戶端單調遞增的完成序號。
 * @property requestId 完成的請求識別碼。
 * @property gameId 請求所屬的對局識別碼。
 * @property result 請求最後的結果。
 * @property authoritativeSnapshot 完成時可用的最新權威快照；沒有快照時為 null。
 */
data class ClientAutomaticControlCompletion(
    val sequence: Long,
    val requestId: String,
    val gameId: String,
    val result: ClientAutomaticControlResultKind,
    val authoritativeSnapshot: AutomaticControlSnapshotDto?,
)
