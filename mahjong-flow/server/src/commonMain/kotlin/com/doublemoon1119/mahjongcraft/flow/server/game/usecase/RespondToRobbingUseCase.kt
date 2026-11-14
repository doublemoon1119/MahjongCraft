package com.doublemoon1119.mahjongcraft.flow.server.game.usecase

import com.doublemoon1119.mahjongcraft.flow.common.game.history.HistoryEventDraft
import com.doublemoon1119.mahjongcraft.flow.common.game.history.HistoryFact
import com.doublemoon1119.mahjongcraft.flow.common.game.model.AppliedActionScoring
import com.doublemoon1119.mahjongcraft.flow.common.game.model.BuiltInRoundOutcomeIds
import com.doublemoon1119.mahjongcraft.flow.common.game.model.GameError
import com.doublemoon1119.mahjongcraft.flow.common.game.model.SettledWinPresentation
import com.doublemoon1119.mahjongcraft.flow.common.game.model.WinCelebrationRequest
import com.doublemoon1119.mahjongcraft.flow.common.game.model.WinCelebrationWinner
import com.doublemoon1119.mahjongcraft.flow.common.game.model.WinSettlementPresentationRequest
import com.doublemoon1119.mahjongcraft.flow.common.game.service.GameEventPublisher
import com.doublemoon1119.mahjongcraft.flow.common.game.service.GamePresentationPublisher
import com.doublemoon1119.mahjongcraft.flow.common.game.service.TableStep
import com.doublemoon1119.mahjongcraft.flow.common.game.service.TableStepEvent
import com.doublemoon1119.mahjongcraft.flow.common.game.service.WinCelebrationCueResolverRegistry
import com.doublemoon1119.mahjongcraft.flow.common.result.Outcome
import com.doublemoon1119.mahjongcraft.flow.server.game.history.acceptedActionHistoryDraft
import com.doublemoon1119.mahjongcraft.flow.server.game.history.actionScoredHistoryDraft
import com.doublemoon1119.mahjongcraft.flow.server.game.history.winSettlementHistoryDetails
import com.doublemoon1119.mahjongcraft.flow.server.game.repository.GameRepository
import com.doublemoon1119.mahjongcraft.flow.server.game.service.GameSnapshotSynchronizer
import com.doublemoon1119.mahjongcraft.flow.server.game.service.WinPresentationHandoff
import com.doublemoon1119.mahjongcraft.flow.server.game.service.WinSettlementDetailResolverRegistry
import com.doublemoon1119.mahjongcraft.flow.server.game.service.WinSettlementPresentationRequestFactory
import com.doublemoon1119.mahjongcraft.logic.base.GameAction
import com.doublemoon1119.mahjongcraft.logic.base.IdentifiedTile
import com.doublemoon1119.mahjongcraft.logic.component.ComponentRegistry
import com.doublemoon1119.mahjongcraft.logic.judgment.LegalActionContext
import com.doublemoon1119.mahjongcraft.logic.module.MahjongModuleRegistry
import com.doublemoon1119.mahjongcraft.logic.module.WinResolutionResult
import com.doublemoon1119.mahjongcraft.logic.table.DrawSource
import com.doublemoon1119.mahjongcraft.logic.table.SupplementalDrawReasonIds
import com.doublemoon1119.mahjongcraft.logic.table.TableState
import com.doublemoon1119.mahjongcraft.logic.table.TurnPhase
import com.doublemoon1119.mahjongcraft.logic.table.WallRevealCheckpoint
import kotlin.uuid.Uuid

