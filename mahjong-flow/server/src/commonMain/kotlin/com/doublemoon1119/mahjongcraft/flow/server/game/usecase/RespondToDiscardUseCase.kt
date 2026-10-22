package com.doublemoon1119.mahjongcraft.flow.server.game.usecase

import com.doublemoon1119.mahjongcraft.flow.common.game.history.HistoryEventDraft
import com.doublemoon1119.mahjongcraft.flow.common.game.history.HistoryFact
import com.doublemoon1119.mahjongcraft.flow.common.game.model.AppliedActionScoring
import com.doublemoon1119.mahjongcraft.flow.common.game.model.BuiltInRoundOutcomeIds
import com.doublemoon1119.mahjongcraft.flow.common.game.model.GameAbortReasonIds
import com.doublemoon1119.mahjongcraft.flow.common.game.model.GameError
import com.doublemoon1119.mahjongcraft.flow.common.game.model.SettledWinPresentation
import com.doublemoon1119.mahjongcraft.flow.common.game.model.WinCelebrationRequest
import com.doublemoon1119.mahjongcraft.flow.common.game.model.WinCelebrationWinner
import com.doublemoon1119.mahjongcraft.flow.common.game.model.WinSettlementPresentationRequest
import com.doublemoon1119.mahjongcraft.flow.common.game.service.GameEventPublisher
import com.doublemoon1119.mahjongcraft.flow.common.game.service.GamePresentationPublisher
import com.doublemoon1119.mahjongcraft.flow.common.game.service.WinCelebrationCueResolverRegistry
import com.doublemoon1119.mahjongcraft.flow.common.game.service.toPresentation
import com.doublemoon1119.mahjongcraft.flow.common.result.Outcome
import com.doublemoon1119.mahjongcraft.flow.server.game.history.acceptedActionHistoryDraft
import com.doublemoon1119.mahjongcraft.flow.server.game.history.actionScoredHistoryDraft
import com.doublemoon1119.mahjongcraft.flow.server.game.history.winSettlementHistoryDetails
import com.doublemoon1119.mahjongcraft.flow.server.game.orchestration.CompletedGameActionContext
import com.doublemoon1119.mahjongcraft.flow.server.game.orchestration.PostActionExhaustiveDrawResolverRegistry
import com.doublemoon1119.mahjongcraft.flow.server.game.orchestration.RuleContractViolationException
import com.doublemoon1119.mahjongcraft.flow.server.game.orchestration.recordAbortiveDraw
import com.doublemoon1119.mahjongcraft.flow.server.game.repository.GameRepository
import com.doublemoon1119.mahjongcraft.flow.server.game.service.DiscardReactionOptionsResolver
import com.doublemoon1119.mahjongcraft.flow.server.game.service.GameSnapshotSynchronizer
import com.doublemoon1119.mahjongcraft.flow.server.game.service.HandSortPreferenceStore
import com.doublemoon1119.mahjongcraft.flow.server.game.service.WinPresentationHandoff
import com.doublemoon1119.mahjongcraft.flow.server.game.service.WinSettlementDetailResolverRegistry
import com.doublemoon1119.mahjongcraft.flow.server.game.service.WinSettlementPresentationRequestFactory
import com.doublemoon1119.mahjongcraft.logic.base.ExhaustiveDrawReason
import com.doublemoon1119.mahjongcraft.logic.base.GameAction
import com.doublemoon1119.mahjongcraft.logic.base.IdentifiedTile
import com.doublemoon1119.mahjongcraft.logic.module.DiscardReactionCandidate
import com.doublemoon1119.mahjongcraft.logic.module.MahjongModuleRegistry
import com.doublemoon1119.mahjongcraft.logic.module.MahjongRuleModule
import com.doublemoon1119.mahjongcraft.logic.module.WinResolutionResult
import com.doublemoon1119.mahjongcraft.logic.table.DrawSource
import com.doublemoon1119.mahjongcraft.logic.table.MahjongPlayer
import com.doublemoon1119.mahjongcraft.logic.table.PendingReaction
import com.doublemoon1119.mahjongcraft.logic.table.SidewaysMarkedDiscardPile
import com.doublemoon1119.mahjongcraft.logic.table.SupplementalDrawReasonIds
import com.doublemoon1119.mahjongcraft.logic.table.TableState
import com.doublemoon1119.mahjongcraft.logic.table.WallRevealCheckpoint
import com.doublemoon1119.mahjongcraft.logic.table.layout.PhysicalWallLayoutTransitionPhase
import kotlin.uuid.Uuid

