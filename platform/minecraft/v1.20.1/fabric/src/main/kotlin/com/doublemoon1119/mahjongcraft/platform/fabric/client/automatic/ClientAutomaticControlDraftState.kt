package com.doublemoon1119.mahjongcraft.platform.fabric.client.automatic

import com.doublemoon1119.mahjongcraft.flow.network.dto.message.AutomaticControlSnapshotDto
import com.doublemoon1119.mahjongcraft.flow.network.dto.message.AutomaticControlUpdateResultKindDto

/**
 * 設定入口目前可觀察的本局自動操作草稿狀態。
 *
 * @property baseline 開啟或最近確認時採用的權威基準；沒有可用快照時為 null。
 * @property enabledControlIds 草稿中的完整啟用集合。
 * @property pendingRequestId 目前等待伺服器回覆的請求識別碼；沒有待處理請求時為 null。
 * @property stale 草稿依據的權威版本是否已過期。
 * @property failure 最近一次未接受的伺服器結果；沒有失敗時為 null。
 * @property unavailable 是否沒有可編輯的本局權威狀態。
 */
data class ClientAutomaticControlDraftState(
    val baseline: AutomaticControlSnapshotDto?,
    val enabledControlIds: Set<String>,
    val pendingRequestId: String?,
    val stale: Boolean,
    val failure: AutomaticControlUpdateResultKindDto?,
    val unavailable: Boolean,
) {
    /** 草稿是否與權威基準不同。 */
    val dirty: Boolean get() = baseline != null && enabledControlIds != baseline.enabledControlIds

    /** 是否正等待 server 確認。 */
    val pending: Boolean get() = pendingRequestId != null
}