/**
 * 回應搶和反應視窗（榮和/過）的實例化用例。
 *
 * 搶和視窗由 [DeclareKanUseCase]（暗槓／加槓）或 [DeclareTileSetAsideUseCase]（移出手牌的擴充動作，例如三人日麻的拔北）
 * 開啟，存在 `TableState.pendingRobbingReaction`。
 *
 * 名詞對照：**宣告者**（`pending.declarerId`）是剛剛宣告動作的那個人；**搶和者**是這裡呼叫
 * [GameAction.Ron] 的其他玩家。搶和的本質是「用宣告者這張要拿去槓或移出的牌榮和」，所以搶和成功時，
 * 宣告者的身分變成榮和結算裡的放銃者。
 *
 * 每位有資格搶和的玩家各自呼叫一次本用例記錄自己的回應，等全部人都回應完才結算，結算只有兩種結果：
 * - **有人搶和成功**：這次宣告的動作視為沒發生，改用 [RonSettlementResolver] 結算榮和；只有搶加槓計搶槓。
 * - **全員放過**：動作真的成立，這時才呼叫 [SelfDeclarationApplier] 補做原本暫緩的套用與收付，並讓
 *   宣告者摸嶺上牌——只有這個分支會摸牌，搶和成功那個分支不會。補到的牌再交給 [DrawFollowUpApplier] 依規則自動處理，
 *   自動處理可能再開一次搶和視窗。
 *
 * 只信任 [GameAction.Ron]/[GameAction.Pass]：`LegalActionValidator.getLegalActions` 的「反應」
 * 分支不分辨 `sourceAction` 是不是捨牌，會一併算出吃/碰/明槓資格，但搶和情境下這些都不合法
 * （這張牌不是捨牌）——回應驗證時額外過濾，只接受 `Ron`/`Pass`。
 *
 * @property componentRegistry 正式組件定義與純效果來源。
 * @property gameRepository 權威對局數據倉庫。
 * @property moduleRegistry 麻將規則模組註冊中心，用於解析當前對局的合法動作判定器與規則特有邏輯。
 * @property snapshotSynchronizer 對局快照同步服務。
 * @property eventPublisher 對局通知服務。
 * @property presentationPublisher 對局 in-process 呈現觸發器。
 * @property winPresentationHandoff 胡牌呈現 request 的交易後交接點。
 * @property winCelebrationCueResolverRegistry 胡牌慶祝 cue 解析器 registry。
 * @property winSettlementDetailResolverRegistry 胡牌詳情解析器 registry。
 */
