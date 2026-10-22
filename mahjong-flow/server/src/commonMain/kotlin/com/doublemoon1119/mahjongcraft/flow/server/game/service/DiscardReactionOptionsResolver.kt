package com.doublemoon1119.mahjongcraft.flow.server.game.service

import com.doublemoon1119.mahjongcraft.flow.common.game.model.GameAbortReasonIds
import com.doublemoon1119.mahjongcraft.flow.server.game.orchestration.RuleContractViolationException
import com.doublemoon1119.mahjongcraft.logic.base.GameAction
import com.doublemoon1119.mahjongcraft.logic.base.IdentifiedTile
import com.doublemoon1119.mahjongcraft.logic.judgment.LegalActionContext
import com.doublemoon1119.mahjongcraft.logic.judgment.LegalActionValidator
import com.doublemoon1119.mahjongcraft.logic.module.DiscardReactionCandidate
import com.doublemoon1119.mahjongcraft.logic.module.DiscardReactionPolicy
import com.doublemoon1119.mahjongcraft.logic.module.DiscardReactionPolicyContext
import com.doublemoon1119.mahjongcraft.logic.module.MahjongRuleModule
import com.doublemoon1119.mahjongcraft.logic.table.TableState
import kotlin.uuid.Uuid

/**
 * 以同一份規則 policy 產生捨牌反應選項並驗證提交內容。
 *
 * 這個服務不替每組選牌建立新的識別碼；選牌仍由候選動作的實體牌 ID 表示，提交時再次套用同一個
 * policy 並比對完整動作與選牌，避免查詢結果被當成永久授權。
 */
internal object DiscardReactionOptionsResolver {
    /**
     * 從同一份權威桌況建立反應候選。
     *
     * @param state 目前權威桌況。
     * @param module 本局規則模組。
     * @param playerId 欲反應的玩家。
     * @param discarderId 捨牌玩家。
     * @param discardedTile 目前待反應的實體捨牌。
     * @return 目前規則可執行的候選。
     */
    fun resolveCandidates(
        state: TableState,
        module: MahjongRuleModule<*>,
        playerId: Uuid,
        discarderId: Uuid,
        discardedTile: IdentifiedTile,
    ): List<DiscardReactionCandidate> {
        val validator = module.createLegalActionValidator()
        val legalActions = discardReactionLegalActions(validator, state, playerId, discarderId, discardedTile)
        return resolveCandidates(
            context = DiscardReactionPolicyContext(state, discarderId, playerId, discardedTile, legalActions),
            policy = module.createDiscardReactionPolicy(),
        ).onEach { candidate ->
            val player = state.players.first { it.id == playerId }
            val requirement = validator.tileSelectionRequirement(state, player, candidate.action)
            requireRuleResult(
                requirement == null ||
                    (
                        candidate.selectedTileIds.size in requirement.minCount..requirement.maxCount &&
                            requirement.eligibleTileIds.containsAll(candidate.selectedTileIds)
                        ),
                "Reaction policy offered a selection outside its declared requirement: ${candidate.action}",
            )
        }
    }

    /**
     * 取得目前規則允許的反應候選。
     *
     * @param context 權威桌況、來源捨牌與規則合法動作。
     * @param policy 本局規則提供的反應 policy。
     * @return 規則可執行的反應候選。
     */
    fun resolveCandidates(
        context: DiscardReactionPolicyContext,
        policy: DiscardReactionPolicy,
    ): List<DiscardReactionCandidate> = policy.resolve(context).onEach { candidate ->
        requireRuleResult(candidate.action in context.legalActions, "Reaction policy offered an action outside legal actions")
        requireRuleResult(
            candidate.claimEffect != null || candidate.action == GameAction.Pass || candidate.action is GameAction.Ron,
            "Reaction policy offered a claim without an effect: ${candidate.action}",
        )
        requireRuleResult(isSafeCandidate(context, candidate), "Reaction policy offered an invalid claim effect: ${candidate.action}")
    }

    /**
     * 驗證玩家提交的完整反應，不建立額外候選 ID。
     *
     * @param context 權威桌況、來源捨牌與規則合法動作。
     * @param policy 本局規則提供的反應 policy。
     * @param action 玩家提交的動作。
     * @param selectedTileIds 玩家額外選出的實體牌 ID。
     * @return 對應的候選及其效果；查不到完全相符候選時為 `null`。
     */
    fun resolveSubmission(
        context: DiscardReactionPolicyContext,
        policy: DiscardReactionPolicy,
        action: GameAction,
        selectedTileIds: List<Uuid>,
    ): DiscardReactionCandidate? {
        if (action !in context.legalActions) return null
        if (resolveCandidates(context, policy).none { it.action == action }) return null
        val normalizedSelectedTileIds = if (selectedTileIds.isEmpty()) {
            builtinSelectedTileIds(action)
        } else {
            selectedTileIds
        }
        val responder = context.tableState.players.firstOrNull { it.id == context.responderId } ?: return null
        val ownedIds = responder.hand.standingTiles.mapTo(mutableSetOf()) { it.id }
        if (normalizedSelectedTileIds.size != normalizedSelectedTileIds.toSet().size ||
            !ownedIds.containsAll(normalizedSelectedTileIds)
        ) {
            return null
        }
        val candidate = policy.resolveSelection(context, action, normalizedSelectedTileIds) ?: return null
        requireRuleResult(candidate.action == action, "Reaction policy replaced the submitted action")
        requireRuleResult(candidate.selectedTileIds == normalizedSelectedTileIds, "Reaction policy replaced the submitted selection")
        requireRuleResult(isSafeCandidate(context, candidate), "Reaction policy returned an invalid claim effect for $action")
        return candidate
    }

