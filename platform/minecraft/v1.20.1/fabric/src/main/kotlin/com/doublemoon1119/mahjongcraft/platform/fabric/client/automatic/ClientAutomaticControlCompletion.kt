package com.doublemoon1119.mahjongcraft.platform.fabric.client.automatic

import com.doublemoon1119.mahjongcraft.flow.network.dto.message.AutomaticControlSnapshotDto
import com.doublemoon1119.mahjongcraft.flow.network.dto.message.AutomaticControlUpdateResultDto

/** 一筆已配對請求的伺服器完成結果。 */
data class ClientAutomaticControlCompletion(
    /** client 端單調遞增的完成序號。 */
    val sequence: Long,
    /** 伺服器回傳的權威結果。 */
    val result: AutomaticControlUpdateResultDto,
    /** 套用結果後 store 中可用的最新權威快照。 */
    val authoritativeSnapshot: AutomaticControlSnapshotDto?,
)
