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
    /** 房間或場地識別碼，適用於所有房間操作。 */
    val venueId: String

    /**
     * Create 網路 DTO。
     *
     * @property venueId 房間或場地識別碼。
     */
    @Serializable data class Create(override val venueId: String) : RoomActionDto

    /**
     * Join 網路 DTO。
     *
     * @property venueId 房間或場地識別碼。
     */
    @Serializable data class Join(override val venueId: String) : RoomActionDto

    /**
     * ToggleReady 網路 DTO。
     *
     * @property venueId 房間或場地識別碼。
     */
    @Serializable data class ToggleReady(override val venueId: String) : RoomActionDto

    /**
     * Start 網路 DTO。
     *
     * @property venueId 房間或場地識別碼。
     */
    @Serializable data class Start(override val venueId: String) : RoomActionDto

    /**
     * Leave 網路 DTO。
     *
     * @property venueId 房間或場地識別碼。
     */
    @Serializable data class Leave(override val venueId: String) : RoomActionDto

    /**
     * Disband 網路 DTO。
     *
     * @property venueId 房間或場地識別碼。
     */
    @Serializable data class Disband(override val venueId: String) : RoomActionDto

    /**
     * AddAi 網路 DTO。
     *
     * @property venueId 房間或場地識別碼。
     * @property strategyKey AI 策略識別碼。
     */
    @Serializable data class AddAi(override val venueId: String, val strategyKey: String? = null) : RoomActionDto

    /**
     * 變更 AI 策略的網路 DTO。
     *
     * @property venueId 房間識別碼。
     * @property targetPlayerId 目標玩家識別碼。
     * @property strategyKey 新的 AI 策略識別碼。
     */
    @Serializable data class ChangeAiStrategy(
        override val venueId: String,
        val targetPlayerId: String,
        val strategyKey: String,
    ) : RoomActionDto

    /**
     * Kick 網路 DTO。
     *
     * @property venueId 房間或場地識別碼。
     * @property targetPlayerId 目標玩家識別碼。
     */
    @Serializable data class Kick(override val venueId: String, val targetPlayerId: String) : RoomActionDto

    /**
     * 房主允許指定被排除者重新加入，不接受來源身分或位置。
     *
     * @property venueId 目前房間。
     * @property targetPlayerId 上次的成員編號，仍由伺服器驗證。
     */
    @Serializable data class AllowParticipant(override val venueId: String, val targetPlayerId: String) : RoomActionDto

    /**
     * 房主解除同一房間的全部暫時排除，伺服器仍驗證房主身分。
     *
     * @property venueId 目前房間。
     */
    @Serializable data class AllowAllParticipants(override val venueId: String) : RoomActionDto

    /**
     * UpdateConfig 網路 DTO。
     *
     * @property venueId 房間或場地識別碼。
     * @property config 更新後的遊戲設定。
     */
    @Serializable data class UpdateConfig(override val venueId: String, val config: GameConfigDto) : RoomActionDto
}
