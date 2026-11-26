package com.doublemoon1119.mahjongcraft.flow.network.dto.message

import com.doublemoon1119.mahjongcraft.flow.common.room.service.RoomEventPublisher
import com.doublemoon1119.mahjongcraft.flow.network.dto.model.JoinReasonDto
import com.doublemoon1119.mahjongcraft.flow.network.dto.model.LeaveReasonDto
import com.doublemoon1119.mahjongcraft.flow.network.dto.rule.MahjongRuleConfigDto
import kotlinx.serialization.Serializable

/**
 * `mahjongcraft:room_update` S2C 頻道用的事件種類——把 [RoomEventPublisher]
 * 的四個方法收斂成同一條頻道能傳的一個 sealed 型別，純粹是網路用的封裝，不動既有的
 * `RoomEventPublisher` 介面本身。
 */
@Serializable
sealed interface RoomUpdateEventDto {
    /**
     * Join 網路 DTO。
     *
     * @property joinedPlayerId 加入房間的玩家識別碼。
     * @property reason 相關的結果或操作原因。
     */
    @Serializable
    data class Join(val joinedPlayerId: String, val reason: JoinReasonDto) : RoomUpdateEventDto

    /**
     * Leave 網路 DTO。
     *
     * @property leftPlayerId 離開房間的玩家識別碼。
     * @property reason 相關的結果或操作原因。
     */
    @Serializable
    data class Leave(val leftPlayerId: String, val reason: LeaveReasonDto) : RoomUpdateEventDto

    /**
     * Ready 網路 DTO。
     *
     * @property readyPlayerId 變更準備狀態的玩家識別碼。
     * @property isReady 玩家目前是否已準備。
     */
    @Serializable
    data class Ready(val readyPlayerId: String, val isReady: Boolean) : RoomUpdateEventDto

    /**
     * ConfigChanged 網路 DTO。
     *
     * @property newConfig 更新後的麻將規則設定。
     */
    @Serializable
    data class ConfigChanged(val newConfig: MahjongRuleConfigDto) : RoomUpdateEventDto
}
