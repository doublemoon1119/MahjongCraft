package com.doublemoon1119.mahjongcraft.platform.fabric.client.automatic

import com.doublemoon1119.mahjongcraft.flow.network.dto.message.AutomaticControlSnapshotDto
import com.doublemoon1119.mahjongcraft.flow.network.dto.message.AutomaticControlUpdateResultKindDto

/** 設定入口目前可觀察的本局自動操作草稿狀態。 */
data class ClientAutomaticControlDraftState(
    /** 開啟或最近確認時採用的權威基準。 */
    val baseline: AutomaticControlSnapshotDto?,
    /** 使用者尚未確認的完整啟用集合。 */
    val enabledControlIds: Set<String>,
    /** 目前等待 ACK 的 request ID。 */
    val pendingRequestId: String?,
    /** 草稿依據的權威 revision 已過期。 */
    val stale: Boolean,
    /** 最近一次未接受的 server 結果。 */
    val failure: AutomaticControlUpdateResultKindDto?,
    /** 目前沒有可編輯的本局權威狀態。 */
    val unavailable: Boolean,
) {
    /** 草稿是否與權威基準不同。 */
    val dirty: Boolean get() = baseline != null && enabledControlIds != baseline.enabledControlIds

    /** 是否正等待 server 確認。 */
    val pending: Boolean get() = pendingRequestId != null
}
