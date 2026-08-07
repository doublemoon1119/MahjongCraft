package com.doublemoon1119.mahjongcraft.flow.server.game.usecase

import com.doublemoon1119.mahjongcraft.flow.common.game.model.GameError
import com.doublemoon1119.mahjongcraft.flow.common.game.repository.GameSnapshotRepository
import com.doublemoon1119.mahjongcraft.flow.common.game.service.GameEventPublisher
import com.doublemoon1119.mahjongcraft.flow.common.result.Outcome
import com.doublemoon1119.mahjongcraft.flow.server.game.repository.GameRepository
import com.doublemoon1119.mahjongcraft.logic.base.GameAction
import com.doublemoon1119.mahjongcraft.logic.base.IdentifiedTile
import com.doublemoon1119.mahjongcraft.logic.base.MeldType
import com.doublemoon1119.mahjongcraft.logic.module.MahjongModuleRegistry
import com.doublemoon1119.mahjongcraft.logic.module.MahjongRuleModule
import com.doublemoon1119.mahjongcraft.logic.table.MahjongPlayer
import com.doublemoon1119.mahjongcraft.logic.table.PendingReaction
import com.doublemoon1119.mahjongcraft.logic.table.TableState
import com.doublemoon1119.mahjongcraft.logic.table.toSnapshot
import com.doublemoon1119.mahjongcraft.logic.util.withoutRed
import org.koin.core.annotation.Factory
import org.koin.core.annotation.Provided
import kotlin.uuid.Uuid

/**
 * 回應捨牌反應視窗（吃/碰/明槓/過）的實例化用例。
 *
 * 對應 [DiscardTileUseCase] 捨牌後開啟的 `TableState.pendingReaction`：每位有資格的玩家各自呼叫
 * 一次本用例記錄自己的回應，直到所有有資格的玩家都回應完畢，才會實際結算。結算時的優先權規則：
 * 榮和 > 碰/明槓 > 吃（碰/明槓則是同一張牌結構上不可能被兩人同時碰/槓，故不需要更細緻的同類型排序；
 * 榮和則可能同時有多位贏家——見 [DiscardTileUseCase] 依 `MultiRonPolicy` 決定的一炮多響資格判斷，
 * 本用例只單純結算「這次收到幾筆榮和回應」，不重複判斷資格與人數限制）；若所有人都選擇過牌，
 * 則單純推進到下一位玩家，行為與捨牌時「無人可反應」的情況相同。
 *
 * 榮和結算後手牌即結束：本用例刻意只改動分數、贏家的 `actionHistory`，以及供託相關的動態規則狀態，
 * `currentPlayerIndex` 維持原樣不動（不像碰/吃/槓那樣把回合交給得標玩家），連莊/過莊/開下一局等
 * 後續流程留給未來獨立的單位。
 *
 * @property gameRepository 權威對局數據倉庫。
 * @property moduleRegistry 麻將規則模組註冊中心，用於解析當前對局的合法動作判定器與規則特有邏輯。
 * @property gameSnapshotRepository 對局快照數據倉庫。
 * @property eventPublisher 對局通知服務。
 */
