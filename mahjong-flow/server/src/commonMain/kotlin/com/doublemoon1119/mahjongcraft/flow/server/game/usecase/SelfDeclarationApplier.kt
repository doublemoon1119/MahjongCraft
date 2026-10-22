package com.doublemoon1119.mahjongcraft.flow.server.game.usecase

import com.doublemoon1119.mahjongcraft.flow.common.game.model.AppliedActionScoring
import com.doublemoon1119.mahjongcraft.flow.server.game.orchestration.RuleContractViolationException
import com.doublemoon1119.mahjongcraft.logic.base.GameAction
import com.doublemoon1119.mahjongcraft.logic.base.IdentifiedTile
import com.doublemoon1119.mahjongcraft.logic.base.MeldType
import com.doublemoon1119.mahjongcraft.logic.base.RelativeDirection
import com.doublemoon1119.mahjongcraft.logic.base.closedKanFaceDownTileIds
import com.doublemoon1119.mahjongcraft.logic.base.closedKanPubliclyRevealedTileIds
import com.doublemoon1119.mahjongcraft.logic.module.MahjongRuleModule
import com.doublemoon1119.mahjongcraft.logic.table.SupplementalDrawContext
import com.doublemoon1119.mahjongcraft.logic.table.SupplementalDrawDecision
import com.doublemoon1119.mahjongcraft.logic.table.SupplementalDrawReasonIds
import com.doublemoon1119.mahjongcraft.logic.table.TableState
import com.doublemoon1119.mahjongcraft.logic.table.WallRevealCheckpoint
import com.doublemoon1119.mahjongcraft.logic.table.layout.PhysicalWallLayoutTransitionContext
import com.doublemoon1119.mahjongcraft.logic.table.layout.PhysicalWallLayoutTransitionDecision
import com.doublemoon1119.mahjongcraft.logic.table.layout.PhysicalWallLayoutTransitionPhase
import com.doublemoon1119.mahjongcraft.logic.table.layout.resolveTransitionValidated
import kotlin.uuid.Uuid

/**
 * 原子套用自己回合宣告的暗槓／加槓（或規則提供的移出手牌動作）與規則提供的後續補牌結果。
 *
 * 本物件只負責通用手牌變化、結果驗證及 action history；補牌來源、上限、牌牆變化與新增公開牌完全由
 * [MahjongRuleModule.createSupplementalDrawPolicy] 決定。
 */
internal object SelfDeclarationApplier {
    /** 自己回合宣告的動作與補牌的套用結果。 */
    sealed interface Result {
        /**
         * 完整動作已成功套用。
         *
         * @property tableState 更新後桌況。
         * @property drawnTiles 規則實際補到的牌張。
         * @property wallRevealBatches 依 checkpoint 順序排列的新公開牌批次。
         * @property physicalWallTransitionPhases 已通過規則驗證的實體牌牆移動階段；本切片只隨結果保留，
         * 尚不負責發布 presentation。
         * @property scoring 動作成立當下的點數收付；不收付時為 null。
         */
        data class Applied(
            val tableState: TableState,
            val drawnTiles: List<IdentifiedTile>,
            val wallRevealBatches: List<Set<Uuid>> = emptyList(),
            val physicalWallTransitionPhases: List<PhysicalWallLayoutTransitionPhase> = emptyList(),
            val scoring: AppliedActionScoring? = null,
        ) : Result

        /**
         * 規則拒絕補牌或回傳無法安全套用的結果。
         *
         * @property reasonId 完整 namespaced 原因 ID。
         */
        data class Rejected(val reasonId: String) : Result
    }

    /**
     * 套用本次暗槓或加槓與其點數收付，並將候選桌況交給規則補牌 policy。
     *
     * @param state 尚未套用本次槓牌的權威桌況。
     * @param declarerId 宣告槓牌的玩家 ID。
     * @param kanAction 本次暗槓或加槓動作。
     * @param incomingTile 觸發本次槓牌的牌張。
     * @param module 對局採用的規則模組。
     * @return 已套用的動作與補牌結果，或規則拒絕結果。
     */
    fun apply(
        state: TableState,
        declarerId: Uuid,
        kanAction: GameAction.Kan,
        incomingTile: IdentifiedTile,
        module: MahjongRuleModule<*>,
    ): Result {
        val scored = ActionScoringApplier.apply(
            before = state,
            after = kanCandidateState(state, declarerId, kanAction, incomingTile, module),
            actorPlayerId = declarerId,
            action = kanAction,
            sourcePlayerId = null,
            module = module,
        )
        return applySupplementalDraw(
            originalState = state,
            candidateState = scored.tableState,
            actorPlayerId = declarerId,
            action = kanAction,
            module = module,
        ).withScoring(scored.scoring)
    }

