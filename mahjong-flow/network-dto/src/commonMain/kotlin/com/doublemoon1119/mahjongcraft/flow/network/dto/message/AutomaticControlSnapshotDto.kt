package com.doublemoon1119.mahjongcraft.flow.network.dto.message

import kotlinx.serialization.Serializable

/**
 * 只提供給對應玩家本人的本局自動操作權威快照。
 *
 *
 * @property gameId 所屬對局識別碼。
 * @property revision 建立快照時的權威 revision。
 * @property supportedControlIds 目前規則支援的完整控制 ID 集合。
 * @property enabledControlIds 該玩家目前已啟用的控制 ID 集合。
 */
@Serializable
data class AutomaticControlSnapshotDto(
    val gameId: String,
    val revision: Long,
    val supportedControlIds: Set<String>,
    val enabledControlIds: Set<String>,
)