@Factory
class RespondToDiscardUseCase(
    private val gameRepository: GameRepository,
    private val moduleRegistry: MahjongModuleRegistry,
    private val gameSnapshotRepository: GameSnapshotRepository,
    @Provided private val eventPublisher: GameEventPublisher,
) {
    /**
     * 執行捨牌反應回應邏輯。
     *
     * @param gameId 對局 Uuid。
     * @param playerId 發起回應的玩家 Uuid。
     * @param action 欲執行的回應動作（[GameAction.Pass]、[GameAction.Chi]、[GameAction.Pon]，
     *               或 [GameAction.KanType.OPEN_KAN] 型態的 [GameAction.Kan]）。
     * @return 回應結果，成功時為 [Unit]，失敗時為 [GameError]。
     */
    suspend operator fun invoke(gameId: Uuid, playerId: Uuid, action: GameAction): Outcome<Unit, GameError> {
        val outcome = gameRepository.update(gameId) { state ->
            val pendingReaction = state?.pendingReaction
            when {
                state == null -> state to Outcome.Error(GameError.GameNotFound(gameId))
                state.players.none { it.id == playerId } ->
                    state to Outcome.Error(GameError.PlayerNotInGame(playerId, gameId))
                pendingReaction == null ->
                    state to Outcome.Error(GameError.IllegalAction(playerId, gameId, action))
                playerId !in pendingReaction.eligiblePlayerIds ->
                    state to Outcome.Error(GameError.IllegalAction(playerId, gameId, action))
                playerId in pendingReaction.responses ->
                    state to Outcome.Error(GameError.IllegalAction(playerId, gameId, action))
                else -> {
                    val discarder = state.players.first { it.id == pendingReaction.discarderId }
                    val responder = state.players.first { it.id == playerId }
                    val discardedTile = discarder.discardPile.entries.first { it.tile.id == pendingReaction.tileId }.tile

                    val module = moduleRegistry.getModule(state.config)
                    val legalActions = module.createLegalActionValidator().getLegalActions(
                        tableState = state,
                        player = responder,
                        sourceAction = GameAction.Discard(pendingReaction.tileId),
                        sourceDirection = state.relativeDirectionOf(playerId, pendingReaction.discarderId),
                        incomingTile = discardedTile,
                    )
                    if (action !in legalActions) {
                        return@update state to Outcome.Error(GameError.IllegalAction(playerId, gameId, action))
                    }

                    // 過水：放過的當下若原本可以碰或榮和，記錄下來——碰用於本巡過水碰，
                    // 榮和用於同巡振聽判定（見 MahjongPlayer.passedTilesInRound 的用途說明）
                    val updatedResponder = if (action == GameAction.Pass &&
                        legalActions.any { it is GameAction.Pon || it is GameAction.Ron }
                    ) {
                        responder.addPassedTile(discardedTile.tile)
                    } else {
                        responder
                    }
                    val playersAfterResponse = state.players.map { if (it.id == playerId) updatedResponder else it }
                    val newPendingReaction = pendingReaction.copy(responses = pendingReaction.responses + (playerId to action))

                    val newState = if (!newPendingReaction.isComplete) {
                        state.copy(players = playersAfterResponse, pendingReaction = newPendingReaction)
                    } else {
                        resolvePendingReaction(state, playersAfterResponse, newPendingReaction, discardedTile, module)
                    }

                    newState to Outcome.Success(newState)
                }
            }
        }

        if (outcome is Outcome.Error) return outcome
        val newState = (outcome as Outcome.Success).value

        val observers = gameSnapshotRepository.getAllObservers(gameId)
        observers.forEach { observerId ->
            gameSnapshotRepository.setSnapshot(observerId, newState.toSnapshot(observerId))
        }

        newState.players.forEach { player ->
            eventPublisher.publish(gameId, player.id, playerId, action)
        }

        return Outcome.Success(Unit)
    }

    /**
     * 所有有資格的玩家皆已回應，進行結算：找出優先權最高的非過牌回應套用鳴牌，或在全員過牌時單純推進回合。
     */
    private fun resolvePendingReaction(
        state: TableState,
        players: List<MahjongPlayer>,
        pendingReaction: PendingReaction,
        discardedTile: IdentifiedTile,
        module: MahjongRuleModule<*>,
    ): TableState {
        val ronWinnerIds = pendingReaction.responses.filterValues { it is GameAction.Ron }.keys
        if (ronWinnerIds.isNotEmpty()) {
            val resolved = RonSettlementResolver.resolve(
                state = state,
                players = players,
                discarderId = pendingReaction.discarderId,
                winningTile = discardedTile,
                module = module,
                winnerIds = ronWinnerIds,
            )
            return resolved?.copy(pendingReaction = null)
                ?: state.copy(players = players, pendingReaction = pendingReaction)
        }

        val winningEntry = pendingReaction.responses.entries.firstOrNull { it.value is GameAction.Pon || it.value is GameAction.Kan }
            ?: pendingReaction.responses.entries.firstOrNull { it.value is GameAction.Chi }

        if (winningEntry == null) {
            // 所有人皆過牌：行為與捨牌時「無人可反應」相同，直接推進到下一位玩家
            val discarderIndex = players.indexOfFirst { it.id == pendingReaction.discarderId }
            return state.copy(
                players = players,
                currentPlayerIndex = (discarderIndex + 1) % state.playerCount,
                pendingReaction = null,
            )
        }

        val winnerId = winningEntry.key
        val winnerAction = winningEntry.value
        val winner = players.first { it.id == winnerId }
        val winnerDirection = state.relativeDirectionOf(winnerId, pendingReaction.discarderId)

        val meldType = when (winnerAction) {
            is GameAction.Chi -> MeldType.CHI
            is GameAction.Pon -> MeldType.PON
            is GameAction.Kan -> MeldType.OPEN_KAN
            else -> error("Unreachable: only Chi/Pon/Kan can win a discard reaction")
        }
        val handTilesUsed: List<IdentifiedTile> = when (winnerAction) {
            is GameAction.Chi -> winnerAction.withTiles.mapNotNull { id -> winner.hand.standingTiles.find { it.id == id } }
            is GameAction.Kan -> winnerAction.withTiles.mapNotNull { id -> winner.hand.standingTiles.find { it.id == id } }
            is GameAction.Pon ->
                winner.hand.standingTiles
                    .filter { it.tile.withoutRed == discardedTile.tile.withoutRed }
                    .take(2)
        }

        val winnerWithPao = if (meldType == MeldType.PON || meldType == MeldType.OPEN_KAN) {
            module.applyPaoLiabilityIfTriggered(winner, discardedTile, winnerDirection)
        } else {
            winner
        }
        val winnerAfterMeld = winnerWithPao
            .copy(hand = winnerWithPao.hand.call(meldType, handTilesUsed + discardedTile, discardedTile, winnerDirection))
            .recordAction(winnerAction)

        val playersAfterMeld = players.map { player ->
            when (player.id) {
                winnerId -> winnerAfterMeld
                pendingReaction.discarderId -> player.copy(discardPile = player.discardPile.takeLast())
                else -> player
            }
        }
        val playersAfterMeldClaimed = module.onMeldClaimed(playersAfterMeld)
        val winnerIndex = playersAfterMeldClaimed.indexOfFirst { it.id == winnerId }

        if (meldType != MeldType.OPEN_KAN) {
            return state.copy(
                players = playersAfterMeldClaimed,
                currentPlayerIndex = winnerIndex,
                pendingReaction = null,
            )
        }

        // 明槓比照暗槓/加槓，得標後立即從死牌區補摸嶺上牌，取代原本依賴得標玩家事後另外呼叫
        // DrawTileUseCase（那是從牌山前端摸牌，摸錯位置）的既有錯誤行為。若牌山恰好在此刻摸盡
        // （極端邊界情況），這裡沒有 Outcome 通道可回報錯誤（本方法回傳單純 TableState），
        // 已知簡化：讓 lastDrawn 維持空，不中斷已經套用完成的副露結果。
        val (rinshanTile, newWall) = state.tileWall.drawLast()
        val playersWithRinshanDraw = if (rinshanTile == null) {
            playersAfterMeldClaimed
        } else {
            playersAfterMeldClaimed.map { player ->
                if (player.id == winnerId) {
                    player.copy(hand = player.hand.copy(lastDrawn = rinshanTile)).clearPassedTiles().recordAction(GameAction.Draw)
                } else {
                    player
                }
            }
        }

        return state.copy(
            players = playersWithRinshanDraw,
            currentPlayerIndex = winnerIndex,
            pendingReaction = null,
            tileWall = if (rinshanTile == null) state.tileWall else newWall,
        )
    }
}
