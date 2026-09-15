package com.doublemoon1119.mahjongcraft.flow.server.game.service

import com.doublemoon1119.mahjongcraft.flow.server.game.model.PlayerDecisionActionOption
import com.doublemoon1119.mahjongcraft.flow.server.game.model.PlayerDecisionOptions
import com.doublemoon1119.mahjongcraft.logic.base.GameAction
import com.doublemoon1119.mahjongcraft.logic.base.RelativeDirection
import com.doublemoon1119.mahjongcraft.logic.base.Tile
import com.doublemoon1119.mahjongcraft.logic.judgment.LegalActionValidator
import com.doublemoon1119.mahjongcraft.logic.module.MahjongModuleRegistry
import com.doublemoon1119.mahjongcraft.logic.table.MahjongPlayer
import com.doublemoon1119.mahjongcraft.logic.table.TableState

/** 集中解析合法動作、選牌需求與捨牌分析的 Flow 內部規則查詢服務。 */
internal object PlayerDecisionOptionsResolver {
    /**
     * 只解析合法動作，供不需要選牌與分析資料的既有查詢使用。
     *
     * 此路徑不建立捨牌分析器，避免 AI 的高頻合法動作查詢額外執行完整聽牌分析。
     */
    fun resolveActions(
        state: TableState,
        player: MahjongPlayer,
        moduleRegistry: MahjongModuleRegistry,
        actionContextResolver: PlayerActionContextResolver,
    ): List<GameAction> {
        val validator = moduleRegistry.getModule(state.config).createLegalActionValidator()
        val context = actionContextResolver.resolveFor(state, player.id)
        return resolveActions(state, player, validator, context)
    }

    /**
     * 依同一份 [state] 與 [player] 建立完整決策選項，避免各平台分別執行規則查詢。
     *
     * @param state 目前權威桌況。
     * @param player 欲查詢的玩家。
     * @param moduleRegistry 麻將規則模組註冊中心。
     * @param actionContextResolver 玩家操作情境解析器。
     */
    fun resolve(
        state: TableState,
        player: MahjongPlayer,
        moduleRegistry: MahjongModuleRegistry,
        actionContextResolver: PlayerActionContextResolver,
    ): PlayerDecisionOptions {
        val module = moduleRegistry.getModule(state.config)
        val validator = module.createLegalActionValidator()
        val analyzer = module.createDiscardReadinessAnalyzer()
        val context = actionContextResolver.resolveFor(state, player.id)
        val actions = resolveActions(state, player, validator, context)
        val ownTurn = context is PlayerActionContext.OwnTurn
        val actionOptions = actions.map { action ->
            val requirement = validator.tileSelectionRequirement(state, player, action)
            val analyses = if (ownTurn && requirement != null) {
                analyzer?.analyzeForAction(state, player, action)
                    ?.filter { it.discardTileId in requirement.eligibleTileIds }
                    .orEmpty()
            } else {
                emptyList()
            }
            PlayerDecisionActionOption(action, requirement, analyses)
        }
        return PlayerDecisionOptions(
            actions = actionOptions,
            discardAnalyses = if (ownTurn) analyzer?.analyze(state, player).orEmpty() else emptyList(),
            referenceTile = referenceTile(state, player, context),
        )
    }

    /** 依已解析的 [context] 執行一次合法動作查詢。 */
    private fun resolveActions(
        state: TableState,
        player: MahjongPlayer,
        validator: LegalActionValidator,
        context: PlayerActionContext?,
    ): List<GameAction> = when (context) {
        is PlayerActionContext.KanReaction ->
            validator.getLegalActions(
                tableState = state,
                player = player,
                sourceAction = context.pending.kanAction,
                sourceDirection = state.relativeDirectionOf(player.id, context.pending.declarerId),
                incomingTile = context.pending.robbedTile,
            ).filter { it is GameAction.Ron || it == GameAction.Pass }

        is PlayerActionContext.DiscardReaction -> {
            val pending = context.pending
            val discardedTile = state.players
                .first { it.id == pending.discarderId }
                .discardPile.entries
                .first { it.tile.id == pending.tileId }
                .tile
            validator.getLegalActions(
                tableState = state,
                player = player,
                sourceAction = GameAction.Discard(pending.tileId),
                sourceDirection = state.relativeDirectionOf(player.id, pending.discarderId),
                incomingTile = discardedTile,
            )
        }

        is PlayerActionContext.OwnTurn ->
            if (player.hand.lastDrawn != null) {
                validator.getLegalActions(state, player, GameAction.Draw, RelativeDirection.Self, null)
            } else {
                emptyList()
            }

        null -> emptyList()
    }

    /** 找出目前決策的觸發牌；沒有特定牌時回傳 null。 */
    private fun referenceTile(
        state: TableState,
        player: MahjongPlayer,
        context: PlayerActionContext?,
    ): Tile? = when (context) {
        is PlayerActionContext.KanReaction -> context.pending.robbedTile.tile
        is PlayerActionContext.DiscardReaction ->
            state.players
                .first { it.id == context.pending.discarderId }
                .discardPile.entries
                .first { it.tile.id == context.pending.tileId }
                .tile.tile
        is PlayerActionContext.OwnTurn -> player.hand.lastDrawn?.tile
        null -> null
    }
}
