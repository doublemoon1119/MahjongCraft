package com.doublemoon1119.mahjongcraft.flow.server.game.usecase

import com.doublemoon1119.mahjongcraft.flow.common.di.createBuiltInWinCelebrationCueResolverRegistry
import com.doublemoon1119.mahjongcraft.flow.common.game.model.GameError
import com.doublemoon1119.mahjongcraft.flow.common.game.model.WinCelebrationRequest
import com.doublemoon1119.mahjongcraft.flow.common.game.model.WinCelebrationWinner
import com.doublemoon1119.mahjongcraft.flow.common.game.service.GameEventPublisher
import com.doublemoon1119.mahjongcraft.flow.common.game.service.GamePresentationPublisher
import com.doublemoon1119.mahjongcraft.flow.common.game.service.WinCelebrationCueResolverRegistry
import com.doublemoon1119.mahjongcraft.flow.common.result.Outcome
import com.doublemoon1119.mahjongcraft.flow.server.game.repository.GameRepository
import com.doublemoon1119.mahjongcraft.flow.server.game.service.GameSnapshotSynchronizer
import com.doublemoon1119.mahjongcraft.logic.base.GameAction
import com.doublemoon1119.mahjongcraft.logic.base.RelativeDirection
import com.doublemoon1119.mahjongcraft.logic.module.MahjongModuleRegistry
import com.doublemoon1119.mahjongcraft.logic.module.MahjongRuleModule
import org.koin.core.annotation.Factory
import org.koin.core.annotation.Provided
import kotlin.uuid.Uuid

/**
 * 宣告自摸（Tsumo）胡牌的實例化用例。
 *
 * 這是「胡牌結算」系列 use case 中最先實作的一片：只處理自摸造成的點數異動、供託（如立直棒）歸屬
 * 與事件廣播，刻意不處理榮和（含一炮多響），也不處理一局結束後的連莊/過莊/開新局等後續流程——
 * 那些留給未來各自獨立的 use case 處理。因此本 use case 執行完成後，`TableState` 被改變的部分只有
 * 玩家分數（與贏家的 `actionHistory`）以及供託相關的動態規則狀態；`currentPlayerIndex`、
 * `pendingReaction` 則刻意維持原樣不動。
 *
 * 自摸是否合法（含最低番數限制）、贏家實際獲得多少點數、其他玩家各自要付多少點數，這些規則相關
 * 的判斷完全交給 [MahjongRuleModule.declareTsumo]
 * 處理——這裡刻意不轉型成任何規則專屬的具體型別，理由與 [DeclareRiichiUseCase] 相同。
 *
 * @property gameRepository 權威對局數據倉庫。
 * @property moduleRegistry 麻將規則模組註冊中心，用於解析當前對局的合法動作判定器與點數結算邏輯。
 * @property snapshotSynchronizer 對局快照同步服務。
 * @property eventPublisher 對局通知服務。
 * @property presentationPublisher 對局 in-process 呈現觸發器，用於觸發胡牌慶祝演出。
 */
