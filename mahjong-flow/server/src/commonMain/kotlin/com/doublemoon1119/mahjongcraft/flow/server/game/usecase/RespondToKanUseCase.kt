package com.doublemoon1119.mahjongcraft.flow.server.game.usecase

import com.doublemoon1119.mahjongcraft.flow.common.di.createBuiltInWinCelebrationCueResolverRegistry
import com.doublemoon1119.mahjongcraft.flow.common.game.model.BuiltInRoundOutcomeIds
import com.doublemoon1119.mahjongcraft.flow.common.game.model.GameError
import com.doublemoon1119.mahjongcraft.flow.common.game.model.SettledWinPresentation
import com.doublemoon1119.mahjongcraft.flow.common.game.model.WinCelebrationRequest
import com.doublemoon1119.mahjongcraft.flow.common.game.model.WinCelebrationWinner
import com.doublemoon1119.mahjongcraft.flow.common.game.service.GameEventPublisher
import com.doublemoon1119.mahjongcraft.flow.common.game.service.GamePresentationPublisher
import com.doublemoon1119.mahjongcraft.flow.common.game.service.WinCelebrationCueResolverRegistry
import com.doublemoon1119.mahjongcraft.flow.common.game.service.toPresentation
import com.doublemoon1119.mahjongcraft.flow.common.result.Outcome
import com.doublemoon1119.mahjongcraft.flow.server.game.repository.GameRepository
import com.doublemoon1119.mahjongcraft.flow.server.game.service.GameSnapshotSynchronizer
import com.doublemoon1119.mahjongcraft.flow.server.game.service.WinPresentationHandoff
import com.doublemoon1119.mahjongcraft.flow.server.game.service.WinSettlementDetailResolverRegistry
import com.doublemoon1119.mahjongcraft.flow.server.game.service.WinSettlementPresentationRequestFactory
import com.doublemoon1119.mahjongcraft.flow.server.game.service.createBuiltInWinSettlementDetailResolverRegistry
import com.doublemoon1119.mahjongcraft.logic.base.GameAction
import com.doublemoon1119.mahjongcraft.logic.module.MahjongModuleRegistry
import com.doublemoon1119.mahjongcraft.logic.table.TableState
import org.koin.core.annotation.Factory
import org.koin.core.annotation.Provided
import kotlin.uuid.Uuid

/**
 * 回應搶槓反應視窗（榮和/過）的實例化用例。
 *
 * 名詞對照：**宣告者**（`pending.declarerId`）是剛剛宣告暗槓/加槓的那個人；**搶槓者**是這裡呼叫
 * [GameAction.Ron] 的其他玩家。搶槓的本質是「用宣告者這張要拿去槓的牌榮和」，所以搶槓成功時，
 * 宣告者的身分變成榮和結算裡的放銃者，不是槓的宣告者——這跟一般槓牌完全是兩回事。
 *
 * 對應 [DeclareKanUseCase] 開啟的 `TableState.pendingKanReaction`：每位有資格搶槓的玩家各自呼叫一次
 * 本用例記錄自己的回應，等全部人都回應完才結算，結算只有兩種結果：
 * - **有人搶槓成功**：這次暗槓/加槓視為沒發生，改用 [RonSettlementResolver] 結算榮和。
 * - **全員放過**：槓真的成立，這時才呼叫 [KanDeclarationApplier] 補做原本暫緩的副露套用，並讓
 *   宣告者摸嶺上牌——只有這個分支會摸牌，搶槓成功那個分支不會。
 *
 * 只信任 [GameAction.Ron]/[GameAction.Pass]：`LegalActionValidator.getLegalActions` 的「反應」
 * 分支不分辨 `sourceAction` 是捨牌還是槓牌，會一併算出吃/碰/明槓資格，但搶槓情境下這些都不合法
 * （這張牌不是捨牌）——回應驗證時額外過濾，只接受 `Ron`/`Pass`。
 *
 * @property gameRepository 權威對局數據倉庫。
 * @property moduleRegistry 麻將規則模組註冊中心，用於解析當前對局的合法動作判定器與規則特有邏輯。
 * @property snapshotSynchronizer 對局快照同步服務。
 * @property eventPublisher 對局通知服務。
 * @property presentationPublisher 對局 in-process 呈現觸發器。
 */
