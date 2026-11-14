package com.doublemoon1119.mahjongcraft.flow.server.game.usecase

import com.doublemoon1119.mahjongcraft.flow.common.game.model.AppliedActionScoring
import com.doublemoon1119.mahjongcraft.flow.server.game.orchestration.RuleContractViolationException
import com.doublemoon1119.mahjongcraft.logic.base.GameAction
import com.doublemoon1119.mahjongcraft.logic.base.IdentifiedTile
import com.doublemoon1119.mahjongcraft.logic.base.MeldType
import com.doublemoon1119.mahjongcraft.logic.base.RelativeDirection
import com.doublemoon1119.mahjongcraft.logic.base.closedKanFaceDownTileIds
import com.doublemoon1119.mahjongcraft.logic.base.closedKanPubliclyRevealedTileIds
import com.doublemoon1119.mahjongcraft.logic.base.toValue
import com.doublemoon1119.mahjongcraft.logic.component.ComponentRegistry
import com.doublemoon1119.mahjongcraft.logic.module.MahjongRuleModule
import com.doublemoon1119.mahjongcraft.logic.module.MeldCompletedContext
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
    /** 已套用移出手牌動作及其收付、尚未補牌的結果。 */
    data class SetAsideDeclaration(
        val tableState: TableState,
        val scoring: AppliedActionScoring?,
    )

    /** 自己回合宣告的動作與補牌的套用結果。 */
    sealed interface Result {
        /**
         * 完整動作已成功套用。
         *
         * @property tableState 更新後桌況。
         * @property declaredState 動作已成立、補牌之前的桌況，包含動作的收付與補牌前公開的牌；呈現補牌前的那一步使用。
         * @property drawnTiles 規則實際補到的牌張。
         * @property physicalWallTransitionPhases 已通過規則驗證的實體牌牆移動階段，在補牌那一步依序完成。
         * @property scoring 動作成立當下的點數收付；不收付時為 null。
         */
        data class Applied(
            val tableState: TableState,
            val declaredState: TableState,
            val drawnTiles: List<IdentifiedTile>,
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
     * @param componentRegistry 解析及驗證補牌組件的正式定義。
     * @param module 對局採用的規則模組。
     * @return 已套用的動作與補牌結果，或規則拒絕結果。
     */
    fun apply(
        state: TableState,
        declarerId: Uuid,
        kanAction: GameAction.Kan,
        incomingTile: IdentifiedTile,
        module: MahjongRuleModule<*>,
        componentRegistry: ComponentRegistry,
    ): Result {
        val scored = ActionScoringApplier.apply(
            before = state,
            after = kanCandidateState(state, declarerId, kanAction, incomingTile, module, componentRegistry),
            actorPlayerId = declarerId,
            action = kanAction,
            sourcePlayerId = null,
            module = module,
            componentRegistry = componentRegistry,
        )
        return applySupplementalDraw(
            originalState = state,
            candidateState = scored.tableState,
            actorPlayerId = declarerId,
            action = kanAction,
            module = module,
            componentRegistry = componentRegistry,
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
     * @param componentRegistry 初始化與驗證組件的正式定義。
     * @return 候選桌況；不是這三種動作或規則拒絕時為 null。
     */
    fun declaredState(
        state: TableState,
        declarerId: Uuid,
        action: GameAction,
        incomingTile: IdentifiedTile,
        module: MahjongRuleModule<*>,
        componentRegistry: ComponentRegistry,
    ): TableState? = when (action) {
        is GameAction.Kan -> kanCandidateState(state, declarerId, action, incomingTile, module, componentRegistry)
        is GameAction.Extension -> setAsideCandidateState(state, declarerId, action, module, componentRegistry)
        else -> null
    }

    /**
     * 套用移出手牌的動作並驗證結果，尚未收付與補牌。
     *
     * @param state 尚未套用本次動作的權威桌況。
     * @param declarerId 執行動作的玩家 ID。
     * @param action 本次移出手牌的擴充動作。
     * @param module 對局採用的規則模組。
     * @param componentRegistry 驗證組件的正式定義。
     * @return 通過驗證的候選桌況；規則不支援這個動作時為 null。
     * @throws RuleContractViolationException 規則回傳的結果改動了不允許改動的資料時。
     */
    private fun setAsideCandidateState(
        state: TableState,
        declarerId: Uuid,
        action: GameAction.Extension,
        module: MahjongRuleModule<*>,
        componentRegistry: ComponentRegistry,
    ): TableState? {
        val declarer = state.players.first { it.id == declarerId }
        val setAsideTile = module.tileSetAsideBy(declarer, action) ?: return null
        val candidate = module.applyTileSetAsideAction(state, declarerId, action) ?: return null
        return RuleHookResultValidator.requireValidTileSetAside(
            before = state,
            after = candidate,
            actorPlayerId = declarerId,
            action = action,
            setAsideTile = setAsideTile,
            components = RuleComponentChecker(componentRegistry, module.id),
        )
    }

    /** 套用暗槓或加槓的副露與宣告紀錄，新副露帶上組件初值，尚未補牌。 */
    private fun kanCandidateState(
        state: TableState,
        declarerId: Uuid,
        kanAction: GameAction.Kan,
        incomingTile: IdentifiedTile,
        module: MahjongRuleModule<*>,
        componentRegistry: ComponentRegistry,
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
                    heldKanTiles.sortedWith(compareBy(module.tileOrder.valueOrder) { it.toValue() })
                } else {
                    heldKanTiles.sortedBy { it.id.toString() }
                }
                declarer.hand.call(
                    type = MeldType.ClosedKan,
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
                    it.type == MeldType.Pon &&
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
        val ruleComponents = RuleComponentChecker(componentRegistry, module.id)
        val stateAfterMeld = ruleComponents.initializeCreatedRecords(
            before = state,
            after = state.copy(players = playersAfterMeld),
            source = "Kan declaration",
        )
        val playersAfterMeldHook = ruleComponents.applyPlayerPatches(
            tableState = stateAfterMeld,
            players = stateAfterMeld.players,
            patches = module.onMeldCompleted(
                context = MeldCompletedContext(
                    playersBefore = state.players,
                    playersAfter = stateAfterMeld.players,
                    actorPlayerId = declarerId,
                    meldType = when (kanAction.type) {
                        GameAction.KanType.CLOSED_KAN -> MeldType.ClosedKan
                        GameAction.KanType.ADDED_KAN -> MeldType.AddedKan
                        GameAction.KanType.OPEN_KAN -> error("Unreachable: OPEN_KAN is applied by RespondToDiscardUseCase")
                    },
                ),
            ),
            source = "onMeldCompleted",
        )
        return stateAfterMeld.copy(players = playersAfterMeldHook)
    }

    /**
     * 套用移出手牌的擴充動作（見 [MahjongRuleModule.applyTileSetAsideAction]）與其點數收付，並將候選桌況交給規則補牌 policy。
     *
     * @param state 尚未套用本次動作的權威桌況。
     * @param declarerId 宣告動作的玩家 ID。
     * @param action 本次移出手牌的擴充動作。
     * @param componentRegistry 解析及驗證補牌組件的正式定義。
     * @param module 對局採用的規則模組。
     */
    fun applyTileSetAside(
        state: TableState,
        declarerId: Uuid,
        action: GameAction.Extension,
        module: MahjongRuleModule<*>,
        componentRegistry: ComponentRegistry,
    ): Result {
        val declaration = applyTileSetAsideDeclaration(state, declarerId, action, module, componentRegistry)
            ?: return Result.Rejected(SupplementalDrawReasonIds.INVALID_RESULT)
        return applySupplementalDraw(
            originalState = state,
            candidateState = declaration.tableState,
            actorPlayerId = declarerId,
            action = action,
            module = module,
            componentRegistry = componentRegistry,
        ).withScoring(declaration.scoring)
    }

    /**
     * 套用移出手牌動作與其點數收付，但不執行補牌。
     *
     * @param state 動作前的權威桌況。
     * @param declarerId 執行動作的玩家。
     * @param action 要套用的擴充動作。
     * @param module 對局採用的規則模組。
     * @param componentRegistry 解析及驗證組件的正式定義。
     * @return 動作成立後、補牌前的結果；規則不接受動作時為 null。
     */
    fun applyTileSetAsideDeclaration(
        state: TableState,
        declarerId: Uuid,
        action: GameAction.Extension,
        module: MahjongRuleModule<*>,
        componentRegistry: ComponentRegistry,
    ): SetAsideDeclaration? {
        val candidateState = setAsideCandidateState(state, declarerId, action, module, componentRegistry) ?: return null
        val scored = ActionScoringApplier.apply(
            before = state,
            after = candidateState,
            actorPlayerId = declarerId,
            action = action,
            sourcePlayerId = null,
            module = module,
            componentRegistry = componentRegistry,
        )
        return SetAsideDeclaration(scored.tableState, scored.scoring)
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
     * @param componentRegistry 解析及驗證補牌組件的正式定義。
     * @param module 對局採用的規則模組。
     */
    fun applySupplementalDraw(
        originalState: TableState,
        candidateState: TableState,
        actorPlayerId: Uuid,
        action: GameAction,
        module: MahjongRuleModule<*>,
        componentRegistry: ComponentRegistry,
    ): Result {
        // 動作本身與它的收付可能已更新對局組件；補牌前的公開檢查接在這份狀態之後，不退回動作前的狀態。
        val beforeReveal = WallRevealDecisionApplier.apply(
            tableState = originalState.copy(components = candidateState.components),
            checkpoint = WallRevealCheckpoint.BEFORE_SUPPLEMENTAL_DRAW,
            module = module,
            actorPlayerId = actorPlayerId,
            sourceAction = action,
            componentRegistry = componentRegistry,
        )
        if (beforeReveal is WallRevealDecisionApplier.Result.Rejected) {
            return Result.Rejected(beforeReveal.reasonId)
        }
        beforeReveal as WallRevealDecisionApplier.Result.Applied
        val stateBeforeSupplementalDraw = beforeReveal.tableState
        val candidateAfterBeforeReveal = candidateState.copy(
            components = stateBeforeSupplementalDraw.components,
            publicTileEvidenceIds = stateBeforeSupplementalDraw.publicTileEvidenceIds,
        )
        val decision = module.createSupplementalDrawPolicy().resolve(
            context = SupplementalDrawContext(stateBeforeSupplementalDraw, candidateAfterBeforeReveal, actorPlayerId, action),
        )
        return when (decision) {
            SupplementalDrawDecision.NotRequired -> Result.Applied(
                tableState = candidateAfterBeforeReveal,
                declaredState = candidateAfterBeforeReveal,
                drawnTiles = emptyList(),
            )
            is SupplementalDrawDecision.Rejected -> Result.Rejected(decision.reasonId)
            is SupplementalDrawDecision.Completed -> applyCompletedDecision(
                originalState = stateBeforeSupplementalDraw,
                candidateState = candidateAfterBeforeReveal,
                actorPlayerId = actorPlayerId,
                decision = decision,
                action = action,
                module = module,
                componentRegistry = componentRegistry,
            ).completeWallReveal(module, actorPlayerId, action, componentRegistry)
        }
    }

    /** 補牌成功後呼叫公開 policy。 */
    private fun Result.completeWallReveal(
        module: MahjongRuleModule<*>,
        actorPlayerId: Uuid,
        action: GameAction,
        componentRegistry: ComponentRegistry,
    ): Result {
        if (this is Result.Rejected) return this
        this as Result.Applied
        val afterReveal = WallRevealDecisionApplier.apply(
            tableState = tableState,
            checkpoint = WallRevealCheckpoint.AFTER_SUPPLEMENTAL_DRAW,
            module = module,
            actorPlayerId = actorPlayerId,
            sourceAction = action,
            componentRegistry = componentRegistry,
        )
        return when (afterReveal) {
            is WallRevealDecisionApplier.Result.Rejected -> Result.Rejected(afterReveal.reasonId)
            is WallRevealDecisionApplier.Result.Applied -> copy(tableState = afterReveal.tableState)
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
        componentRegistry: ComponentRegistry,
    ): Result {
        if (decision.drawnTiles.isEmpty()) return Result.Rejected(SupplementalDrawReasonIds.INVALID_RESULT)
        val originalWallTiles = originalState.tileWall.getAllTiles() + originalState.reservedWallTiles
        val resultingWallTiles = buildList {
            addAll(decision.tileWall.getAllTiles())
            addAll(decision.reservedWallTiles)
            addAll(decision.drawnTiles)
        }
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
            components = RuleComponentChecker(componentRegistry, module.id).applyTablePatch(candidateState, decision.tableComponents, "supplementalDrawPolicy"),
            physicalWallLayout = null,
        )
        val currentPhysicalLayout = originalState.physicalWallLayout
            ?: return Result.Applied(stateWithoutResolvedPhysicalLayout, candidateState, decision.drawnTiles)
        val physicalDecision = module.createPhysicalWallLayoutPolicy().resolveTransitionValidated(
            context = PhysicalWallLayoutTransitionContext(
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
        return Result.Applied(updatedState, candidateState, decision.drawnTiles, physicalWallTransitionPhases = phases)
    }
}
