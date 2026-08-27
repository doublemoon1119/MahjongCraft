package com.doublemoon1119.mahjongcraft.flow.server.game.usecase

import com.doublemoon1119.mahjongcraft.flow.common.game.model.GameError
import com.doublemoon1119.mahjongcraft.flow.common.game.model.WinRoundContinuationContext
import com.doublemoon1119.mahjongcraft.flow.common.game.model.WinRoundDirective
import com.doublemoon1119.mahjongcraft.flow.common.game.model.applyTo
import com.doublemoon1119.mahjongcraft.flow.common.result.Outcome
import com.doublemoon1119.mahjongcraft.flow.server.game.orchestration.WinRoundContinuationResolverRegistry
import com.doublemoon1119.mahjongcraft.flow.server.game.repository.GameRepository
import com.doublemoon1119.mahjongcraft.flow.server.game.service.GameSnapshotSynchronizer
import com.doublemoon1119.mahjongcraft.logic.base.GameAction
import com.doublemoon1119.mahjongcraft.logic.module.MahjongModuleRegistry
import com.doublemoon1119.mahjongcraft.logic.table.TableState
import org.koin.core.annotation.Factory
import kotlin.uuid.Uuid

/**
 * 一次胡牌（自摸／榮和，含搶槓）即時結算完成後，判定並套用本局後續的權威決策。
 *
 * 呼叫時機：[GameFlowCoordinator][com.doublemoon1119.mahjongcraft.flow.server.game.orchestration.GameFlowCoordinator]
 * 偵測到這次成功指令為某些玩家新增了 [GameAction.Tsumo]／[GameAction.Ron] 記錄後呼叫一次——
 * 一炮多響已經在 `RonSettlementResolver` 收斂成單一結算，因此 [winnerPlayerIds] 在此時已包含這次
 * 一起成立的所有贏家，本用例只呼叫一次 [WinRoundContinuationResolverRegistry.resolve]。
 *
 * 只負責「本局要不要繼續」這個權威決策，不碰任何呈現：[WinRoundDirective.EndRound] 不修改任何桌況；
 * [WinRoundDirective.ContinueRound] 原子套用 `finishedPlayerIds`／`currentPlayerIndex` 的變化並同步
 * 快照。呼叫端據此決定是否銜接 `AdvanceRoundUseCase`，以及這次胡牌演出要用什麼方式播放。
 *
 * @property gameRepository 權威對局數據倉庫。
 * @property moduleRegistry 麻將規則模組註冊中心，用於解析當前對局的規則模組。
 * @property resolverRegistry 供規則 extension 登記胡牌後續決策的 registry。
 * @property snapshotSynchronizer 對局快照同步服務。
 */
@Factory
class ResolveWinRoundContinuationUseCase(
    private val gameRepository: GameRepository,
    private val moduleRegistry: MahjongModuleRegistry,
    private val resolverRegistry: WinRoundContinuationResolverRegistry,
    private val snapshotSynchronizer: GameSnapshotSynchronizer,
) {
    /**
     * 原子判定並（若 [WinRoundDirective.ContinueRound]）套用本局後續決策。
     *
     * @param gameId 對局 Uuid。
     * @param previousTableState 這次胡牌指令送出前的桌況，用於還原放銃者／搶槓宣告者身分。
     * @param winnerPlayerIds 這次一起成立的所有贏家 Uuid。
     * @return 這次判定出的權威決策，找不到對局時為 [GameError]。
     */
    suspend operator fun invoke(
        gameId: Uuid,
        previousTableState: TableState,
        winnerPlayerIds: Set<Uuid>,
    ): Outcome<WinRoundDirective, GameError> {
        val result = gameRepository.updateGame(gameId) { game ->
            if (game == null) return@updateGame game to Outcome.Error(GameError.GameNotFound(gameId))
            val settledTableState = game.tableState
            val module = moduleRegistry.getModule(settledTableState.config)
            val context = buildContext(previousTableState, settledTableState, winnerPlayerIds)
            val directive = resolverRegistry.resolve(context, module)
            when (directive) {
                WinRoundDirective.EndRound -> game to Outcome.Success(directive)

                is WinRoundDirective.ContinueRound ->
                    game.copy(tableState = directive.applyTo(settledTableState)) to Outcome.Success(directive)
            }
        }
        if (result is Outcome.Success && result.value is WinRoundDirective.ContinueRound) {
            snapshotSynchronizer.syncAll(gameId)
        }
        return result
    }

    /**
     * 從結算前後的桌況重建 [WinRoundContinuationContext]——放銃者／搶槓宣告者身分要從
     * [previousTableState] 尚未清除的 `pendingReaction`／`pendingKanReaction` 還原（自摸時兩者皆為
     * null）；胡牌張則優先取任一贏家剛記錄的 [GameAction.Ron.tileId]，自摸時改用贏家的 `lastDrawn`
     * （[com.doublemoon1119.mahjongcraft.flow.server.game.usecase.DeclareTsumoUseCase] 不會清除它）。
     */
    private fun buildContext(
        previousTableState: TableState,
        settledTableState: TableState,
        winnerPlayerIds: Set<Uuid>,
    ): WinRoundContinuationContext {
        val anyWinner = settledTableState.players.first { it.id in winnerPlayerIds }
        val ronAction = anyWinner.actionHistory.lastOrNull { it is GameAction.Ron } as? GameAction.Ron
        val winningTileId = ronAction?.tileId
            ?: checkNotNull(anyWinner.hand.lastDrawn?.id) { "Tsumo winner must still hold the winning tile as lastDrawn" }
        return WinRoundContinuationContext(
            previousTableState = previousTableState,
            settledTableState = settledTableState,
            winnerPlayerIds = winnerPlayerIds,
            ronDiscarderId = previousTableState.pendingReaction?.discarderId ?: previousTableState.pendingKanReaction?.declarerId,
            winningTileId = winningTileId,
        )
    }
}