class RespondToRobbingUseCase(
    private val gameRepository: GameRepository,
    private val moduleRegistry: MahjongModuleRegistry,
    private val snapshotSynchronizer: GameSnapshotSynchronizer,
    private val eventPublisher: GameEventPublisher,
    private val presentationPublisher: GamePresentationPublisher,
    private val winPresentationHandoff: WinPresentationHandoff,
    private val winCelebrationCueResolverRegistry: WinCelebrationCueResolverRegistry,
    private val winSettlementDetailResolverRegistry: WinSettlementDetailResolverRegistry,
    private val componentRegistry: ComponentRegistry,
) {
    /**
     * 執行搶和反應回應邏輯。
     *
     * @param gameId 對局 Uuid。
     * @param playerId 發起回應的玩家 Uuid。
     * @param action 欲執行的回應動作（[GameAction.Pass] 或 [GameAction.Ron]）。
     * @return 回應結果，成功時為 [Unit]，失敗時為 [GameError]。
     */
    suspend operator fun invoke(gameId: Uuid, playerId: Uuid, action: GameAction): Outcome<Unit, GameError> {
        // AI 名單整場對局不變，先取出供結算呈現使用。
        val aiPlayerIds = gameRepository.getGame(gameId)?.aiPlayerIds.orEmpty()
        val outcome = gameRepository.update(
            gameId = gameId,
            history = { before, after, result ->
                if (result is Outcome.Success && before != null && after != null) {
                    listOf(
                        element = acceptedActionHistoryDraft(
                            actorPlayerId = playerId,
                            action = action,
                            before = before,
                            after = after,
                            affectedTileIds = action.affectedTileIds(),
                        ),
                    ) + if (result.value.windowResolved) {
                        val resolution = result.value
                        val resolvedActor = resolution.declarerId ?: resolution.ronWinnerIds.singleOrNull()
                        buildList {
                            add(
                                element = HistoryEventDraft(
                                    actorPlayerId = resolvedActor,
                                    fact = HistoryFact.ReactionResolved(
                                        resolvedAction = if (resolution.declarerId != null) {
                                            before.pendingRobbingReaction?.declaredAction
                                        } else {
                                            resolution.ronWinningTileId?.let(GameAction::Ron)
                                        },
                                        actorPlayerId = resolvedActor,
                                    ),
                                ),
                            )
                            resolution.declarerId?.let { declarerId ->
                                resolution.scoring?.let { add(actionScoredHistoryDraft(it)) }
                                if (resolution.drawnTiles.isNotEmpty()) {
                                    add(
                                        element = acceptedActionHistoryDraft(
                                            actorPlayerId = declarerId,
                                            action = GameAction.Draw,
                                            before = before,
                                            after = after,
                                            affectedTileIds = resolution.drawnTiles.map { it.id },
                                        ),
                                    )
                                }
                                resolution.followUp?.let { addAll(it.historyDrafts(declarerId, before, after)) }
                            }
                            resolution.settlement?.let { settlement ->
                                add(
                                    element = HistoryEventDraft(
                                        actorPlayerId = resolution.ronDiscarderId,
                                        fact = HistoryFact.WinSettled(
                                            outcomeId = settlement.outcomeId,
                                            winDetails = winSettlementHistoryDetails(settlement),
                                            responsiblePlayerIds = listOfNotNull(resolution.ronDiscarderId),
                                        ),
                                    ),
                                )
                            }
                        }
                    } else {
                        emptyList()
                    }
                } else {
                    emptyList()
                }
            },
        ) { state ->
            val pending = state?.pendingRobbingReaction
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
                        context = LegalActionContext.declarationReaction(
                            tableState = state,
                            playerId = playerId,
                            declarerId = pending.declarerId,
                            declaredAction = pending.declaredAction,
                            declaredTile = pending.robbedTile,
                        ),
                    ).filter { it is GameAction.Ron || it == GameAction.Pass }
                    if (action !in legalActions) {
                        return@update state to Outcome.Error(GameError.IllegalAction(playerId, gameId, action))
                    }

                    // 放過原本合法的搶槓：後果交給規則決定，見 MahjongRuleModule.onPlayerDeclinedWin。
                    val stateAfterResponse = if (action == GameAction.Pass && legalActions.any { it is GameAction.Ron }) {
                        val declinedResponder = RuleComponentChecker(componentRegistry, module.id).applyPlayerPatch(
                            tableState = state,
                            player = responder,
                            patch = module.onPlayerDeclinedWin(responder),
                            source = "onPlayerDeclinedWin",
                        )
                        state.copy(players = state.players.map { if (it.id == playerId) declinedResponder else it })
                    } else {
                        state
                    }
                    val newPending = pending.copy(responses = pending.responses + (playerId to action))
                    if (!newPending.isComplete) {
                        val newState = stateAfterResponse.copy(turnPhase = TurnPhase.AwaitingRobbingReaction(newPending))
                        return@update newState to Outcome.Success(RobbingResult(newState))
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
                            isRobbingKan = (pending.declaredAction as? GameAction.Kan)?.type == GameAction.KanType.ADDED_KAN,
                        )
                        val settledState = resolved.tableState
                        val revealResult = WallRevealDecisionApplier.apply(
                            tableState = settledState,
                            checkpoint = WallRevealCheckpoint.WIN_CONFIRMED,
                            module = module,
                            sourceAction = GameAction.Ron(pending.robbedTile.id),
                            componentRegistry = componentRegistry,
                        )
                        if (revealResult is WallRevealDecisionApplier.Result.Rejected) {
                            return@update state to Outcome.Error(
                                error = GameError.UnsupportedAction(gameId, playerId, revealResult.reasonId),
                            )
                        }
                        revealResult as WallRevealDecisionApplier.Result.Applied
                        val newState = revealResult.tableState
                        val settlement = WinSettlementPresentationRequestFactory.create(
                            previousState = state,
                            currentState = newState,
                            module = module,
                            aiPlayerIds = aiPlayerIds,
                            outcomeId = BuiltInRoundOutcomeIds.RON,
                            isTsumo = false,
                            winningTileId = pending.robbedTile.id,
                            responsiblePlayerId = pending.declarerId,
                            resolutions = resolved.resolutions,
                            detailResolverRegistry = winSettlementDetailResolverRegistry,
                        )
                        newState to Outcome.Success(
                            value = RobbingResult(
                                tableState = newState,
                                ronWinnerIds = ronWinnerIds,
                                ronWinningTileId = pending.robbedTile.id,
                                ronResolutions = resolved.resolutions,
                                ruleModuleId = module.id,
                                ronDiscarderId = pending.declarerId,
                                settlement = settlement,
                            ),
                        )
                    } else {
                        // 全員放過：可以搶槓卻沒有榮和的玩家（含一炮多響設定下沒被詢問的玩家）記入被搶的牌，
                        // 槓真的成立，補做副露套用，並讓宣告者摸嶺上牌。
                        val passedPlayerIds = newPending.responses.keys + RobbingEligibility.ronEligiblePlayerIds(
                            tableState = state,
                            declarerId = pending.declarerId,
                            declaredAction = pending.declaredAction,
                            robbedTile = pending.robbedTile,
                            module = module,
                        )
                        val stateAfterPasses = PassedTileRecorder.record(
                            tableState = stateAfterResponse,
                            tile = pending.robbedTile,
                            playerIds = passedPlayerIds,
                            module = module,
                        )
                        val applied = when (val declaredAction = pending.declaredAction) {
                            is GameAction.Kan -> SelfDeclarationApplier.apply(
                                state = stateAfterPasses,
                                declarerId = pending.declarerId,
                                kanAction = declaredAction,
                                incomingTile = pending.robbedTile,
                                module = module,
                                componentRegistry = componentRegistry,
                            )
                            is GameAction.Extension -> SelfDeclarationApplier.applyTileSetAside(
                                state = stateAfterPasses,
                                declarerId = pending.declarerId,
                                action = declaredAction,
                                module = module,
                                componentRegistry = componentRegistry,
                            )
                            else -> SelfDeclarationApplier.Result.Rejected(SupplementalDrawReasonIds.INVALID_RESULT)
                        }
                        if (applied is SelfDeclarationApplier.Result.Rejected) {
                            val error = if (applied.reasonId == SupplementalDrawReasonIds.WALL_EXHAUSTED) {
                                GameError.WallExhausted(gameId)
                            } else {
                                GameError.UnsupportedAction(gameId, pending.declarerId, applied.reasonId)
                            }
                            return@update state to Outcome.Error(error)
                        }
                        applied as SelfDeclarationApplier.Result.Applied
                        val appliedState = applied.tableState.copy(turnPhase = TurnPhase.AwaitingAction)
                        val followUp = if (applied.drawnTiles.isNotEmpty()) {
                            // 暫停前還沒詢問過的牌照原本的來源排在這次補到的牌之前。
                            val heldTiles = appliedState.players.first { it.id == pending.declarerId }.hand.standingTiles.associateBy { it.id }
                            DrawFollowUpApplier.run(
                                state = appliedState,
                                playerId = pending.declarerId,
                                source = DrawSource.Supplemental(pending.declaredAction),
                                drawnTiles = applied.drawnTiles,
                                module = module,
                                resumedDraws = pending.pendingFollowUpDraws.mapNotNull { draw -> heldTiles[draw.tileId]?.let { it to draw.source } },
                                componentRegistry = componentRegistry,
                            )
                        } else {
                            null
                        }
                        val newState = followUp?.tableState ?: appliedState
                        newState to Outcome.Success(
                            value = RobbingResult(
                                tableState = newState,
                                drawnTiles = applied.drawnTiles,
                                scoring = applied.scoring,
                                followUp = followUp,
                                declarerId = pending.declarerId,
                                // 宣告在開啟搶和視窗時已經呈現，這裡從收付與補牌接續。
                                steps = applied.tableSteps(
                                    playerId = pending.declarerId,
                                    action = pending.declaredAction,
                                    followUp = followUp,
                                    announced = true,
                                    drawnState = appliedState,
                                ),
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

        val seatedPlayerIds = newState.players.map { it.id }
        val ronWinningTileId = result.ronWinningTileId
        when {
            ronWinningTileId != null -> result.ronWinnerIds.forEach { winnerId ->
                eventPublisher.publishToAllObservers(
                    gameId = gameId,
                    seatedPlayerIds = seatedPlayerIds,
                    actorId = winnerId,
                    action = GameAction.Ron(ronWinningTileId),
                )
            }
        }
        result.declarerId?.let { declarerId ->
            result.followUp?.let { followUp ->
                eventPublisher.publishToAllObservers(gameId, seatedPlayerIds, declarerId, GameAction.Draw)
                followUp.publishedActions.forEach { followUpAction ->
                    eventPublisher.publishToAllObservers(gameId, seatedPlayerIds, declarerId, followUpAction)
                }
            }
        }

        // 呈現回應的結果：全員放過時接續收付、補牌與補牌後的自動處理；搶和成立時宣告和牌。
        val steps = result.steps ?: listOf(
            element = TableStep(
                tableState = newState,
                events = result.ronWinningTileId?.let { tileId ->
                    result.ronWinnerIds.map { winnerId -> TableStepEvent.ActionDeclared(winnerId, GameAction.Ron(tileId)) }
                }.orEmpty(),
            ),
        )
        presentationPublisher.publishTableSteps(gameId, steps)

        // 建構胡牌演出內容並寫進交接槽——搶槓成功時 result.ronWinnerIds 可能不只一人，打包成同一筆；
        // winningTileId 對每位贏家來說都是同一張被搶的加槓/暗槓牌。刻意不直接發布，理由同 DeclareTsumoUseCase。
        result.ronWinningTileId?.let { winningTileId ->
            val presentation = SettledWinPresentation(
                winnerPlayerIds = result.ronWinnerIds,
                celebration = WinCelebrationRequest(
                    winningTileId = winningTileId,
                    isTsumo = false,
                    revealsWinnerHands = true,
                    winners = result.ronWinnerIds.map { winnerId ->
                        WinCelebrationWinner(
                            seatIndex = newState.players.indexOfFirst { it.id == winnerId },
                            cueIds = result.ronResolutions[winnerId]?.let {
                                winCelebrationCueResolverRegistry.resolve(result.ruleModuleId.orEmpty(), it.handValueResult)
                            }.orEmpty(),
                        )
                    },
                ),
                settlement = checkNotNull(result.settlement),
            )
            winPresentationHandoff.stage(gameId, presentation)
        }

        return Outcome.Success(Unit)
    }

    /** 搶槓回應動作直接涉及的牌；過牌沒有牌面變更。 */
    private fun GameAction.affectedTileIds(): List<Uuid> = when (this) {
        is GameAction.Ron -> listOf(tileId)
        else -> emptyList()
    }

    /**
     * `update` 區塊內部用的中繼結果。[declarerId]（槓的宣告者）只在「全員放過、槓真的成立」時才有值，這時才有
     * 嶺上摸牌與收付。[ronWinnerIds]／
     * [ronWinningTileId] 只在搶槓成功時非空，供呼叫端逐一觸發胡牌慶祝演出——跟 [declarerId] 是互斥的
     * 兩個視窗，同一次結算只會有其中一種非空。
     *
     * @property tableState 本次交易提交的權威桌況。
     * @property drawnTiles 全員放過且動作成立後補到的牌；沒有補牌時為空清單。
     * @property scoring 全員放過且動作成立當下的點數收付；不收付時為 null。
     * @property followUp 補牌之後的自動處理；沒有補牌時為 null。
     * @property declarerId 全員放過且槓成功時的宣告者識別碼。
     * @property ronWinnerIds 搶槓榮和成立的贏家識別碼集合。
     * @property ronWinningTileId 搶槓榮和使用的牌張識別碼。
     * @property ronResolutions 各榮和贏家的算役結果。
     * @property ruleModuleId 本次榮和所用規則模組的識別碼。
     * @property ronDiscarderId 搶槓榮和結算中的放銃者識別碼。
     * @property settlement 同一搶槓榮和交易建立的胡牌呈現詳情。
     * @property steps 全員放過而動作成立時依序的步驟；其他結果為 null，由呼叫端以提交的桌況呈現。
     */
    private data class RobbingResult(
        val tableState: TableState,
        val drawnTiles: List<IdentifiedTile> = emptyList(),
        val scoring: AppliedActionScoring? = null,
        val followUp: DrawFollowUpApplier.Result? = null,
        val declarerId: Uuid? = null,
        val ronWinnerIds: Set<Uuid> = emptySet(),
        val ronWinningTileId: Uuid? = null,
        val ronResolutions: Map<Uuid, WinResolutionResult> = emptyMap(),
        val ruleModuleId: String? = null,
        val ronDiscarderId: Uuid? = null,
        val settlement: WinSettlementPresentationRequest? = null,
        val steps: List<TableStep>? = null,
    ) {
        /** 這次回應是否結束了搶和視窗：全員放過而動作成立，或有人搶和成功。 */
        val windowResolved: Boolean
            get() = declarerId != null || ronWinningTileId != null
    }
}