@Factory
class RespondToKanUseCase(
    private val gameRepository: GameRepository,
    private val moduleRegistry: MahjongModuleRegistry,
    private val snapshotSynchronizer: GameSnapshotSynchronizer,
    @Provided private val eventPublisher: GameEventPublisher,
    @Provided private val presentationPublisher: GamePresentationPublisher,
    private val winPresentationHandoff: WinPresentationHandoff,
    private val winCelebrationCueResolverRegistry: WinCelebrationCueResolverRegistry =
        createBuiltInWinCelebrationCueResolverRegistry(),
    private val winSettlementDetailResolverRegistry: WinSettlementDetailResolverRegistry =
        createBuiltInWinSettlementDetailResolverRegistry(),
) {
    /**
     * 執行搶槓反應回應邏輯。
     *
     * @param gameId 對局 Uuid。
     * @param playerId 發起回應的玩家 Uuid。
     * @param action 欲執行的回應動作（[GameAction.Pass] 或 [GameAction.Ron]）。
     * @return 回應結果，成功時為 [Unit]，失敗時為 [GameError]。
     */
    suspend operator fun invoke(gameId: Uuid, playerId: Uuid, action: GameAction): Outcome<Unit, GameError> {
        val outcome = gameRepository.update(gameId) { state ->
            val pending = state?.pendingKanReaction
            when {
                state == null -> state to Outcome.Error(GameError.GameNotFound(gameId))
                state.players.none { it.id == playerId } ->
                    state to Outcome.Error(GameError.PlayerNotInGame(playerId, gameId))
                pending == null ->
                    state to Outcome.Error(GameError.IllegalAction(playerId, gameId, action))
                playerId !in pending.eligiblePlayerIds ->
                    state to Outcome.Error(GameError.IllegalAction(playerId, gameId, action))
                playerId in pending.responses ->
                    state to Outcome.Error(GameError.IllegalAction(playerId, gameId, action))
                else -> {
                    val module = moduleRegistry.getModule(state.config)
                    val responder = state.players.first { it.id == playerId }
                    val legalActions = module.createLegalActionValidator().getLegalActions(
                        tableState = state,
                        player = responder,
                        sourceAction = pending.kanAction,
                        sourceDirection = state.relativeDirectionOf(playerId, pending.declarerId),
                        incomingTile = pending.robbedTile,
                    ).filter { it is GameAction.Ron || it == GameAction.Pass }
                    if (action !in legalActions) {
                        return@update state to Outcome.Error(GameError.IllegalAction(playerId, gameId, action))
                    }

                    val newPending = pending.copy(responses = pending.responses + (playerId to action))
                    if (!newPending.isComplete) {
                        val newState = state.copy(pendingKanReaction = newPending)
                        return@update newState to Outcome.Success(ChankanResult(newState, drawHappened = false))
                    }

                    val ronWinnerIds = newPending.responses.filterValues { it is GameAction.Ron }.keys
                    if (ronWinnerIds.isNotEmpty()) {
                        // 搶槓成功：這次槓視為沒發生，宣告者變成放銃者，改走榮和結算，不摸嶺上牌。
                        val resolved = RonSettlementResolver.resolve(
                            state = state,
                            players = state.players,
                            discarderId = pending.declarerId,
                            winningTile = pending.robbedTile,
                            module = module,
                            winnerIds = ronWinnerIds,
                            isRobbingKan = pending.kanAction.type == GameAction.KanType.ADDED_KAN,
                        )
                        val newState = resolved?.tableState?.copy(pendingKanReaction = null)
                            ?: state.copy(pendingKanReaction = newPending)
                        newState to Outcome.Success(
                            ChankanResult(
                                tableState = newState,
                                drawHappened = false,
                                ronWinnerIds = if (resolved != null) ronWinnerIds else emptySet(),
                                ronWinningTileId = if (resolved != null) pending.robbedTile.id else null,
                                ronResolutions = resolved?.resolutions.orEmpty(),
                                ruleModuleId = if (resolved != null) module.id else null,
                                previousTableState = if (resolved != null) state else null,
                                ronDiscarderId = if (resolved != null) pending.declarerId else null,
                            ),
                        )
                    } else {
                        // 全員放過：槓真的成立，補做副露套用，並讓宣告者摸嶺上牌。
                        val applied = KanDeclarationApplier.apply(state, pending.declarerId, pending.kanAction, pending.robbedTile, module)
                        if (applied.rinshanTile == null) {
                            return@update state to Outcome.Error(GameError.WallExhausted(gameId))
                        }
                        val newState = applied.tableState.copy(pendingKanReaction = null)
                        newState to Outcome.Success(
                            ChankanResult(
                                newState,
                                drawHappened = true,
                                declarerId = pending.declarerId,
                                newlyRevealedDeadWallTileIds = newlyRevealedDeadWallTileIds(state, newState),
                            ),
                        )
                    }
                }
            }
        }

        if (outcome is Outcome.Error) return outcome
        val result = (outcome as Outcome.Success).value
        val newState = result.tableState

        snapshotSynchronizer.syncAll(gameId)

        newState.players.forEach { player ->
            eventPublisher.publish(gameId, player.id, playerId, action)
            if (result.drawHappened) {
                eventPublisher.publish(gameId, player.id, playerId, GameAction.Draw)
            }
        }

        // declarerId 只在「全員放過、槓真的成立」時才有值（見上面 KanDeclarationApplier 那個分支）；
        // 這裡重新呈現宣告者的手牌/摸牌位/副露，把摸到的嶺上牌移到摸牌位（跟一般摸牌同一套呈現方式，
        // 見 DrawTileUseCase）。搶槓成功那個分支不會走到這裡。
        result.declarerId?.let { declarerId ->
            val declarerSeatIndex = newState.players.indexOfFirst { it.id == declarerId }
            val declarer = newState.players[declarerSeatIndex]
            val dealerSeatIndex = newState.dealerIndex
            presentationPublisher.publishPlayerAreaUpdated(
                gameId,
                declarerSeatIndex,
                declarer.hand.tiles.map { it.id },
                declarer.hand.lastDrawn?.id,
                declarer.hand.melds.map { it.toPresentation(newState.config.revealsClosedKanTiles) },
                comboStickCount = if (declarerSeatIndex == dealerSeatIndex) newState.comboCount else 0,
            )
            // 槓牌真的成立後可能翻開新的一張寶牌指示牌，理由同 DeclareKanUseCase。
            if (result.newlyRevealedDeadWallTileIds.isNotEmpty()) {
                presentationPublisher.publishDeadWallRevealUpdated(gameId, result.newlyRevealedDeadWallTileIds)
            }
        }

        // 建構胡牌演出內容並寫進交接槽——搶槓成功時 result.ronWinnerIds 可能不只一人，打包成同一筆；
        // winningTileId 對每位贏家來說都是同一張被搶的加槓/暗槓牌。刻意不直接發布，理由同 DeclareTsumoUseCase。
        result.ronWinningTileId?.let { winningTileId ->
            val module = moduleRegistry.getModule(newState.config)
            val presentation = SettledWinPresentation(
                winnerPlayerIds = result.ronWinnerIds,
                celebration = WinCelebrationRequest(
                    winningTileId,
                    isTsumo = false,
                    winners = result.ronWinnerIds.map { winnerId ->
                        WinCelebrationWinner(
                            newState.players.indexOfFirst { it.id == winnerId },
                            result.ronResolutions[winnerId]?.let {
                                winCelebrationCueResolverRegistry.resolve(result.ruleModuleId.orEmpty(), it.handValueResult)
                            },
                        )
                    },
                ),
                settlement = WinSettlementPresentationRequestFactory.create(
                    previousState = checkNotNull(result.previousTableState),
                    currentState = newState,
                    module = module,
                    outcomeId = BuiltInRoundOutcomeIds.RON,
                    isTsumo = false,
                    winningTileId = winningTileId,
                    responsiblePlayerId = result.ronDiscarderId,
                    resolutions = result.ronResolutions,
                    detailResolverRegistry = winSettlementDetailResolverRegistry,
                ),
            )
            winPresentationHandoff.stage(gameId, presentation)
        }

        return Outcome.Success(Unit)
    }

    /**
     * `update` 區塊內部用的中繼結果。[drawHappened] 為 true 代表「全員放過、槓真的成立」，這時才有
     * 嶺上摸牌，需要廣播 [GameAction.Draw]；[declarerId]（槓的宣告者）也只在這種情況才有值。[ronWinnerIds]／
     * [ronWinningTileId] 只在搶槓成功時非空，供呼叫端逐一觸發胡牌慶祝演出——跟 [declarerId] 是互斥的
     * 兩個視窗，同一次結算只會有其中一種非空。
     */
    private data class ChankanResult(
        val tableState: TableState,
        val drawHappened: Boolean,
        val declarerId: Uuid? = null,
        val ronWinnerIds: Set<Uuid> = emptySet(),
        val ronWinningTileId: Uuid? = null,
        val ronResolutions: Map<Uuid, com.doublemoon1119.mahjongcraft.logic.module.WinResolutionResult> = emptyMap(),
        val ruleModuleId: String? = null,
        val previousTableState: TableState? = null,
        val ronDiscarderId: Uuid? = null,
        val newlyRevealedDeadWallTileIds: Set<Uuid> = emptySet(),
    )
}