    /**
     * 只把宣告的槓或移出手牌的動作套用到宣告者手上、尚未補牌的候選桌況。
     *
     * 搶和反應視窗開啟時，平台依此先呈現宣告完成的樣子，其他玩家在動畫播完後才決定要不要搶；權威桌況要等
     * 反應結束才套用，不使用這份結果。
     *
     * @param state 尚未套用本次動作的權威桌況。
     * @param declarerId 宣告動作的玩家 ID。
     * @param action 本次暗槓、加槓或移出手牌的擴充動作。
     * @param incomingTile 觸發本次動作的牌張。
     * @param module 對局採用的規則模組。
     * @return 候選桌況；不是這三種動作或規則拒絕時為 null。
     */
    fun declaredState(
        state: TableState,
        declarerId: Uuid,
        action: GameAction,
        incomingTile: IdentifiedTile,
        module: MahjongRuleModule<*>,
    ): TableState? = when (action) {
        is GameAction.Kan -> kanCandidateState(state, declarerId, action, incomingTile, module)
        is GameAction.Extension -> setAsideCandidateState(state, declarerId, action, module)
        else -> null
    }

    /**
     * 套用移出手牌的動作並驗證結果，尚未收付與補牌。
     *
     * @param state 尚未套用本次動作的權威桌況。
     * @param declarerId 執行動作的玩家 ID。
     * @param action 本次移出手牌的擴充動作。
     * @param module 對局採用的規則模組。
     * @return 通過驗證的候選桌況；規則不支援這個動作時為 null。
     * @throws RuleContractViolationException 規則回傳的結果改動了不允許改動的資料時。
     */
    private fun setAsideCandidateState(
        state: TableState,
        declarerId: Uuid,
        action: GameAction.Extension,
        module: MahjongRuleModule<*>,
    ): TableState? {
        val declarer = state.players.first { it.id == declarerId }
        val setAsideTile = module.tileSetAsideBy(declarer, action) ?: return null
        val candidate = module.applyTileSetAsideAction(state, declarerId, action) ?: return null
        return RuleHookResultValidator.requireValidTileSetAside(state, candidate, declarerId, action, setAsideTile, module)
    }

    /** 套用暗槓或加槓的副露與宣告紀錄，尚未補牌。 */
    private fun kanCandidateState(
        state: TableState,
        declarerId: Uuid,
        kanAction: GameAction.Kan,
        incomingTile: IdentifiedTile,
        module: MahjongRuleModule<*>,
    ): TableState {
        val declarer = state.players.first { it.id == declarerId }
        val handAfterMeld = when (kanAction.type) {
            GameAction.KanType.CLOSED_KAN -> {
                val heldKanTiles = kanAction.withTiles.mapNotNull { id ->
                    declarer.hand.standingTiles.find { it.id == id }
                } + incomingTile
                val revealsClosedKanTiles = state.config.revealsClosedKanTiles
                // 公開的暗槓照規則牌序排列；不公開時改用與牌面無關的順序，排列不透露牌面。
                val kanTiles = if (revealsClosedKanTiles) {
                    heldKanTiles.sortedWith(compareBy(module.tileOrder) { it.tile })
                } else {
                    heldKanTiles.sortedBy { it.id.toString() }
                }
                declarer.hand.call(
                    type = MeldType.CLOSED_KAN,
                    tiles = kanTiles,
                    source = null,
                    direction = RelativeDirection.Self,
                    publiclyRevealedTileIds = closedKanPubliclyRevealedTileIds(kanTiles, revealsClosedKanTiles),
                    faceDownTileIds = closedKanFaceDownTileIds(kanTiles),
                )
            }

            GameAction.KanType.ADDED_KAN -> {
                val tileInterpretation = module.createTileInterpretationPolicy()
                val targetMeldIndex = declarer.hand.exposedMelds.indexOfFirst {
                    it.type == MeldType.PON &&
                        tileInterpretation.canonicalize(it.tiles.first().tile) ==
                        tileInterpretation.canonicalize(incomingTile.tile)
                }
                declarer.hand.upgradeToAddedKan(incomingTile, targetMeldIndex)
            }

            GameAction.KanType.OPEN_KAN -> error("Unreachable: OPEN_KAN is applied by RespondToDiscardUseCase")
        }

        val handReadyForSupplement = handAfterMeld.lastDrawn?.let { drawnTile ->
            handAfterMeld.copy(tiles = handAfterMeld.tiles + drawnTile, lastDrawn = null)
        } ?: handAfterMeld
        val declarerAfterMeld = declarer.copy(hand = handReadyForSupplement).recordAction(kanAction)
        val playersAfterMeld = state.players.map { player ->
            if (player.id == declarerId) declarerAfterMeld else player
        }
        val playersAfterHook = RuleHookResultValidator.requireRuleStateOnly(
            before = playersAfterMeld,
            after = module.onMeldClaimed(playersAfterMeld),
            hookName = "onMeldClaimed",
        )
        return state.copy(players = playersAfterHook)
    }