/**
 * 接收捨牌反應，並在所有有資格的玩家回應後裁定結果。
 *
 * 對應 [DiscardTileUseCase] 捨牌後開啟的 `TableState.pendingReaction`：每位有資格的玩家各自呼叫
 * 一次本用例記錄自己的回應。優先順序由規則提供，同級時再比較規則的次順位與距離捨牌者的座次。
 * 吃、碰、明槓與擴充副露使用同一條裁定流程；榮和沿用開啟視窗時決定的多家和牌資格。
 * 所有人都選擇過牌時，回合交給下一位玩家。
 *
 * 榮和結算後手牌即結束：本用例刻意只改動分數、贏家的 `actionHistory`，以及供託相關的動態規則狀態，
 * `currentPlayerIndex` 維持原樣不動（不像碰/吃/槓那樣把回合交給得標玩家），連莊/過莊/開下一局等
 * 後續流程由對局協調器銜接。
 *
 * @property gameRepository 權威對局數據倉庫。
 * @property moduleRegistry 麻將規則模組註冊中心，用於解析當前對局的合法動作判定器與規則特有邏輯。
 * @property snapshotSynchronizer 對局快照同步服務。
 * @property handSortPreferenceStore 查詢玩家是否啟用自動整理手牌，見該類別 KDoc。
 * @property eventPublisher 對局通知服務。
 * @property presentationPublisher 對局 in-process 呈現觸發器。
 * @property winPresentationHandoff 胡牌呈現 request 的交易後交接點。
 * @property winCelebrationCueResolverRegistry 胡牌慶祝 cue 解析器 registry。
 * @property winSettlementDetailResolverRegistry 胡牌詳情解析器 registry。
 * @property postActionExhaustiveDrawResolverRegistry 捨牌反應後流局解析器 registry。
 */
