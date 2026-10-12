package com.doublemoon1119.mahjongcraft.flow.server.game.service

import com.doublemoon1119.mahjongcraft.flow.common.game.model.Game
import com.doublemoon1119.mahjongcraft.flow.server.game.model.PlayerDecisionActionOption
import com.doublemoon1119.mahjongcraft.flow.server.game.model.PlayerDecisionOptions
import com.doublemoon1119.mahjongcraft.flow.server.game.policy.GameVisibilityPolicy
import com.doublemoon1119.mahjongcraft.logic.base.GameAction
import com.doublemoon1119.mahjongcraft.logic.module.AutomaticControlAction
import com.doublemoon1119.mahjongcraft.logic.module.AutomaticControlContext
import com.doublemoon1119.mahjongcraft.logic.module.MahjongModuleRegistry
import com.doublemoon1119.mahjongcraft.logic.table.visibleTiles
import org.koin.core.annotation.Single
import kotlin.uuid.Uuid

/**
 * 套用規則模組的自動操作政策，解析決策選項與可立即執行的動作。
 *
 * @property moduleRegistry 取得這一局規則模組。
 * @property visibilityPolicy 產生玩家本人的桌況快照，捨牌分析的剩餘張數只扣除其中看得到的牌。
 * @property actionContextResolver 判斷玩家目前的操作情境。
 */
@Single
class AutomaticDecisionEvaluator(
    private val moduleRegistry: MahjongModuleRegistry,
    private val visibilityPolicy: GameVisibilityPolicy,
    private val actionContextResolver: PlayerActionContextResolver = PlayerActionContextResolver(),
) {
    /**
     * 針對指定玩家解析目前決策；玩家不在對局時回傳 null，沒有操作權時回傳空選項，不自行推測動作。
     *
     * @param game 欲解析的權威遊戲。
     * @param playerId 欲解析決策的玩家識別碼。
     * @return 決策選項與政策指定的立即動作；玩家不在對局時為 null。
     */
    fun evaluate(game: Game, playerId: Uuid): EvaluatedPlayerDecision? {
        val state = game.tableState
        val player = state.players.firstOrNull { it.id == playerId } ?: return null
        val module = moduleRegistry.getModule(state.config)
        val options = PlayerDecisionOptionsResolver.resolve(
            state = state,
            player = player,
            visibleTiles = visibilityPolicy.snapshotFor(game, playerId).visibleTiles(),
            moduleRegistry = moduleRegistry,
            actionContextResolver = actionContextResolver,
        )
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

    /**
     * 防止規則擴充政策繞過合法動作與選牌契約。
     *
     * @param decision 規則政策要求立即執行的動作及選牌。
     * @param options 本次決策的合法選項。
     * @param playerId 決策玩家識別碼。
     * @param game 目前權威遊戲。
     */
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
