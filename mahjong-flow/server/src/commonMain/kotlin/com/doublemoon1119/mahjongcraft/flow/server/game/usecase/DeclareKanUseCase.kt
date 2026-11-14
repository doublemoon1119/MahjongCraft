package com.doublemoon1119.mahjongcraft.flow.server.game.usecase

import com.doublemoon1119.mahjongcraft.flow.common.game.history.HistoryEventDraft
import com.doublemoon1119.mahjongcraft.flow.common.game.model.AppliedActionScoring
import com.doublemoon1119.mahjongcraft.flow.common.game.model.GameError
import com.doublemoon1119.mahjongcraft.flow.common.game.service.GameEventPublisher
import com.doublemoon1119.mahjongcraft.flow.common.game.service.GamePresentationPublisher
import com.doublemoon1119.mahjongcraft.flow.common.game.service.TableStep
import com.doublemoon1119.mahjongcraft.flow.common.game.service.TableStepEvent
import com.doublemoon1119.mahjongcraft.flow.common.result.Outcome
import com.doublemoon1119.mahjongcraft.flow.server.game.history.acceptedActionHistoryDraft
import com.doublemoon1119.mahjongcraft.flow.server.game.history.actionScoredHistoryDraft
import com.doublemoon1119.mahjongcraft.flow.server.game.orchestration.recordAbortiveDraw
import com.doublemoon1119.mahjongcraft.flow.server.game.repository.GameRepository
import com.doublemoon1119.mahjongcraft.flow.server.game.riichi.DeclareRiichiUseCase
import com.doublemoon1119.mahjongcraft.flow.server.game.service.GameSnapshotSynchronizer
import com.doublemoon1119.mahjongcraft.flow.server.game.service.isOwnTurnWindowOpenFor
import com.doublemoon1119.mahjongcraft.logic.base.ExhaustiveDrawReason
import com.doublemoon1119.mahjongcraft.logic.base.GameAction
import com.doublemoon1119.mahjongcraft.logic.base.IdentifiedTile
import com.doublemoon1119.mahjongcraft.logic.component.ComponentRegistry
import com.doublemoon1119.mahjongcraft.logic.config.MahjongRuleConfig
import com.doublemoon1119.mahjongcraft.logic.config.RonResolution
import com.doublemoon1119.mahjongcraft.logic.judgment.LegalActionContext
import com.doublemoon1119.mahjongcraft.logic.module.MahjongModuleRegistry
import com.doublemoon1119.mahjongcraft.logic.module.MahjongRuleModule
import com.doublemoon1119.mahjongcraft.logic.table.DrawSource
import com.doublemoon1119.mahjongcraft.logic.table.PendingRobbingReaction
import com.doublemoon1119.mahjongcraft.logic.table.SupplementalDrawReasonIds
import com.doublemoon1119.mahjongcraft.logic.table.TableState
import com.doublemoon1119.mahjongcraft.logic.table.TurnPhase
import kotlin.uuid.Uuid

/**
 * 宣告暗槓（[GameAction.KanType.CLOSED_KAN]）或加槓（[GameAction.KanType.ADDED_KAN]）的實例化用例。
 *
 * 是否合法完全交給該規則模組自己的 `LegalActionValidator`——這裡不重新實作暗槓/加槓的偵測邏輯
 * （含立直後暗槓「不能改變聽牌」的限制），理由與 [DeclareRiichiUseCase]、
 * [DeclareAbortiveDrawUseCase] 相同。合法性確認後，先檢查有沒有其他玩家可以搶槓
 * （是否允許搶槓以及哪些和牌形能搶，完全由規則模組的合法動作判定器決定）：
 * 有人可以搶時開一次反應視窗（[TableState.pendingRobbingReaction]，副露暫緩套用，交給
 * [RespondToRobbingUseCase] 解析）；沒人可以搶時直接套用（[SelfDeclarationApplier]）。多位玩家同時
 * 可搶時（罕見），依 [MahjongRuleConfig.multiRonPolicy] 決定實際開放
 * 給誰，與一般捨牌榮和共用同一套判定（見 [DiscardReactionResolver]）；判定為途中流局時，這次
 * 加槓視為未成立，不開反應視窗、不套用副露。
 *
 * 套用副露後依序記錄 [GameAction.Kan] 與規則的即時收付，再由 [MahjongRuleModule.createSupplementalDrawPolicy]
 * 決定是否補牌、補牌來源及牌牆變化，公開牌則由規則的牌牆公開 policy 決定。若規則補牌成功，
 * Flow 會記錄 [GameAction.Draw]，補到的牌再交給 [DrawFollowUpApplier] 依規則自動處理；
 * 規則層可依動作歷史判斷其專屬役種或狀態。
 *
 * 暗槓與加槓都不是鳴取他家捨牌，不呼叫 [MahjongRuleModule.beforeDiscardClaimed]；加槓沿用
 * `Hand.upgradeToAddedKan` 保留的原碰副露來源方位。
 *
 * 明牌（[GameAction.KanType.OPEN_KAN]，反應別人的捨牌）不在本用例範圍內，走 [RespondToDiscardUseCase]。
 *
 * @property componentRegistry 正式組件定義與純效果來源。
 * @property gameRepository 權威對局數據倉庫。
 * @property moduleRegistry 麻將規則模組註冊中心，用於解析當前對局的合法動作判定器。
 * @property snapshotSynchronizer 對局快照同步服務。
 * @property eventPublisher 對局通知服務。
 * @property presentationPublisher 對局 in-process 呈現觸發器。
 */
