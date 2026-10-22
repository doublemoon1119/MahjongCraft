package com.doublemoon1119.mahjongcraft.flow.server.game.service

import com.doublemoon1119.mahjongcraft.flow.server.game.model.PlayerDecisionActionOption
import com.doublemoon1119.mahjongcraft.flow.server.game.model.PlayerDecisionOptions
import com.doublemoon1119.mahjongcraft.logic.base.GameAction
import com.doublemoon1119.mahjongcraft.logic.base.IdentifiedTile
import com.doublemoon1119.mahjongcraft.logic.base.Tile
import com.doublemoon1119.mahjongcraft.logic.judgment.LegalActionContext
import com.doublemoon1119.mahjongcraft.logic.judgment.LegalActionValidator
import com.doublemoon1119.mahjongcraft.logic.module.MahjongModuleRegistry
import com.doublemoon1119.mahjongcraft.logic.module.MahjongRuleModule
import com.doublemoon1119.mahjongcraft.logic.table.MahjongPlayer
import com.doublemoon1119.mahjongcraft.logic.table.TableState

/** 集中解析合法動作、選牌需求與捨牌分析的 Flow 內部規則查詢服務。 */
internal object PlayerDecisionOptionsResolver {
    /**
     * 只解析合法動作，供不需要選牌與分析資料的既有查詢使用。
     *
     * 此路徑不建立捨牌分析器，避免 AI 的高頻合法動作查詢額外執行完整聽牌分析。
     *
     * @param state 目前權威桌況。
     * @param player 欲查詢的玩家。
     * @param moduleRegistry 提供本局規則模組的註冊中心。
     * @param actionContextResolver 解析玩家操作情境的服務。
     * @return 目前可執行的合法動作。
     */
    fun resolveActions(
        state: TableState,
        player: MahjongPlayer,
        moduleRegistry: MahjongModuleRegistry,
        actionContextResolver: PlayerActionContextResolver,
    ): List<GameAction> {
        val module = moduleRegistry.getModule(state.config)
        val validator = module.createLegalActionValidator()
        val context = actionContextResolver.resolveFor(state, player.id)
        return resolveActions(state, player, validator, context, module)
    }

    /**
     * 依同一份 [state] 與 [player] 建立完整決策選項，避免各平台分別執行規則查詢。
     *
     * @param state 目前權威桌況。
     * @param player 欲查詢的玩家。
     * @param visibleTiles 這位玩家看得到牌面的每一張實體牌，供捨牌分析扣除剩餘張數。
     * @param moduleRegistry 麻將規則模組註冊中心。
     * @param actionContextResolver 玩家操作情境解析器。
     * @return 合法動作、選牌需求與可用的捨牌分析。
     */
    fun resolve(
        state: TableState,
        player: MahjongPlayer,
        visibleTiles: List<IdentifiedTile>,
        moduleRegistry: MahjongModuleRegistry,
        actionContextResolver: PlayerActionContextResolver,
    ): PlayerDecisionOptions {
        val module = moduleRegistry.getModule(state.config)
        val validator = module.createLegalActionValidator()
        val analyzer = module.createDiscardReadinessAnalyzer()
        val context = actionContextResolver.resolveFor(state, player.id)
        val actions = resolveActions(state, player, validator, context, module)
        val ownTurn = context is PlayerActionContext.OwnTurn
        val actionOptions = actions.map { action ->
            val requirement = validator.tileSelectionRequirement(state, player, action)
            val analyses = if (ownTurn && requirement != null) {
                analyzer?.analyzeForAction(tableState = state, player = player, action = action, visibleTiles = visibleTiles)
                    ?.filter { it.discardTileId in requirement.eligibleTileIds }
                    .orEmpty()
            } else {
                emptyList()
            }
            PlayerDecisionActionOption(action, requirement, analyses)
        }
        return PlayerDecisionOptions(
            actions = actionOptions,
            discardAnalyses = if (ownTurn) analyzer?.analyze(tableState = state, player = player, visibleTiles = visibleTiles).orEmpty() else emptyList(),
            referenceTile = referenceTile(state, player, context),
        )
    }

    /**
     * 依已解析的 [context] 執行一次合法動作查詢。
     *
     * @param state 目前權威桌況。
     * @param player 欲查詢的玩家。
     * @param validator 本局規則模組提供的合法動作判定器。
     * @param context 已解析的操作情境；玩家目前沒有操作權時為 null。
     * @param module 本局規則模組。
     * @return 目前可執行的合法動作。
     */
    private fun resolveActions(
        state: TableState,
        player: MahjongPlayer,
        validator: LegalActionValidator,
        context: PlayerActionContext?,
        module: MahjongRuleModule<*>,
    ): List<GameAction> = when (context) {
        is PlayerActionContext.RobbingReaction ->
            validator.getLegalActions(
                LegalActionContext.declarationReaction(
                    tableState = state,
                    playerId = player.id,
                    declarerId = context.pending.declarerId,
                    declaredAction = context.pending.declaredAction,
                    declaredTile = context.pending.robbedTile,
                ),
            ).filter { it is GameAction.Ron || it == GameAction.Pass }

        is PlayerActionContext.DiscardReaction -> {
            val pending = context.pending
            val discardedTile = state.players
                .first { it.id == pending.discarderId }
                .discardPile.entries
                .first { it.tile.id == pending.tileId }
                .tile
            DiscardReactionOptionsResolver.resolveCandidates(
                state = state,
                module = module,
                playerId = player.id,
                discarderId = pending.discarderId,
                discardedTile = discardedTile,
            ).map { it.action }.distinct()
        }

        is PlayerActionContext.OwnTurn ->
            validator.getLegalActions(LegalActionContext.ownTurn(state, player.id))

        null -> emptyList()
    }

    /**
     * 找出目前決策的觸發牌；沒有特定牌時回傳 null。
     *
     * @param state 目前權威桌況。
     * @param player 欲查詢的玩家。
     * @param context 已解析的玩家操作情境。
     * @return 反應牌或摸入牌；沒有特定牌或無操作情境時為 null。
     */
    private fun referenceTile(
        state: TableState,
        player: MahjongPlayer,
        context: PlayerActionContext?,
    ): Tile? = when (context) {
        is PlayerActionContext.RobbingReaction -> context.pending.robbedTile.tile
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
