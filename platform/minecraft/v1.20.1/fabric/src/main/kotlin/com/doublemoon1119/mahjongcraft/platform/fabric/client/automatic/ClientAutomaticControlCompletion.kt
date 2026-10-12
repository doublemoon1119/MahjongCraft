package com.doublemoon1119.mahjongcraft.platform.fabric.client.automatic

import com.doublemoon1119.mahjongcraft.flow.network.dto.message.AutomaticControlSnapshotDto
import com.doublemoon1119.mahjongcraft.flow.network.dto.message.AutomaticControlUpdateResultDto

/**
 * 一筆已配對請求的伺服器完成結果。
 *
 * @property sequence 客戶端單調遞增的完成序號。
 * @property result 伺服器回傳的權威結果。
 * @property authoritativeSnapshot 套用結果後可用的最新權威快照；沒有快照時為 null。
 */
data class ClientAutomaticControlCompletion(
    val sequence: Long,
    val result: AutomaticControlUpdateResultDto,
    val authoritativeSnapshot: AutomaticControlSnapshotDto?,
)
