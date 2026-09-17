package com.doublemoon1119.mahjongcraft.flow.network.dto.message

import com.doublemoon1119.mahjongcraft.flow.common.room.model.RoomSnapshot
import com.doublemoon1119.mahjongcraft.flow.network.dto.snapshot.RoomSnapshotDto
import com.doublemoon1119.mahjongcraft.flow.network.dto.snapshot.RoundPreparationSnapshotDto
import com.doublemoon1119.mahjongcraft.flow.network.dto.snapshot.TableStateSnapshotDto
import com.doublemoon1119.mahjongcraft.logic.table.TableStateSnapshot
import kotlinx.serialization.Serializable

/** 沒有伴隨房間事件、單純重新同步最新 [RoomSnapshot] 時使用的 S2C payload。 */
@Serializable
data class RoomSnapshotSyncPayloadDto(
    /** 房間 UUID 的字串表示。 */
    val roomId: String,
    /** 指定 observer 當下可見的完整房間快照。 */
    val snapshot: RoomSnapshotDto,
)

/**
 * 房間與對局都不存在時使用的 S2C payload。
 *
 * 只清除該玩家手上這個識別碼的房間與對局快照，不攜帶任何動作語意，也不會開啟任何畫面。
 */
@Serializable
data class SnapshotClearedPayloadDto(
    /** 房間或對局 UUID 的字串表示。 */
    val id: String,
)

/** 沒有伴隨遊戲動作、單純重新同步最新 [TableStateSnapshot] 時使用的 S2C payload。 */
@Serializable
data class GameSnapshotSyncPayloadDto(
    /** 遊戲 UUID 的字串表示。 */
    val gameId: String,
    /** 指定 observer 當下可見的完整遊戲快照。 */
    val snapshot: TableStateSnapshotDto,
    /** 指定 observer 可見的開局準備狀態。 */
    val roundPreparation: RoundPreparationSnapshotDto? = null,
)