class DeclareKanUseCase(
    private val gameRepository: GameRepository,
    private val moduleRegistry: MahjongModuleRegistry,
    private val snapshotSynchronizer: GameSnapshotSynchronizer,
    private val eventPublisher: GameEventPublisher,
    private val presentationPublisher: GamePresentationPublisher,
    private val componentRegistry: ComponentRegistry,
) {
    /**
     * 執行暗槓/加槓宣告邏輯。
     *
     * @param gameId 對局 Uuid。
     * @param playerId 發起宣告的玩家 Uuid。
     * @param kanType 欲宣告的槓牌種類（僅支援 [GameAction.KanType.CLOSED_KAN]／[GameAction.KanType.ADDED_KAN]）。
     * @param tileId 合法槓牌候選中作為觸發牌的唯一識別碼；暗槓候選可位於既有立牌中。
     * @return 宣告結果，成功時為 [Unit]，失敗時為 [GameError]。
     */
    suspend operator fun invoke(
        gameId: Uuid,
        playerId: Uuid,
        kanType: GameAction.KanType,
        tileId: Uuid,
    ): Outcome<Unit, GameError> {
        // 1. 以原子方式讀取桌況、驗證業務規則並寫回
        val outcome = gameRepository.update(
            gameId = gameId,
            history = { before, after, result ->
                if (result is Outcome.Success && before != null && after != null) {
                    val kan = result.value.kanAction
                    val drafts = mutableListOf(
                        acceptedActionHistoryDraft(
                            actorPlayerId = playerId,
                            action = kan,
                            before = before,
                            after = after,
                            affectedTileIds = kan.withTiles + kan.tileId,
                        ),
                    )
                    result.value.scoring?.let { drafts += actionScoredHistoryDraft(it) }
                    if (result.value.drawnTiles.isNotEmpty()) {
                        drafts += acceptedActionHistoryDraft(
                            actorPlayerId = playerId,
                            action = GameAction.Draw,
                            before = before,
                            after = after,
                            affectedTileIds = result.value.drawnTiles.map { it.id },
                        )
                    }
                    result.value.followUp?.let { drafts += it.historyDrafts(playerId, before, after) }
                    result.value.abortiveDrawReason?.let { reason ->
                        drafts += acceptedActionHistoryDraft(playerId, GameAction.ExhaustiveDraw(reason), before, after)
                    }
                    drafts
                } else {
                    emptyList<HistoryEventDraft>()
                }
            },
        ) { state ->
            when {
                state == null -> state to Outcome.Error(GameError.GameNotFound(gameId))
                state.players.none { it.id == playerId } ->
                    state to Outcome.Error(GameError.PlayerNotInGame(playerId, gameId))

                state.currentPlayer.id != playerId ->
                    state to Outcome.Error(GameError.NotPlayersTurn(playerId, gameId))

                !state.isOwnTurnWindowOpenFor(playerId) || state.currentPlayer.hand.lastDrawn == null ->
                    state to Outcome.Error(
                        error = GameError.IllegalAction(playerId, gameId, GameAction.Kan(kanType, tileId, emptyList())),
                    )

                else -> {
                    val module = moduleRegistry.getModule(state.config)

                    val legalActions = module.createLegalActionValidator().getLegalActions(
                        context = LegalActionContext.ownTurn(state, playerId),
                    )
                    val kanAction = legalActions.filterIsInstance<GameAction.Kan>().firstOrNull {
                        it.type == kanType && it.tileId == tileId
                    }
                        ?: return@update state to Outcome.Error(
                            error = GameError.IllegalAction(playerId, gameId, GameAction.Kan(kanType, tileId, emptyList())),
                        )
                    val declaredTile = state.currentPlayer.hand.standingTiles.firstOrNull { it.id == kanAction.tileId }
                        ?: return@update state to Outcome.Error(
                            error = GameError.IllegalAction(playerId, gameId, GameAction.Kan(kanType, tileId, emptyList())),
                        )

                    val ronEligiblePlayerIds = RobbingEligibility.ronEligiblePlayerIds(
                        tableState = state,
                        declarerId = playerId,
                        declaredAction = kanAction,
                        robbedTile = declaredTile,
                        module = module,
                    )

                    // 搶槓多響（罕見：多位玩家同時可搶同一次加槓）依 MultiRonPolicy 決定實際開放給誰，
                    // 跟一般捨牌榮和共用同一套判定（見 DiscardReactionResolver）——搶槓本質上就是
                    // 「榮和一張牌，只是牌的來源是加槓而非捨牌」，沒有理由用不同規則。
                    val ronResolution = when (ronEligiblePlayerIds.size) {
                        0, 1 -> null
                        2 -> state.config.multiRonPolicy.doubleRonResolution
                        else -> state.config.multiRonPolicy.tripleRonResolution
                    }
                    val ronWinningPlayerIds = when (ronResolution) {
                        null, RonResolution.ALL_WINNERS -> ronEligiblePlayerIds
                        RonResolution.NEAREST_WINNER -> setOf(
                            element = state.nearestPlayerInTurnOrder(
                                fromPlayerId = playerId,
                                candidateIds = ronEligiblePlayerIds,
                            ),
                        )

                        RonResolution.ABORTIVE_DRAW -> emptySet()
                    }
                    val abortiveDrawReason = if (ronResolution == RonResolution.ABORTIVE_DRAW) {
                        RuleHookResultValidator.requireMultiRonAbortiveDrawReason(module)
                    } else {
                        null
                    }

                    if (abortiveDrawReason != null) {
                        // 多響流局：這次加槓視為未成立（比照 RespondToRobbingUseCase 既有的「搶槓
                        // 成功時，暗槓/加槓視為未成立」原則），不套用 SelfDeclarationApplier、不補
                        // 嶺上牌，直接記錄流局。
                        val newState = state.recordAbortiveDraw(abortiveDrawReason, declarerId = null, module = module)
                        return@update newState to Outcome.Success(
                            value = KanResult(
                                tableState = newState,
                                kanAction = kanAction,
                                abortiveDrawReason = abortiveDrawReason,
                                steps = listOf(TableStep(newState, listOf(TableStepEvent.ActionDeclared(playerId, kanAction)))),
                            ),
                        )
                    }

                    if (ronWinningPlayerIds.isNotEmpty()) {
                        val newState = state.copy(
                            turnPhase = TurnPhase.AwaitingRobbingReaction(
                                reaction = PendingRobbingReaction(
                                    declarerId = playerId,
                                    declaredAction = kanAction,
                                    robbedTile = declaredTile,
                                    eligiblePlayerIds = ronWinningPlayerIds,
                                ),
                            ),
                            publicTileEvidenceIds = buildSet {
                                addAll(state.publicTileEvidenceIds)
                                addAll(kanAction.withTiles)
                                add(kanAction.tileId)
                            },
                        )
                        // 先呈現槓完成的樣子（牌移入副露、尚未補牌），其他玩家看到後才決定要不要搶；權威桌況要等反應結束才套用。
                        val declaredState = SelfDeclarationApplier.declaredState(state, playerId, kanAction, declaredTile, module, componentRegistry)
                        return@update newState to Outcome.Success(
                            value = KanResult(
                                tableState = newState,
                                kanAction = kanAction,
                                steps = listOf(TableStep(declaredState ?: newState, listOf(TableStepEvent.ActionDeclared(playerId, kanAction)))),
                            ),
                        )
                    }

                    val applied = SelfDeclarationApplier.apply(state, playerId, kanAction, declaredTile, module, componentRegistry = componentRegistry)
                    if (applied is SelfDeclarationApplier.Result.Rejected) {
                        val error = if (applied.reasonId == SupplementalDrawReasonIds.WALL_EXHAUSTED) {
                            GameError.WallExhausted(gameId)
                        } else {
                            GameError.UnsupportedAction(gameId, playerId, applied.reasonId)
                        }
                        return@update state to Outcome.Error(error)
                    }
                    applied as SelfDeclarationApplier.Result.Applied
                    val followUp = if (applied.drawnTiles.isNotEmpty()) {
                        DrawFollowUpApplier.run(applied.tableState, playerId, DrawSource.Supplemental(kanAction), applied.drawnTiles, module, componentRegistry = componentRegistry)
                    } else {
                        null
                    }
                    val committed = followUp?.tableState ?: applied.tableState
                    committed to Outcome.Success(
                        value = KanResult(
                            tableState = committed,
                            kanAction = kanAction,
                            applied = true,
                            drawnTiles = applied.drawnTiles,
                            scoring = applied.scoring,
                            followUp = followUp,
                            steps = applied.tableSteps(playerId, kanAction, followUp),
                        ),
                    )
                }
            }
        }

        if (outcome is Outcome.Error) return outcome
        val result = (outcome as Outcome.Success).value
        val newState = result.tableState

        // 2. 同步快照給所有正在觀察的玩家
        snapshotSynchronizer.syncAll(gameId)

        // 3. 廣播槓牌宣告；若無人可搶槓、副露已直接套用，依序再廣播補摸嶺上牌事件；若搶槓多響
        //    依規則設定判定為流局，則改廣播流局事件（此時副露未套用，不會有補摸嶺上牌事件）
        val seatedPlayerIds = newState.players.map { it.id }
        eventPublisher.publishToAllObservers(gameId, seatedPlayerIds, playerId, result.kanAction)
        result.followUp?.let { followUp ->
            eventPublisher.publishToAllObservers(gameId, seatedPlayerIds, playerId, GameAction.Draw)
            followUp.publishedActions.forEach { followUpAction ->
                eventPublisher.publishToAllObservers(gameId, seatedPlayerIds, playerId, followUpAction)
            }
        }
        result.abortiveDrawReason?.let { reason ->
            eventPublisher.publishToAllObservers(gameId, seatedPlayerIds, playerId, GameAction.ExhaustiveDraw(reason))
        }

        // 4. 依序呈現槓的成立、補牌與補牌後的自動處理
        presentationPublisher.publishTableSteps(gameId, result.steps)
        return Outcome.Success(Unit)
    }

    /**
     * `update` 區塊內部使用的中繼結果，讓 [kanAction]/[applied]/[abortiveDrawReason] 能跟著
     * [tableState] 一起帶出 `gameRepository.update` 的作用域，供廣播事件時使用。[applied] 為
     * false 代表這次宣告開啟了搶槓反應視窗、或搶槓多響判定為流局，副露與嶺上摸牌皆尚未套用，不應
     * 廣播 [GameAction.Draw]。[abortiveDrawReason] 非 null 代表搶槓多響依規則設定判定為流局，這次
     * 加槓視為未成立。
     *
     * @property tableState 此次原子更新後的權威桌況。
     * @property kanAction 規則判定成立的槓動作。
     * @property applied 槓是否已成立並套用。
     * @property drawnTiles 槓成立後補到的牌；沒有補牌時為空清單。
     * @property scoring 槓成立當下的點數收付；沒有成立或不收付時為 null。
     * @property followUp 補牌之後的自動處理；沒有補牌時為 null。
     * @property abortiveDrawReason 搶槓多響成立的途中流局原因；未發生時為 null。
     * @property steps 依序呈現的步驟；開啟搶槓反應視窗時只有槓已移入副露、尚未補牌的候選桌況。
     */
    private data class KanResult(
        val tableState: TableState,
        val kanAction: GameAction.Kan,
        val applied: Boolean = false,
        val drawnTiles: List<IdentifiedTile> = emptyList(),
        val scoring: AppliedActionScoring? = null,
        val followUp: DrawFollowUpApplier.Result? = null,
        val abortiveDrawReason: ExhaustiveDrawReason? = null,
        val steps: List<TableStep>,
    )
}
