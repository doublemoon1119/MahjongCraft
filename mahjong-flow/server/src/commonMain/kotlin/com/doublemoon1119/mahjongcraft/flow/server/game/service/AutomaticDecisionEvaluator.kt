package com.doublemoon1119.mahjongcraft.flow.server.game.service

import com.doublemoon1119.mahjongcraft.flow.common.game.model.Game
import com.doublemoon1119.mahjongcraft.flow.server.game.model.PlayerDecisionActionOption
import com.doublemoon1119.mahjongcraft.flow.server.game.model.PlayerDecisionOptions
import com.doublemoon1119.mahjongcraft.logic.base.GameAction
import com.doublemoon1119.mahjongcraft.logic.module.AutomaticControlAction
import com.doublemoon1119.mahjongcraft.logic.module.AutomaticControlContext
import com.doublemoon1119.mahjongcraft.logic.module.MahjongModuleRegistry
import org.koin.core.annotation.Single
import kotlin.uuid.Uuid

/** 套用規則模組的自動操作政策，解析決策選項與可立即執行的動作。 */
@Single
class AutomaticDecisionEvaluator(
    private val moduleRegistry: MahjongModuleRegistry,
    private val actionContextResolver: PlayerActionContextResolver = PlayerActionContextResolver(),
) {
    /** 針對指定玩家解析目前決策；沒有操作權時仍回傳空選項，不自行推測動作。 */
    fun evaluate(game: Game, playerId: Uuid): EvaluatedPlayerDecision? {
        val state = game.tableState
        val player = state.players.firstOrNull { it.id == playerId } ?: return null
        val module = moduleRegistry.getModule(state.config)
        val options = PlayerDecisionOptionsResolver.resolve(state, player, moduleRegistry, actionContextResolver)
        val supportedIds = module.getSupportedAutomaticControlIds()
        val enabledIds = game.enabledAutomaticControlIdsByPlayerId[playerId].orEmpty().intersect(supportedIds)
        val legalActions = options.actions.map(PlayerDecisionActionOption::action)
        val result = module.createAutomaticControlPolicy().evaluate(
            AutomaticControlContext(state, player, legalActions, enabledIds),
        )
        require(result.hiddenActions.all { it in legalActions }) {
            "Automatic control policy hid an action that is not currently legal"
        }
        result.immediateAction?.let { validateImmediateAction(it, options, playerId, game) }
        return EvaluatedPlayerDecision(
            options = options.copy(actions = options.actions.filterNot { it.action in result.hiddenActions }),
            immediateAction = result.immediateAction,
        )
    }

    /** 防止規則擴充政策繞過合法動作與選牌契約。 */
    private fun validateImmediateAction(
        decision: AutomaticControlAction,
        options: PlayerDecisionOptions,
        playerId: Uuid,
        game: Game,
    ) {
        val action = decision.action
        if (action is GameAction.Discard) {
            val lastDrawnId = game.tableState.players.first { it.id == playerId }.hand.lastDrawn?.id
            require(
                actionContextResolver.resolveFor(game.tableState, playerId) is PlayerActionContext.OwnTurn &&
                    action.tileId == lastDrawnId &&
                    decision.selectedTileIds.isEmpty(),
            ) {
                "Automatic discard must use the player's last drawn tile"
            }
            return
        }
        val option = options.actions.firstOrNull { it.action == decision.action }
        requireNotNull(option) { "Automatic control policy selected an action that is not currently legal" }
        val requirement = option.tileSelectionRequirement
        require(
            if (requirement == null) {
                decision.selectedTileIds.isEmpty()
            } else {
                decision.selectedTileIds.size in requirement.minCount..requirement.maxCount &&
                    decision.selectedTileIds.distinct().size == decision.selectedTileIds.size &&
                    decision.selectedTileIds.all { it in requirement.eligibleTileIds }
            },
        ) { "Automatic control policy selected tiles that violate the action requirement" }
    }
}

/** 一次自動操作政策評估後的玩家可見選項與立即動作。 */
data class EvaluatedPlayerDecision(
    val options: PlayerDecisionOptions,
    val immediateAction: AutomaticControlAction?,
)