@Factory
class DeclareTsumoUseCase(
    private val gameRepository: GameRepository,
    private val moduleRegistry: MahjongModuleRegistry,
    private val snapshotSynchronizer: GameSnapshotSynchronizer,
    @Provided private val eventPublisher: GameEventPublisher,
    @Provided private val presentationPublisher: GamePresentationPublisher,
    private val winCelebrationCueResolverRegistry: WinCelebrationCueResolverRegistry =
        createBuiltInWinCelebrationCueResolverRegistry(),
) {
    /**
     * 執行自摸宣告邏輯。
     *
     * @param gameId 對局 Uuid。
     * @param playerId 發起自摸宣告的玩家 Uuid。
     * @return 宣告結果，成功時為 [Unit]，失敗時為 [GameError]。
     */
    suspend operator fun invoke(gameId: Uuid, playerId: Uuid): Outcome<Unit, GameError> {
        // 1. 以原子方式讀取桌況、驗證業務規則並寫回
        val outcome = gameRepository.update(gameId) { state ->
            when {
                state == null -> state to Outcome.Error(GameError.GameNotFound(gameId))
                state.players.none { it.id == playerId } ->
                    state to Outcome.Error(GameError.PlayerNotInGame(playerId, gameId))
                state.currentPlayer.id != playerId ->
                    state to Outcome.Error(GameError.NotPlayersTurn(playerId, gameId))
                state.currentPlayer.hand.lastDrawn == null ->
                    state to Outcome.Error(GameError.IllegalAction(playerId, gameId, GameAction.Tsumo))
                else -> {
                    val winningTile = state.currentPlayer.hand.lastDrawn
                        ?: return@update state to Outcome.Error(GameError.IllegalAction(playerId, gameId, GameAction.Tsumo))

                    val module = moduleRegistry.getModule(state.config)

                    // LegalActionValidator 的既有慣例是傳入的手牌「不含胡牌張」，胡牌張只透過
                    // incomingTile 參數單獨傳入；currentPlayer.hand 此時 lastDrawn 已經是胡牌張，
                    // 這裡剝離避免重複計數（否則手牌張數會對不上，導致自摸永遠被判定為不合法）。
                    val playerForLegalCheck = state.currentPlayer.copy(
                        hand = state.currentPlayer.hand.copy(lastDrawn = null),
                    )
                    val legalActions = module.createLegalActionValidator().getLegalActions(
                        tableState = state,
                        player = playerForLegalCheck,
                        sourceAction = GameAction.Draw,
                        sourceDirection = RelativeDirection.Self,
                        incomingTile = winningTile,
                    )
                    if (legalActions.none { it is GameAction.Tsumo }) {
                        return@update state to Outcome.Error(GameError.IllegalAction(playerId, gameId, GameAction.Tsumo))
                    }

                    // 這個規則不支援自摸結算時 declareTsumo 回傳 null。理論上不會走到
                    // 這裡，因為上面的 legalActions 檢查已經先擋下了；僅作防呆。
                    val tsumoResult = module.declareTsumo(state, state.currentPlayer)
                        ?: return@update state to Outcome.Error(GameError.IllegalAction(playerId, gameId, GameAction.Tsumo))

                    // 贏家同時收下場上所有供託（如立直棒），不支援此機制的規則回傳 null
                    val stickPot = module.collectStickPot(state)

                    val updatedWinner = state.currentPlayer
                        .copy(score = state.currentPlayer.score + tsumoResult.totalGained + (stickPot?.second ?: 0))
                        .recordAction(GameAction.Tsumo)
                    val updatedPlayers = state.players.map { p ->
                        when {
                            p.id == playerId -> updatedWinner
                            else -> tsumoResult.paymentsByPlayerId[p.id]
                                ?.let { payment -> p.copy(score = p.score - payment) }
                                ?: p
                        }
                    }
                    val newState = state.copy(
                        players = updatedPlayers,
                        dynamicRuleState = stickPot?.first ?: state.dynamicRuleState,
                    )

                    newState to Outcome.Success(TsumoResult(newState, tsumoResult.handValueResult, module.id))
                }
            }
        }

        if (outcome is Outcome.Error) return outcome
        val result = (outcome as Outcome.Success).value
        val newState = result.tableState

        // 2. 同步快照給所有正在觀察的玩家
        snapshotSynchronizer.syncAll(gameId)

        // 3. 通知對局內的所有玩家：廣播自摸事件
        newState.players.forEach { player ->
            eventPublisher.publish(gameId, player.id, playerId, GameAction.Tsumo)
        }

        // 4. 觸發平台呈現層：胡牌慶祝演出——手牌不受這個 use case 影響（只改分數與 actionHistory），
        // 贏家的 lastDrawn 此時仍是自摸那張牌。
        val winnerSeatIndex = newState.players.indexOfFirst { it.id == playerId }
        newState.players[winnerSeatIndex].hand.lastDrawn?.let { winningTile ->
            presentationPublisher.publishWinCelebration(
                gameId,
                WinCelebrationRequest(
                    winningTileId = winningTile.id,
                    isTsumo = true,
                    winners = listOf(
                        WinCelebrationWinner(
                            winnerSeatIndex,
                            winCelebrationCueResolverRegistry.resolve(result.ruleModuleId, result.handValueResult),
                        ),
                    ),
                ),
            )
        }

        return Outcome.Success(Unit)
    }

    /** 將原子更新內取得的算役結果帶到呈現發布階段。 */
    private data class TsumoResult(
        val tableState: com.doublemoon1119.mahjongcraft.logic.table.TableState,
        val handValueResult: com.doublemoon1119.mahjongcraft.logic.judgment.HandValueResult,
        val ruleModuleId: String,
    )
}