    /**
     * 從權威桌況重新建立並驗證反應提交。
     *
     * @param state 目前權威桌況。
     * @param module 本局規則模組。
     * @param playerId 提交反應的玩家。
     * @param discarderId 捨牌玩家。
     * @param discardedTile 目前待反應的實體捨牌。
     * @param action 玩家提交的動作。
     * @param selectedTileIds 玩家額外選出的實體牌 ID。
     * @return 重新計算後的候選；提交不合法時為 `null`。
     */
    fun resolveSubmission(
        state: TableState,
        module: MahjongRuleModule<*>,
        playerId: Uuid,
        discarderId: Uuid,
        discardedTile: IdentifiedTile,
        action: GameAction,
        selectedTileIds: List<Uuid>,
    ): DiscardReactionCandidate? {
        val validator = module.createLegalActionValidator()
        val legalActions = discardReactionLegalActions(validator, state, playerId, discarderId, discardedTile)
        val player = state.players.firstOrNull { it.id == playerId } ?: return null
        val effectiveSelectedTileIds = if (selectedTileIds.isEmpty()) builtinSelectedTileIds(action) else selectedTileIds
        if (action !in legalActions) return null
        val requirement = validator.tileSelectionRequirement(state, player, action)
        if (requirement != null &&
            (
                effectiveSelectedTileIds.size !in requirement.minCount..requirement.maxCount ||
                    !requirement.eligibleTileIds.containsAll(effectiveSelectedTileIds)
                )
        ) {
            return null
        }
        return resolveSubmission(
            context = DiscardReactionPolicyContext(state, discarderId, playerId, discardedTile, legalActions),
            policy = module.createDiscardReactionPolicy(),
            action = action,
            selectedTileIds = effectiveSelectedTileIds,
        )
    }

    /**
     * 規則對這次捨牌回傳的合法反應；已開啟的視窗不允許某位玩家榮和時，去掉他的榮和。
     * 只剩過牌時一併去掉，該玩家沒有可做的反應。
     *
     * @param validator 本局規則的合法動作判定器。
     * @param state 目前權威桌況；有反應視窗時依視窗的榮和名單過濾。
     * @param playerId 欲反應的玩家。
     * @param discarderId 捨牌玩家。
     * @param discardedTile 目前待反應的實體捨牌。
     * @return 這位玩家在目前視窗可做的合法反應。
     */
    private fun discardReactionLegalActions(
        validator: LegalActionValidator,
        state: TableState,
        playerId: Uuid,
        discarderId: Uuid,
        discardedTile: IdentifiedTile,
    ): List<GameAction> {
        val legalActions = validator.getLegalActions(
            LegalActionContext.discardReaction(state, playerId, discarderId, discardedTile),
        )
        val window = state.pendingReaction ?: return legalActions
        if (playerId in window.ronPlayerIds) return legalActions
        val withoutRon = legalActions.filterNot { it is GameAction.Ron }
        return if (withoutRon.all { it == GameAction.Pass }) emptyList() else withoutRon
    }

    /** 從內建完整反應動作取得已指定的手牌，擴充選牌仍使用獨立提交資料。 */
    private fun builtinSelectedTileIds(action: GameAction): List<Uuid> = when (action) {
        is GameAction.Chi -> action.withTiles
        is GameAction.Pon -> action.withTiles
        is GameAction.Kan -> action.withTiles
        else -> emptyList()
    }

    /** 驗證規則效果只會取用當前來源牌與已選取的本人牌。 */
    private fun isSafeCandidate(
        context: DiscardReactionPolicyContext,
        candidate: DiscardReactionCandidate,
    ): Boolean {
        val responder = context.tableState.players.firstOrNull { it.id == context.responderId } ?: return false
        val discarder = context.tableState.players.firstOrNull { it.id == context.discarderId } ?: return false
        val sourceEntry = discarder.discardPile.entries.lastOrNull() ?: return false
        if (sourceEntry.isTaken || sourceEntry.tile != context.discardedTile || responder.id == discarder.id) return false
        val actionSourceId = when (val action = candidate.action) {
            is GameAction.Ron -> action.tileId
            is GameAction.Chi -> action.tileId
            is GameAction.Pon -> action.tileId
            is GameAction.Kan -> action.tileId
            else -> context.discardedTile.id
        }
        if (actionSourceId != context.discardedTile.id) return false
        val ownedIds = responder.hand.standingTiles.map { it.id }.toSet()
        val selectedIds = candidate.selectedTileIds
        if (selectedIds.size != selectedIds.toSet().size || !ownedIds.containsAll(selectedIds)) return false
        val effect = candidate.claimEffect ?: return selectedIds.isEmpty() &&
            (candidate.action == GameAction.Pass || candidate.action is GameAction.Ron)
        val allMeldIds = effect.handTileIds.toSet() + context.discardedTile.id
        if (effect.handTileIds != selectedIds || !ownedIds.containsAll(effect.handTileIds)) return false
        if (!allMeldIds.containsAll(effect.publiclyRevealedTileIds)) return false
        if (!allMeldIds.containsAll(effect.faceDownTileIds)) return false
        if (context.discardedTile.id !in effect.publiclyRevealedTileIds) return false
        return candidate.action !is GameAction.Pass && candidate.action !is GameAction.Ron
    }

    /** 規則回傳矛盾效果時以明確的契約錯誤停止流程，不視為玩家的非法選擇。 */
    private fun requireRuleResult(condition: Boolean, diagnostic: String) {
        if (!condition) throw RuleContractViolationException(GameAbortReasonIds.RULE_EXECUTION_FAILED, diagnostic)
    }
}
