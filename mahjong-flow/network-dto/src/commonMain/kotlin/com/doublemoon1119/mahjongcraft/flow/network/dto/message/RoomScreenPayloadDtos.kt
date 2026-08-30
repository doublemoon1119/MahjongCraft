package com.doublemoon1119.mahjongcraft.flow.network.dto.message

import com.doublemoon1119.mahjongcraft.flow.network.dto.config.GameConfigDto
import com.doublemoon1119.mahjongcraft.flow.network.dto.snapshot.RoomSnapshotDto
import kotlinx.serialization.Serializable

/** RoomScreen 顯示的權威桌級階段。 */
@Serializable
enum class TableLobbyPhaseDto { EMPTY, WAITING, PLAYING }

/** 伺服器要求客戶端開啟或刷新 RoomScreen 的公開資料。 */
@Serializable
data class TableLobbyPayloadDto(
    val tableId: String,
    val phase: TableLobbyPhaseDto,
    val roomSnapshot: RoomSnapshotDto? = null,
    val playingPlayerIds: List<String> = emptyList(),
    val playingAiPlayerIds: List<String> = emptyList(),
    val playingGameConfig: GameConfigDto? = null,
    val dimensionId: String? = null,
    val tableX: Int? = null,
    val tableY: Int? = null,
    val tableZ: Int? = null,
)

/** RoomScreen 送出的受控房間操作。玩家身分一律由連線取得。 */
@Serializable
sealed interface RoomScreenActionDto {
    val tableId: String

    @Serializable data class Create(override val tableId: String) : RoomScreenActionDto

    @Serializable data class Join(override val tableId: String) : RoomScreenActionDto

    @Serializable data class ToggleReady(override val tableId: String) : RoomScreenActionDto

    @Serializable data class Start(override val tableId: String) : RoomScreenActionDto

    @Serializable data class Leave(override val tableId: String) : RoomScreenActionDto

    @Serializable data class Disband(override val tableId: String) : RoomScreenActionDto

    @Serializable data class Close(override val tableId: String) : RoomScreenActionDto

    @Serializable data class AddAi(override val tableId: String, val strategyKey: String? = null) : RoomScreenActionDto

    @Serializable data class ChangeAiStrategy(
        override val tableId: String,
        val targetPlayerId: String,
        val strategyKey: String,
    ) : RoomScreenActionDto

    @Serializable data class Kick(override val tableId: String, val targetPlayerId: String) : RoomScreenActionDto

    @Serializable data class UpdateConfig(override val tableId: String, val config: GameConfigDto) : RoomScreenActionDto
}
