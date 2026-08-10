package com.doublemoon1119.mahjongcraft.flow.network.dto.message

import com.doublemoon1119.mahjongcraft.flow.network.dto.command.GameCommandDto
import kotlinx.serialization.Serializable

/**
 * `mahjongcraft:game_command` C2S 頻道的網路信封。[GameCommandDto] 本身鏡射
 * [com.doublemoon1119.mahjongcraft.flow.common.game.model.GameCommand]，不帶對局識別碼——伺服器端
 * 的 `GameFlowCoordinator` 需要 gameId 才能找到對應對局，這裡額外包一層帶上，玩家身分則一律用
 * 接收端拿到的連線本身的身分，不透過封包內容宣稱。
 */
@Serializable
data class GameCommandEnvelopeDto(
    val gameId: String,
    val command: GameCommandDto,
)
