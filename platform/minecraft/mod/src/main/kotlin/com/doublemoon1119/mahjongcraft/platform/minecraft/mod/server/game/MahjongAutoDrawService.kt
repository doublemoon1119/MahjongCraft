package com.doublemoon1119.mahjongcraft.platform.minecraft.mod.server.game

import com.doublemoon1119.mahjongcraft.flow.common.game.model.Game
import com.doublemoon1119.mahjongcraft.flow.common.game.model.GameCommand
import com.doublemoon1119.mahjongcraft.flow.common.result.Outcome
import com.doublemoon1119.mahjongcraft.flow.server.game.orchestration.GameFlowCoordinator
import com.doublemoon1119.mahjongcraft.flow.server.game.repository.GameRepository
import com.doublemoon1119.mahjongcraft.flow.server.game.service.isDrawWindowOpenFor
import com.doublemoon1119.mahjongcraft.platform.minecraft.mod.server.event.TablePresentationBusyTracker
import kotlin.uuid.Uuid

/**
 * 讓真人玩家的摸牌跟 AI 一樣由伺服器主動觸發，不需要玩家自己送出 [GameCommand.Draw]。
 *
 * - `mahjong-flow` 的 `AiTurnDriver` 把摸牌當成回合開始時的機械動作，直接回傳固定命令，但只對 AI 玩家生效。
 *   共用流程對真人玩家維持等待玩家送出請求。
 * - 伺服器替真人摸牌是 Minecraft 平台的互動設計，不寫進 `mahjong-flow` 共用的 `AiTurnDriver`／`GameFlowCoordinator`，
 *   其他平台可以採用不同的互動方式。
 *
 * @property gameRepository 權威對局數據倉庫。
 * @property gameFlowCoordinator 對局命令的分派與自動銜接入口。
 * @property busyTracker 呈現動畫播放期間查詢是否要暫時擋下自動摸牌，避免開局擲骰動畫還沒播完，莊家就先自動摸牌。
 */
class MahjongAutoDrawService(
    private val gameRepository: GameRepository,
    private val gameFlowCoordinator: GameFlowCoordinator,
    private val busyTracker: TablePresentationBusyTracker,
) {
    /**
     * 輪到真人玩家且尚未摸牌時，代替他送出 [GameCommand.Draw]。
     *
     * - 輪次與合法動作由決策計時同步建立的私人操作 HUD prompt 呈現，這裡不推送「輪到你」或手牌聊天訊息。
     * - 判斷比照 `AiTurnDriver.resolveNextAction` 對 AI 回合的判斷，套用到真人。以下情況不觸發：
     *   - 有反應視窗開著。
     *   - 目前玩家是 AI。
     *   - 已經摸過牌，或剛吃／碰成立、準備直接捨牌。
     *   - 玩家已進入強制自動操作（[Game.forcedAutoPlayPlayerIds]）。這類玩家的摸牌與捨牌由 `ForcedAutoPlayDriver` 在自動推進中代打。
     * - 只分派摸牌，不接著驅動自動連鎖。它在每次自動推進的最後執行，摸牌成功時由推進再跑一輪接續後續流程。
     *
     * @param gameId 欲檢查的對局 Uuid。
     * @return 是否替真人摸了牌。
     */
    suspend fun checkAndAutoDraw(gameId: Uuid): Boolean {
        if (busyTracker.isBusy(gameId)) return false
        val game = gameRepository.getGame(gameId) ?: return false
        if (game.isMatchOver) return false
        val state = game.tableState
        val current = state.currentPlayer
        if (game.isAi(current.id) || current.id in game.forcedAutoPlayPlayerIds) return false
        if (!state.isDrawWindowOpenFor(current.id)) return false

        return gameFlowCoordinator.dispatch(gameId, current.id, GameCommand.Draw) is Outcome.Success
    }
}
