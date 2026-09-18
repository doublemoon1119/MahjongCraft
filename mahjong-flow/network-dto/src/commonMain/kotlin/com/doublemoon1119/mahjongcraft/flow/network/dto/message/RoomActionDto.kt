package com.doublemoon1119.mahjongcraft.flow.network.dto.message

import com.doublemoon1119.mahjongcraft.flow.network.dto.config.GameConfigDto
import kotlinx.serialization.Serializable

/**
 * 玩家對一個房間送出的受控操作。
 *
 * 玩家身分一律由連線取得，不採信 payload 宣稱的身分。
 */
@Serializable
sealed interface RoomActionDto {
    val tableId: String

    @Serializable data class Create(override val tableId: String) : RoomActionDto

    @Serializable data class Join(override val tableId: String) : RoomActionDto

    @Serializable data class ToggleReady(override val tableId: String) : RoomActionDto

    @Serializable data class Start(override val tableId: String) : RoomActionDto

    @Serializable data class Leave(override val tableId: String) : RoomActionDto

    @Serializable data class Disband(override val tableId: String) : RoomActionDto

    @Serializable data class AddAi(override val tableId: String, val strategyKey: String? = null) : RoomActionDto

    @Serializable data class ChangeAiStrategy(
        override val tableId: String,
        val targetPlayerId: String,
        val strategyKey: String,
    ) : RoomActionDto

    @Serializable data class Kick(override val tableId: String, val targetPlayerId: String) : RoomActionDto

    @Serializable data class UpdateConfig(override val tableId: String, val config: GameConfigDto) : RoomActionDto
}