class RespondToDiscardUseCase(
    private val gameRepository: GameRepository,
    private val moduleRegistry: MahjongModuleRegistry,
    private val snapshotSynchronizer: GameSnapshotSynchronizer,
    private val handSortPreferenceStore: HandSortPreferenceStore,
    private val eventPublisher: GameEventPublisher,
    private val presentationPublisher: GamePresentationPublisher,
    private val winPresentationHandoff: WinPresentationHandoff,
    private val winCelebrationCueResolverRegistry: WinCelebrationCueResolverRegistry,
    private val winSettlementDetailResolverRegistry: WinSettlementDetailResolverRegistry,
    private val postActionExhaustiveDrawResolverRegistry: PostActionExhaustiveDrawResolverRegistry,
) {
    /**
     * 執行捨牌反應回應邏輯。
     *
     * @param gameId 對局 Uuid。
     * @param playerId 發起回應的玩家 Uuid。
     * @param action 規則在目前視窗提供的回應動作，包含內建反應或擴充副露。
     * @param reactionId 提交時所回應的視窗識別碼。
     * @param selectedTileIds 本次動作選取的本人手牌。
     * @return 回應結果，成功時為 [Unit]，失敗時為 [GameError]。
     */
    suspend operator fun invoke(
        gameId: Uuid,
        playerId: Uuid,
        action: GameAction,
        reactionId: Uuid,
        selectedTileIds: List<Uuid> = emptyList(),
    ): Outcome<Unit, GameError> {
        // AI 名單整場對局不變，先取出供結算呈現使用。
        val aiPlayerIds = gameRepository.getGame(gameId)?.aiPlayerIds.orEmpty()
        val outcome = gameRepository.update(
            gameId = gameId,
            history = { before, after, result ->
                if (result is Outcome.Success && before != null && after != null) {
                    listOf(
                        acceptedActionHistoryDraft(
                            actorPlayerId = playerId,
                            action = action,
                            before = before,
                            after = after,
                            affectedTileIds = (action.affectedTileIds() + selectedTileIds + listOfNotNull(before.pendingReaction?.tileId)).distinct(),
                        ),
                    ) + if (before.pendingReaction != null && after.pendingReaction == null) {
                        val resolution = result.value
                        buildList {
                            add(
                                HistoryEventDraft(
                                    actorPlayerId = resolution.winnerId ?: resolution.ronWinnerIds.singleOrNull(),
                                    fact = HistoryFact.ReactionResolved(
                                        resolvedAction = resolution.resolvedAction
                                            ?: resolution.ronWinningTileId?.let(GameAction::Ron),
                                        actorPlayerId = resolution.winnerId ?: resolution.ronWinnerIds.singleOrNull(),
                                    ),
                                ),
                            )
                            resolution.winnerId?.let { winnerId ->
                                resolution.scoring?.let { add(actionScoredHistoryDraft(it)) }
                                if (resolution.drawnTiles.isNotEmpty()) {
                                    add(
                                        acceptedActionHistoryDraft(
                                            actorPlayerId = winnerId,
                                            action = GameAction.Draw,
                                            before = before,
                                            after = after,
                                            affectedTileIds = resolution.drawnTiles.map { it.id },
                                        ),
                                    )
                                }
                                resolution.followUp?.let { addAll(it.historyDrafts(winnerId, before, after)) }
                            }
                            resolution.abortiveDrawReason?.let { reason ->
                                add(
                                    acceptedActionHistoryDraft(
                                        actorPlayerId = resolution.abortiveDrawActorId,
                                        action = GameAction.ExhaustiveDraw(reason),
                                        before = before,
                                        after = after,
                                    ),
                                )
                            }
                            resolution.settlement?.let { settlement ->
                                add(
                                    HistoryEventDraft(
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
            val pendingReaction = state?.pendingReaction
            when {
                state == null -> state to Outcome.Error(GameError.GameNotFound(gameId))
                state.players.none { it.id == playerId } ->
                    state to Outcome.Error(GameError.PlayerNotInGame(playerId, gameId))

                pendingReaction == null ->
                    state to Outcome.Error(GameError.IllegalAction(playerId, gameId, action))

                pendingReaction.id != reactionId ->
                    state to Outcome.Error(GameError.IllegalAction(playerId, gameId, action))

                playerId !in pendingReaction.eligiblePlayerIds ->
                    state to Outcome.Error(GameError.IllegalAction(playerId, gameId, action))

                playerId in pendingReaction.responses ->
                    state to Outcome.Error(GameError.IllegalAction(playerId, gameId, action))

                else -> {
                    val discarder = state.players.first { it.id == pendingReaction.discarderId }
                    val responder = state.players.first { it.id == playerId }
                    val discardedTile = discarder.discardPile.entries.lastOrNull()
                        ?.takeIf { it.tile.id == pendingReaction.tileId && !it.isTaken }
                        ?.tile
                        ?: return@update state to Outcome.Error(GameError.IllegalAction(playerId, gameId, action))

                    val module = moduleRegistry.getModule(state.config)
                    val candidates = DiscardReactionOptionsResolver.resolveCandidates(
                        state = state,
                        module = module,
                        playerId = playerId,
                        discarderId = pendingReaction.discarderId,
                        discardedTile = discardedTile,
                    )
                    val accepted = DiscardReactionOptionsResolver.resolveSubmission(
                        state = state,
                        module = module,
                        playerId = playerId,
                        discarderId = pendingReaction.discarderId,
                        discardedTile = discardedTile,
                        action = action,
                        selectedTileIds = selectedTileIds,
                    )
                    if (accepted == null) {
                        return@update state to Outcome.Error(GameError.IllegalAction(playerId, gameId, action))
                    }

                    // 放過原本合法的榮和：後果交給規則決定，見 MahjongRuleModule.onPlayerDeclinedWin。
                    // 這張牌記入放過清單的時機是反應視窗結束且沒有人榮和時，見 resolvePendingReaction。
                    val updatedResponder = if (action == GameAction.Pass && candidates.any { it.action is GameAction.Ron }) {
                        RuleHookResultValidator.requireRuleStateOnly(
                            before = responder,
                            after = module.onPlayerDeclinedWin(responder),
                            hookName = "onPlayerDeclinedWin",
                        )
                    } else {
                        responder
                    }
                    val playersAfterResponse = state.players.map { if (it.id == playerId) updatedResponder else it }
                    val newPendingReaction =
                        pendingReaction.copy(
                            responses = pendingReaction.responses + (playerId to action),
                            selectedTileIds = pendingReaction.selectedTileIds + (playerId to accepted.selectedTileIds.toList()),
                        )

                    val result = if (!newPendingReaction.isComplete) {
                        RespondResult(state.copy(players = playersAfterResponse, pendingReaction = newPendingReaction))
                    } else {
                        resolvePendingReaction(state, playersAfterResponse, newPendingReaction, discardedTile, module)
                    }

                    val settledResult = if (result.ronWinningTileId != null && result.ronResolutions.isNotEmpty()) {
                        result.copy(
                            settlement = WinSettlementPresentationRequestFactory.create(
                                previousState = checkNotNull(result.previousTableState),
                                currentState = result.tableState,
                                module = module,
                                aiPlayerIds = aiPlayerIds,
                                outcomeId = BuiltInRoundOutcomeIds.RON,
                                isTsumo = false,
                                winningTileId = result.ronWinningTileId,
                                responsiblePlayerId = result.ronDiscarderId,
                                resolutions = result.ronResolutions,
                                detailResolverRegistry = winSettlementDetailResolverRegistry,
                            ),
                        )
                    } else {
                        result
                    }

                    settledResult.rejectionReasonId?.let { reasonId ->
                        val error = if (reasonId == SupplementalDrawReasonIds.WALL_EXHAUSTED) {
                            GameError.WallExhausted(gameId)
                        } else {
                            GameError.UnsupportedAction(gameId, playerId, reasonId)
                        }
                        return@update state to Outcome.Error(error)
                    }

                    settledResult.tableState to Outcome.Success(settledResult)
                }
            }
        }

        if (outcome is Outcome.Error) return outcome
        val result = (outcome as Outcome.Success).value
        val newState = result.tableState

        snapshotSynchronizer.syncAll(gameId)

        val seatedPlayerIds = newState.players.map { it.id }
        val abortiveDrawReason = result.abortiveDrawReason
        val ronWinningTileId = result.ronWinningTileId
        val resolvedAction = result.resolvedAction
        when {
            abortiveDrawReason != null -> eventPublisher.publishToAllObservers(
                gameId,
                seatedPlayerIds,
                requireNotNull(result.abortiveDrawActorId),
                GameAction.ExhaustiveDraw(abortiveDrawReason),
            )

            ronWinningTileId != null -> result.ronWinnerIds.forEach { winnerId ->
                eventPublisher.publishToAllObservers(
                    gameId,
                    seatedPlayerIds,
                    winnerId,
                    GameAction.Ron(ronWinningTileId),
                )
            }

            resolvedAction != null -> eventPublisher.publishToAllObservers(
                gameId,
                seatedPlayerIds,
                requireNotNull(result.winnerId),
                resolvedAction,
            )
        }
        result.followUp?.let { followUp ->
            val winnerId = requireNotNull(result.winnerId)
            eventPublisher.publishToAllObservers(gameId, seatedPlayerIds, winnerId, GameAction.Draw)
            followUp.publishedActions.forEach { followUpAction ->
                eventPublisher.publishToAllObservers(gameId, seatedPlayerIds, winnerId, followUpAction)
            }
            presentationPublisher.publishRoundInfoUpdated(gameId, newState)
        }

        // 觸發平台呈現層：碰/吃/明槓得標時，丟牌者的 discardPile 被 takeLast() 標記，即使沒有新增
        // 捨牌，側身標記也可能因此位移，需要重新呈現這位丟牌者的牌河
        result.discarderId?.let { discarderId ->
            val discarderSeatIndex = newState.players.indexOfFirst { it.id == discarderId }
            val discarder = newState.players[discarderSeatIndex]
            presentationPublisher.publishDiscardPileUpdated(
                gameId,
                discarderSeatIndex,
                discarder.discardPile.entries.filterNot { it.isTaken }.map { it.tile.id },
                (discarder.discardPile as? SidewaysMarkedDiscardPile)?.sidewaysMarkedTileId(),
            )
        }

        // 碰/吃/明槓得標時，得標玩家的副露多了一組、手牌也少了對應張數，重新呈現整份手牌/摸牌位/
        // 副露；規則另外補牌時（[RespondResult.followUp]）摸牌位自然帶著呈現，不需要
        // 額外判斷——合併呼叫後也順便補上先前缺漏的手牌重新呈現（吃/碰/明槓後手牌張數變少，先前完全
        // 沒有重新呈現手牌列）。
        result.winnerId?.let { winnerId ->
            result.resolvedAction?.let { resolvedAction ->
                presentationPublisher.publishGameActionDeclared(gameId, winnerId, resolvedAction)
            }
            result.scoring?.let { scoring ->
                presentationPublisher.publishActionScored(gameId, scoring)
                // 沒有補牌時不會更新局況；收付改變了分數，玩家資訊要跟著更新。
                if (result.followUp == null) presentationPublisher.publishRoundInfoUpdated(gameId, newState)
            }
            val winnerSeatIndex = newState.players.indexOfFirst { it.id == winnerId }
            val winner = newState.players[winnerSeatIndex]
            val module = moduleRegistry.getModule(newState.config)
            // 吃/碰/明槓永遠整組一次成立新副露（附加到 exposedMelds 尾端，不是原地修改既有組），
            // 最後一組必定就是這次剛成立的那組，組內全部牌都是新移入副露的牌。
            val claimedTileIds = winner.hand.melds.last().tiles.map { it.id }.toSet()
            val followUp = result.followUp
            if (followUp != null) {
                presentationPublisher.publishPlayerAreaAfterDraw(
                    gameId = gameId,
                    playerId = winnerId,
                    followUp = followUp,
                    module = module,
                    newlyClaimedMeldTileIds = claimedTileIds,
                )
            } else {
                presentationPublisher.publishPlayerTilesUpdated(
                    gameId = gameId,
                    seatIndex = winnerSeatIndex,
                    standingTileIds = winner.hand.tiles.map { it.id },
                    drawnTileId = winner.hand.lastDrawn?.id,
                    melds = winner.hand.melds.map { it.toPresentation(newState.config.revealsClosedKanTiles) },
                    setAsideTileIds = module.setAsideTiles(winner).map { it.id },
                    newlyClaimedMeldTileIds = claimedTileIds,
                )
            }
        }

        // 依 checkpoint 順序發布新公開的牌牆資訊；除了本次明槓，先前延後的公開也可能在全員跳過、
        // 吃或碰確定無人榮和時於此完成，因此不能綁在 winnerId 的呈現區塊內。
        result.wallRevealBatches.forEach { revealedTileIds ->
            presentationPublisher.publishWallTilesRevealed(gameId, revealedTileIds)
        }
        if (result.physicalWallTransitionPhases.isNotEmpty()) {
            presentationPublisher.publishWallLayoutTransition(gameId, result.physicalWallTransitionPhases)
        }

        // 建構胡牌演出內容並寫進交接槽——一炮多響時 result.ronWinnerIds 可能不只一人，打包成同一筆；
        // winningTileId 對每位贏家來說都是同一張放銃的捨牌。刻意不直接發布，理由同 DeclareTsumoUseCase。
        result.ronWinningTileId?.let { winningTileId ->
            result.ronWinnerIds.forEach { winnerId ->
                presentationPublisher.publishGameActionDeclared(gameId, winnerId, GameAction.Ron(winningTileId))
            }
            val presentation = SettledWinPresentation(
                winnerPlayerIds = result.ronWinnerIds,
                celebration = WinCelebrationRequest(
                    winningTileId,
                    isTsumo = false,
                    winners = result.ronWinnerIds.map { winnerId ->
                        WinCelebrationWinner(
                            newState.players.indexOfFirst { it.id == winnerId },
                            result.ronResolutions[winnerId]?.let {
                                winCelebrationCueResolverRegistry.resolve(
                                    result.ruleModuleId.orEmpty(),
                                    it.handValueResult,
                                )
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

    /** 回應動作直接涉及的牌；過牌沒有牌面變更。 */
    private fun GameAction.affectedTileIds(): List<Uuid> = when (this) {
        is GameAction.Chi -> listOf(tileId) + withTiles
        is GameAction.Pon -> listOf(tileId) + withTiles
        is GameAction.Kan -> listOf(tileId) + withTiles
        is GameAction.Ron -> listOf(tileId)
        else -> emptyList()
    }

    /**
     * `update` 區塊內部使用的中繼結果，讓 [followUp]／[discarderId]／[winnerId] 能跟著
     * [tableState] 一起帶出 `gameRepository.update` 的作用域，供廣播事件／觸發呈現時使用。
     * [followUp] 不為 null 代表明槓得標後規則成功補牌，需要額外廣播 [GameAction.Draw] 與後續的自動處理。
     * [discarderId] 只在碰/吃/明槓得標、實際對丟牌者的 `discardPile` 呼叫過 `takeLast()` 時才有值
     * （全員過牌與榮和都不會呼叫 `takeLast()`），供呼叫端重新呈現該玩家的牌河。[winnerId] 跟
     * [discarderId] 同一個有效視窗（碰/吃/明槓得標時才有值），供呼叫端重新呈現得標玩家的副露。
     * [ronWinnerIds] 只在榮和結算成立時非空（一炮多響可能不只一人），[ronWinningTileId] 是被榮和的那張
     * 捨牌，供呼叫端逐一觸發胡牌慶祝演出；跟 [winnerId]（碰/吃/明槓得標）是互斥的兩個視窗，同一次結算
     * 只會有其中一種非空。
     *
     * @property tableState 本次交易提交的權威桌況。
     * @property drawnTiles 鳴牌成立後補到的牌；沒有補牌時為空清單。
     * @property scoring 鳴牌成立當下的點數收付；不收付時為 null。
     * @property followUp 補牌之後的自動處理；沒有補牌時為 null。
     * @property discarderId 鳴牌成立時原捨牌者的識別碼；其他結果為 null。
     * @property winnerId 鳴牌成立時取得副露的玩家識別碼；其他結果為 null。
     * @property resolvedAction 最後成立的非過牌動作；沒有成立的動作時為 null。
     * @property ronWinnerIds 本次榮和成立的贏家識別碼集合。
     * @property ronWinningTileId 本次榮和使用的捨牌識別碼。
     * @property ronResolutions 各榮和贏家的算役結果。
     * @property ruleModuleId 本次榮和所用規則模組的識別碼。
     * @property previousTableState 榮和結算前、仍保留反應視窗資訊的桌況。
     * @property ronDiscarderId 榮和結算中的放銃者識別碼。
     * @property settlement 同一榮和交易建立的胡牌呈現詳情。
     * @property wallRevealBatches 依 checkpoint 順序新增公開的牌張 ID 批次。
     * @property physicalWallTransitionPhases 已驗證的實體牌牆移動階段。
     * @property rejectionReasonId 規則拒絕或結果驗證失敗時的原因 ID。
     * @property abortiveDrawReason 本次結算觸發的途中流局原因。
     * @property abortiveDrawActorId 記錄途中流局動作的玩家識別碼。
     */
    private data class RespondResult(
        val tableState: TableState,
        val drawnTiles: List<IdentifiedTile> = emptyList(),
        val scoring: AppliedActionScoring? = null,
        val followUp: DrawFollowUpApplier.Result? = null,
        val discarderId: Uuid? = null,
        val winnerId: Uuid? = null,
        val resolvedAction: GameAction? = null,
        val ronWinnerIds: Set<Uuid> = emptySet(),
        val ronWinningTileId: Uuid? = null,
        val ronResolutions: Map<Uuid, WinResolutionResult> = emptyMap(),
        val ruleModuleId: String? = null,
        val previousTableState: TableState? = null,
        val ronDiscarderId: Uuid? = null,
        val settlement: WinSettlementPresentationRequest? = null,
        val wallRevealBatches: List<Set<Uuid>> = emptyList(),
        val physicalWallTransitionPhases: List<PhysicalWallLayoutTransitionPhase> = emptyList(),
        val rejectionReasonId: String? = null,
        val abortiveDrawReason: ExhaustiveDrawReason? = null,
        val abortiveDrawActorId: Uuid? = null,
    )

    /**
     * 所有有資格的玩家皆已回應，進行結算：找出優先權最高的非過牌回應套用鳴牌，或在全員過牌時單純推進回合。
     *
     * 沒有人榮和時，這張捨牌記入打牌者以外每位仍在本局玩家的放過清單；鳴牌者隨即清除自己的清單。
     */
    private fun resolvePendingReaction(
        state: TableState,
        respondedPlayers: List<MahjongPlayer>,
        pendingReaction: PendingReaction,
        discardedTile: IdentifiedTile,
        module: MahjongRuleModule<*>,
    ): RespondResult {
        // 過牌不參與裁定，也不重新驗證：放過和牌的規則後果（例如立直後放過榮和而永久振聽）在提交時已經套用，
        // 套用後該玩家可能不再有任何合法動作。
        val responses = pendingReaction.responses.filterValues { it != GameAction.Pass }.mapValues { (playerId, action) ->
            DiscardReactionOptionsResolver.resolveSubmission(
                state = state,
                module = module,
                playerId = playerId,
                discarderId = pendingReaction.discarderId,
                discardedTile = discardedTile,
                action = action,
                selectedTileIds = pendingReaction.selectedTileIds[playerId].orEmpty(),
            ) ?: throw RuleContractViolationException(
                GameAbortReasonIds.RULE_EXECUTION_FAILED,
                "Accepted discard reaction cannot be resolved for $playerId: $action",
            )
        }
        val discarderIndex = state.players.indexOfFirst { it.id == pendingReaction.discarderId }
        val rankedResponses = responses.entries.sortedWith(
            compareByDescending<Map.Entry<Uuid, DiscardReactionCandidate>> { it.value.priority }
                .thenByDescending { it.value.tieBreakPriority }
                .thenBy { entry ->
                    (state.players.indexOfFirst { it.id == entry.key } - discarderIndex + state.players.size) % state.players.size
                },
        )
        val winningResponse = rankedResponses.firstOrNull()
        val ronWinnerIds = if (winningResponse?.value?.action is GameAction.Ron) {
            responses.filterValues { it.action is GameAction.Ron }.keys
        } else {
            emptySet()
        }
        if (ronWinnerIds.isNotEmpty()) {
            val resolved = RonSettlementResolver.resolve(
                state = state,
                players = respondedPlayers,
                discarderId = pendingReaction.discarderId,
                winningTile = discardedTile,
                module = module,
                winnerIds = ronWinnerIds,
            )
            val result = RespondResult(
                tableState = resolved.tableState.copy(pendingReaction = null),
                ronWinnerIds = ronWinnerIds,
                ronWinningTileId = discardedTile.id,
                ronResolutions = resolved.resolutions,
                ruleModuleId = module.id,
                previousTableState = state.copy(players = respondedPlayers),
                ronDiscarderId = pendingReaction.discarderId,
            )
            return result.applyWallReveal(
                checkpoint = WallRevealCheckpoint.WIN_CONFIRMED,
                module = module,
                sourceAction = GameAction.Ron(discardedTile.id),
            )
        }

        val players = PassedTileRecorder.record(
            tableState = state.copy(players = respondedPlayers),
            tile = discardedTile,
            playerIds = respondedPlayers
                .filter { it.id != pendingReaction.discarderId && state.isPlayerActive(it.id) }
                .mapTo(mutableSetOf()) { it.id },
            module = module,
        ).players
        val stateAfterResponses = state.copy(players = players, pendingReaction = null)
        val completedDiscard = GameAction.Discard(pendingReaction.tileId)
        val abortiveDrawReason = postActionExhaustiveDrawResolverRegistry.resolve(
            CompletedGameActionContext(
                actorPlayerId = pendingReaction.discarderId,
                action = completedDiscard,
                tableState = stateAfterResponses,
            ),
            module,
        )
        if (abortiveDrawReason != null) {
            return RespondResult(
                tableState = stateAfterResponses.recordAbortiveDraw(abortiveDrawReason, declarerId = null, module = module),
                abortiveDrawReason = abortiveDrawReason,
                abortiveDrawActorId = pendingReaction.discarderId,
            )
        }

        val winningEntry = winningResponse

        if (winningEntry == null) {
            // 所有人皆過牌：行為與捨牌時「無人可反應」相同，直接推進到下一位仍在本局中的玩家
            val stateWithPlayers = state.copy(players = players)
            val nextPlayer = stateWithPlayers.nextActivePlayerAfter(pendingReaction.discarderId)
            return RespondResult(
                stateWithPlayers.copy(
                    currentPlayerIndex = players.indexOf(nextPlayer),
                    pendingReaction = null,
                ),
            ).applyWallReveal(
                checkpoint = WallRevealCheckpoint.AFTER_DISCARD_REACTIONS,
                module = module,
                actorPlayerId = pendingReaction.discarderId,
                sourceAction = GameAction.Discard(pendingReaction.tileId),
            )
        }

        val winnerId = winningEntry.key
        val winnerAction = winningEntry.value.action
        val winner = players.first { it.id == winnerId }
        val winnerDirection = state.relativeDirectionOf(winnerId, pendingReaction.discarderId)

        val effect = winningEntry.value.claimEffect ?: throw RuleContractViolationException(
            GameAbortReasonIds.RULE_EXECUTION_FAILED,
            "Winning discard claim has no meld effect: $winnerAction",
        )
        val meldType = effect.meldType
        val handTilesUsed = effect.handTileIds.map { id -> winner.hand.standingTiles.first { it.id == id } }

        val claimingWinner = module.beforeDiscardClaimed(winner, meldType, discardedTile, winnerDirection)
        val meldTiles = handTilesUsed + discardedTile
        val displayTiles = if (effect.publiclyRevealedTileIds.containsAll(meldTiles.map { it.id })) {
            meldTiles.sortedWith(compareBy(module.tileOrder) { it.tile })
        } else {
            meldTiles.sortedBy { it.id.toString() }
        }
        val calledHand = claimingWinner.hand.call(
            type = meldType,
            tiles = displayTiles,
            source = discardedTile,
            direction = winnerDirection,
            publiclyRevealedTileIds = effect.publiclyRevealedTileIds,
            faceDownTileIds = effect.faceDownTileIds,
        )
        // 明槓得標後緊接著補摸嶺上牌（見下方 lastDrawn 賦值），這裡先不整理——理由同 DeclareKanUseCase
        // 沒有整理手牌時機點的說明：整理只在 lastDrawn == null（沒有還沒決定的摸牌）時才適用。
        val organizedHand = if (!effect.requiresSupplementalDraw && handSortPreferenceStore.isEnabled(winnerId)) {
            calledHand.organize(module.tileOrder)
        } else {
            calledHand
        }
        val winnerAfterMeld = claimingWinner.copy(
            hand = organizedHand,
            mustDiscardAfterMeld = !effect.requiresSupplementalDraw,
        ).clearPassedTiles().recordAction(winnerAction)

        val playersAfterMeld = players.map { player ->
            when (player.id) {
                winnerId -> winnerAfterMeld
                pendingReaction.discarderId -> player.copy(discardPile = player.discardPile.takeLast())
                else -> player
            }
        }
        val playersAfterMeldClaimed = RuleHookResultValidator.requireRuleStateOnly(
            before = playersAfterMeld,
            after = module.onMeldClaimed(playersAfterMeld),
            hookName = "onMeldClaimed",
        )
        val winnerIndex = playersAfterMeldClaimed.indexOfFirst { it.id == winnerId }
        val originalState = state.copy(players = players)
        val scored = ActionScoringApplier.apply(
            before = originalState,
            after = state.copy(
                players = playersAfterMeldClaimed,
                currentPlayerIndex = winnerIndex,
                pendingReaction = null,
            ),
            actorPlayerId = winnerId,
            action = winnerAction,
            sourcePlayerId = pendingReaction.discarderId,
            module = module,
        )

        if (!effect.requiresSupplementalDraw) {
            return RespondResult(
                tableState = scored.tableState,
                discarderId = pendingReaction.discarderId,
                winnerId = winnerId,
                resolvedAction = winnerAction,
                scoring = scored.scoring,
            ).applyWallReveal(
                checkpoint = WallRevealCheckpoint.AFTER_DISCARD_REACTIONS,
                module = module,
                actorPlayerId = pendingReaction.discarderId,
                sourceAction = GameAction.Discard(pendingReaction.tileId),
            )
        }

        val reactionReveal = RespondResult(scored.tableState).applyWallReveal(
            checkpoint = WallRevealCheckpoint.AFTER_DISCARD_REACTIONS,
            module = module,
            actorPlayerId = pendingReaction.discarderId,
            sourceAction = GameAction.Discard(pendingReaction.tileId),
        )
        if (reactionReveal.rejectionReasonId != null) return reactionReveal

        return when (
            val applied = SelfDeclarationApplier.applySupplementalDraw(
                originalState.copy(dynamicRuleState = reactionReveal.tableState.dynamicRuleState),
                reactionReveal.tableState,
                winnerId,
                winnerAction,
                module,
            )
        ) {
            is SelfDeclarationApplier.Result.Rejected -> {
                if (applied.reasonId == SupplementalDrawReasonIds.INVALID_RESULT) {
                    throw RuleContractViolationException(
                        GameAbortReasonIds.RULE_EXECUTION_FAILED,
                        "Claim supplemental draw failed validation: $winnerAction",
                    )
                }
                RespondResult(tableState = state, rejectionReasonId = applied.reasonId)
            }

            is SelfDeclarationApplier.Result.Applied -> {
                if (applied.drawnTiles.isEmpty()) {
                    throw RuleContractViolationException(
                        GameAbortReasonIds.RULE_EXECUTION_FAILED,
                        "Claim requires a supplemental draw but the rule did not provide one: $winnerAction",
                    )
                }
                val followUp = DrawFollowUpApplier.run(
                    state = applied.tableState,
                    playerId = winnerId,
                    source = DrawSource.Supplemental(winnerAction),
                    module = module,
                )
                RespondResult(
                    tableState = followUp.tableState,
                    drawnTiles = applied.drawnTiles,
                    scoring = scored.scoring,
                    followUp = followUp,
                    discarderId = pendingReaction.discarderId,
                    winnerId = winnerId,
                    resolvedAction = winnerAction,
                    wallRevealBatches = reactionReveal.wallRevealBatches + applied.wallRevealBatches,
                    physicalWallTransitionPhases = applied.physicalWallTransitionPhases,
                )
            }
        }
    }

    /** 對中繼結果套用一個規則中立牌牆公開 checkpoint。 */
    private fun RespondResult.applyWallReveal(
        checkpoint: WallRevealCheckpoint,
        module: MahjongRuleModule<*>,
        actorPlayerId: Uuid? = null,
        sourceAction: GameAction? = null,
    ): RespondResult = when (
        val applied = WallRevealDecisionApplier.apply(
            tableState = tableState,
            checkpoint = checkpoint,
            module = module,
            actorPlayerId = actorPlayerId,
            sourceAction = sourceAction,
        )
    ) {
        is WallRevealDecisionApplier.Result.Applied -> copy(
            tableState = applied.tableState,
            wallRevealBatches = wallRevealBatches +
                listOf(applied.newlyRevealedTileIds).filterNot { it.isEmpty() },
        )

        is WallRevealDecisionApplier.Result.Rejected -> copy(rejectionReasonId = applied.reasonId)
    }
}
