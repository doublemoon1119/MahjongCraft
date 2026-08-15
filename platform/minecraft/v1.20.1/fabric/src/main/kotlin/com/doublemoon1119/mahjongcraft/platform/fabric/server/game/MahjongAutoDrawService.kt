package com.doublemoon1119.mahjongcraft.platform.fabric.server.game

import com.doublemoon1119.mahjongcraft.flow.common.game.model.GameCommand
import com.doublemoon1119.mahjongcraft.flow.common.result.Outcome
import com.doublemoon1119.mahjongcraft.flow.server.game.orchestration.GameFlowCoordinator
import com.doublemoon1119.mahjongcraft.flow.server.game.repository.GameRepository
import com.doublemoon1119.mahjongcraft.logic.base.GameAction
import com.doublemoon1119.mahjongcraft.platform.minecraft.text.MinecraftPlayerFeedback
import com.doublemoon1119.mahjongcraft.platform.minecraft.text.MinecraftPlayerFeedbackPublisher
import org.koin.core.annotation.Single
import kotlin.uuid.Uuid

/**
 * 讓真人玩家的摸牌跟 AI 一樣由伺服器主動觸發，不需要玩家自己送出 [GameCommand.Draw]。
 *
 * `mahjong-flow` 的 `AiTurnDriver` 已經把「摸牌是每位玩家（人類/AI）回合開始時都必須做的機械動作」
 * 這件事直接回傳固定命令，但只對 AI 玩家生效；真人玩家維持等待玩家送出請求的假設。這是 Minecraft
 * 平台專屬的 UX 決策（開局全自動、伺服器主動摸牌），刻意不寫進 `mahjong-flow` 共用的
 * `AiTurnDriver`／`GameFlowCoordinator`，避免其他未來平台被迫採用同樣的互動方式。
 *
 * @property gameRepository 權威對局數據倉庫。
 * @property gameFlowCoordinator 對局命令的分派與自動銜接入口。
 * @property feedbackPublisher 摸牌成功後主動通知玩家輪到自己了——真人玩家的摸牌是全自動觸發，沒有
 *   這則通知玩家完全不會知道輪到自己，只能自己反覆查詢 `/mahjongcraft game hand`。
 */
@Single
class MahjongAutoDrawService(
    private val gameRepository: GameRepository,
    private val gameFlowCoordinator: GameFlowCoordinator,
    private val feedbackPublisher: MinecraftPlayerFeedbackPublisher,
) {
    /**
     * 檢查目前是不是輪到真人玩家、且尚未摸牌，是的話代替他送出 [GameCommand.Draw]，成功後發布
     * [MinecraftPlayerFeedback.YourTurn] 通知。
     *
     * 判斷邏輯比照 `AiTurnDriver.resolveNextAction` 對 AI 回合的既有判斷，只是條件反過來套用到真人：
     * 有反應視窗開著、目前玩家是 AI、已經摸過牌、或剛碰/吃成立準備直接捨牌時都不觸發。
     *
     * @param gameId 欲檢查的對局 Uuid。
     */
    suspend fun checkAndAutoDraw(gameId: Uuid) {
        val state = gameRepository.getTableState(gameId) ?: return
        if (state.pendingReaction != null || state.pendingChankan != null) return

        val current = state.currentPlayer
        if (current.isAi) return

        val justClaimedMeld = current.actionHistory.lastOrNull().let { it is GameAction.Pon || it is GameAction.Chi }
        if (current.hand.lastDrawn == null && !justClaimedMeld) {
            val result = gameFlowCoordinator(gameId, current.id, GameCommand.Draw)
            if (result is Outcome.Success) {
                val drawnTile = gameRepository.getTableState(gameId)
                    ?.players?.firstOrNull { it.id == current.id }
                    ?.hand?.lastDrawn?.tile
                    ?: return
                feedbackPublisher.publish(current.id, MinecraftPlayerFeedback.YourTurn(drawnTile))
            }
        }
    }
}
