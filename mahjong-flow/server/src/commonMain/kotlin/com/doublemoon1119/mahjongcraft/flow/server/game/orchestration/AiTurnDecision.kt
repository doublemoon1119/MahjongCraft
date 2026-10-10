package com.doublemoon1119.mahjongcraft.flow.server.game.orchestration

import com.doublemoon1119.mahjongcraft.flow.common.game.model.Game
import com.doublemoon1119.mahjongcraft.flow.common.game.model.GameCommand
import kotlin.uuid.Uuid

/**
 * [AiTurnDriver] 決定的一次 AI 操作。
 *
 * @property playerId 行動的 AI 玩家。
 * @property command 要送出的命令。
 * @property basis 決策所依據的權威遊戲；命令只應在權威遊戲仍是這個遊戲時套用。
 */
data class AiTurnDecision(
    val playerId: Uuid,
    val command: GameCommand,
    val basis: Game,
)