    /**
     * 套用移出手牌的擴充動作（見 [MahjongRuleModule.applyTileSetAsideAction]）與其點數收付，並將候選桌況交給規則補牌 policy。
     *
     * @param state 尚未套用本次動作的權威桌況。
     * @param declarerId 宣告動作的玩家 ID。
     * @param action 本次移出手牌的擴充動作。
     * @param module 對局採用的規則模組。
     */
    fun applyTileSetAside(
        state: TableState,
        declarerId: Uuid,
        action: GameAction.Extension,
        module: MahjongRuleModule<*>,
    ): Result {
        val candidateState = setAsideCandidateState(state, declarerId, action, module)
            ?: return Result.Rejected(SupplementalDrawReasonIds.INVALID_RESULT)
        val scored = ActionScoringApplier.apply(
            before = state,
            after = candidateState,
            actorPlayerId = declarerId,
            action = action,
            sourcePlayerId = null,
            module = module,
        )
        return applySupplementalDraw(state, scored.tableState, declarerId, action, module).withScoring(scored.scoring)
    }

    /** 成功套用時附上動作的點數收付；拒絕結果維持原樣。 */
    private fun Result.withScoring(scoring: AppliedActionScoring?): Result = when (this) {
        is Result.Applied -> copy(scoring = scoring)
        is Result.Rejected -> this
    }

    /**
     * 驗證並套用規則補牌結果；明槓、暗槓／加槓與移出手牌的動作共用此入口。
     *
     * @param originalState 動作套用前的權威桌況。
     * @param candidateState 已套用副露、尚未補牌的候選桌況。
     * @param actorPlayerId 執行動作的玩家 ID。
     * @param action 已套用的動作。
     * @param module 對局採用的規則模組。
     */
    fun applySupplementalDraw(
        originalState: TableState,
        candidateState: TableState,
        actorPlayerId: Uuid,
        action: GameAction,
        module: MahjongRuleModule<*>,
    ): Result {
        // 動作本身與它的收付可能已更新規則動態狀態；補牌前的公開檢查接在這份狀態之後，不退回動作前的狀態。
        val beforeReveal = WallRevealDecisionApplier.apply(
            originalState.copy(dynamicRuleState = candidateState.dynamicRuleState),
            WallRevealCheckpoint.BEFORE_SUPPLEMENTAL_DRAW,
            module,
            actorPlayerId,
            action,
        )
        if (beforeReveal is WallRevealDecisionApplier.Result.Rejected) {
            return Result.Rejected(beforeReveal.reasonId)
        }
        beforeReveal as WallRevealDecisionApplier.Result.Applied
        val stateBeforeSupplementalDraw = beforeReveal.tableState
        val candidateAfterBeforeReveal = candidateState.copy(
            dynamicRuleState = stateBeforeSupplementalDraw.dynamicRuleState,
        )
        val decision = module.createSupplementalDrawPolicy().resolve(
            SupplementalDrawContext(stateBeforeSupplementalDraw, candidateAfterBeforeReveal, actorPlayerId, action),
        )
        return when (decision) {
            SupplementalDrawDecision.NotRequired -> Result.Applied(
                candidateAfterBeforeReveal,
                emptyList(),
                listOf(beforeReveal.newlyRevealedTileIds).filterNot { it.isEmpty() },
            )
            is SupplementalDrawDecision.Rejected -> Result.Rejected(decision.reasonId)
            is SupplementalDrawDecision.Completed -> applyCompletedDecision(
                stateBeforeSupplementalDraw,
                candidateAfterBeforeReveal,
                actorPlayerId,
                decision,
                action,
                module,
            ).completeWallReveal(module, actorPlayerId, action, beforeReveal.newlyRevealedTileIds)
        }
    }

