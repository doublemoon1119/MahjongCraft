package com.doublemoon1119.mahjongcraft.flow.server.game.orchestration

import com.doublemoon1119.mahjongcraft.flow.common.game.model.GameCommand
import com.doublemoon1119.mahjongcraft.flow.common.game.model.GameError
import com.doublemoon1119.mahjongcraft.flow.common.result.Outcome
import kotlin.uuid.Uuid

/**
 * 分派 [command] 後接著驅動自動操作，與平台處理真人命令的方式相同：強制自動操作中的玩家被拒絕時不驅動。
 *
 * 測試在呼叫端協程內直接驅動，不經 [AutomatedAdvanceManager] 排程，因此返回時自動連鎖已經跑完。
 *
 * @param gameId 對局 Uuid。
 * @param playerId 發起操作的玩家 Uuid。
 * @param command 欲執行的操作。
 * @return 分派的結果。
 */
internal suspend fun GameFlowCoordinator.dispatchThenDrive(
    gameId: Uuid,
    playerId: Uuid,
    command: GameCommand,
): Outcome<Unit, GameError> {
    val result = dispatch(gameId, playerId, command)
    if (result !is Outcome.Error || result.error !is GameError.ForcedAutoPlayActive) driveAutomatedPlayers(gameId)
    return result
}
