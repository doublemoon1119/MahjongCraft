package com.doublemoon1119.mahjongcraft.flow.server.game.orchestration

import com.doublemoon1119.mahjongcraft.flow.common.game.model.Game
import com.doublemoon1119.mahjongcraft.flow.common.game.model.GameCommand
import kotlin.uuid.Uuid

/**
 * 需要由伺服器代為完成的一次開局準備提交。
 *
 * @property playerId 提交的參與者。
 * @property command 要送出的提交命令。
 * @property clearsForcedAutoPlay 參與者是否為逾時的真人；提交前須先解除他的強制自動操作。
 * @property basis 提交內容所依據的權威遊戲；AI 的提交只應在權威遊戲仍是這個遊戲時套用。
 */
data class AutomatedRoundPreparation(
    val playerId: Uuid,
    val command: GameCommand.SubmitRoundPreparation,
    val clearsForcedAutoPlay: Boolean,
    val basis: Game,
)