    /** 補牌成功後呼叫公開 policy，並保留補牌前／後兩批呈現順序。 */
    private fun Result.completeWallReveal(
        module: MahjongRuleModule<*>,
        actorPlayerId: Uuid,
        action: GameAction,
        revealedBeforeDraw: Set<Uuid>,
    ): Result {
        if (this is Result.Rejected) return this
        this as Result.Applied
        val afterReveal = WallRevealDecisionApplier.apply(
            tableState,
            WallRevealCheckpoint.AFTER_SUPPLEMENTAL_DRAW,
            module,
            actorPlayerId,
            action,
        )
        return when (afterReveal) {
            is WallRevealDecisionApplier.Result.Rejected -> Result.Rejected(afterReveal.reasonId)
            is WallRevealDecisionApplier.Result.Applied -> copy(
                tableState = afterReveal.tableState,
                wallRevealBatches = listOf(revealedBeforeDraw, afterReveal.newlyRevealedTileIds)
                    .filterNot { it.isEmpty() },
            )
        }
    }

    /** 驗證牌張守恆與公開範圍，成功後把補牌寫入執行者手牌。 */
    internal fun applyCompletedDecision(
        originalState: TableState,
        candidateState: TableState,
        actorPlayerId: Uuid,
        decision: SupplementalDrawDecision.Completed,
        action: GameAction,
        module: MahjongRuleModule<*>,
    ): Result {
        if (decision.drawnTiles.isEmpty()) return Result.Rejected(SupplementalDrawReasonIds.INVALID_RESULT)
        val originalWallTiles = originalState.tileWall.getAllTiles() + originalState.reservedWallTiles
        val resultingWallTiles = decision.tileWall.getAllTiles() + decision.reservedWallTiles + decision.drawnTiles
        if (originalWallTiles.map { it.id }.toSet() != resultingWallTiles.map { it.id }.toSet() ||
            resultingWallTiles.map { it.id }.distinct().size != resultingWallTiles.size
        ) {
            return Result.Rejected(SupplementalDrawReasonIds.INVALID_RESULT)
        }
        val actorIndex = candidateState.players.indexOfFirst { it.id == actorPlayerId }
        if (actorIndex == -1) return Result.Rejected(SupplementalDrawReasonIds.INVALID_RESULT)
        val actor = candidateState.players[actorIndex]
        val handAfterDraw = actor.hand.copy(
            tiles = actor.hand.tiles + decision.drawnTiles.dropLast(1),
            lastDrawn = decision.drawnTiles.last(),
        )
        val actorAfterDraw = actor.copy(hand = handAfterDraw).clearPassedTiles().recordAction(GameAction.Draw)
        val updatedPlayers = candidateState.players.toMutableList().apply { this[actorIndex] = actorAfterDraw }
        val stateWithoutResolvedPhysicalLayout = candidateState.copy(
            players = updatedPlayers,
            tileWall = decision.tileWall,
            reservedWallTiles = decision.reservedWallTiles,
            dynamicRuleState = decision.dynamicRuleState,
            physicalWallLayout = null,
        )
        val currentPhysicalLayout = originalState.physicalWallLayout
            ?: return Result.Applied(stateWithoutResolvedPhysicalLayout, decision.drawnTiles)
        val physicalDecision = module.createPhysicalWallLayoutPolicy().resolveTransitionValidated(
            PhysicalWallLayoutTransitionContext(
                tableStateBeforeAction = originalState,
                tableStateAfterAction = stateWithoutResolvedPhysicalLayout,
                currentLayout = currentPhysicalLayout,
                actorPlayerId = actorPlayerId,
                action = action,
            ),
        )
        val (updatedPhysicalLayout, phases) = when (physicalDecision) {
            PhysicalWallLayoutTransitionDecision.Unchanged -> currentPhysicalLayout to emptyList()
            is PhysicalWallLayoutTransitionDecision.Completed -> physicalDecision.layout to physicalDecision.phases
            is PhysicalWallLayoutTransitionDecision.Rejected -> return Result.Rejected(physicalDecision.reasonId)
        }
        val updatedState = stateWithoutResolvedPhysicalLayout.copy(physicalWallLayout = updatedPhysicalLayout)
        return Result.Applied(updatedState, decision.drawnTiles, physicalWallTransitionPhases = phases)
    }
}
