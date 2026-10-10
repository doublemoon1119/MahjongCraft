package com.doublemoon1119.mahjongcraft.flow.network.dto.message

import kotlinx.serialization.Serializable

/**
 * 房間與對局都不存在時使用的 S2C payload。
 *
 * 只清除該玩家手上這個識別碼的房間與對局快照，不攜帶任何動作語意，也不會開啟任何畫面。
 *
 * @property id 要清除的房間或對局 UUID 字串。
 */
@Serializable
data class SnapshotClearedPayloadDto(
    val id: String,
)
