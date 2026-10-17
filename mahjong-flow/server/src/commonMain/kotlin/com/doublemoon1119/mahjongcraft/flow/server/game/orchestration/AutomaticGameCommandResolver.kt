package com.doublemoon1119.mahjongcraft.flow.server.game.orchestration

import com.doublemoon1119.mahjongcraft.flow.common.game.model.GameCommand
import com.doublemoon1119.mahjongcraft.flow.server.game.service.PlayerActionContext
import com.doublemoon1119.mahjongcraft.flow.server.game.service.PlayerActionContextResolver
import com.doublemoon1119.mahjongcraft.flow.server.game.service.PlayerDecisionOptionsResolver
import com.doublemoon1119.mahjongcraft.flow.server.game.service.isDrawWindowOpenFor
import com.doublemoon1119.mahjongcraft.logic.base.GameAction
import com.doublemoon1119.mahjongcraft.logic.module.MahjongModuleRegistry
import com.doublemoon1119.mahjongcraft.logic.table.TableState
import kotlin.uuid.Uuid

/**
 * 依目前權威桌況，為伺服器代操作選出一個安全的內建命令。
 *
 * 這個解析器只會選擇目前操作情境真正允許的命令：反應視窗必須能合法跳過，自己回合則選擇
 * 強制捨牌或一張現有手牌。它不會把無法映射的擴充動作改送成另一種動作；找不到安全候選時
 * 回傳 null，讓呼叫端進入明確錯誤處理。
 *
 * @property moduleRegistry 提供本局合法動作與強制捨牌規則的註冊中心。
 * @property actionContextResolver 解析目前玩家操作情境的服務。
 */
class AutomaticGameCommandResolver(
    private val moduleRegistry: MahjongModuleRegistry,
    private val actionContextResolver: PlayerActionContextResolver = PlayerActionContextResolver(),
) {
    /**
     * 依 [state] 為指定玩家選出一個安全的代操作命令。
     *
     * @param state 目前的權威桌況。
     * @param playerId 需要代操作的玩家。
     * @return 合法且可映射的命令；沒有安全候選時為 null。
     */
    fun resolve(state: TableState, playerId: Uuid): GameCommand? {
        val context = actionContextResolver.resolveFor(state, playerId)
        if (context != null) {
            return when (context) {
                is PlayerActionContext.RobbingReaction ->
                    resolvePass(state, playerId, GameCommand::RespondToRobbing)

                is PlayerActionContext.DiscardReaction ->
                    resolvePass(state, playerId, GameCommand::RespondToDiscard)

                is PlayerActionContext.OwnTurn -> resolveDiscard(state, playerId)
            }
        }
        return if (state.isDrawWindowOpenFor(playerId)) GameCommand.Draw else null
    }

    /** 只有規則也把 Pass 列為合法動作時，才包裝成對應的反應命令。 */
    private fun resolvePass(
        state: TableState,
        playerId: Uuid,
        wrap: (GameAction) -> GameCommand,
    ): GameCommand? {
        val player = state.players.firstOrNull { it.id == playerId } ?: return null
        val legalActions = PlayerDecisionOptionsResolver.resolveActions(
            state = state,
            player = player,
            moduleRegistry = moduleRegistry,
            actionContextResolver = actionContextResolver,
        )
        return GameAction.Pass.takeIf { it in legalActions }?.let(wrap)
    }

    /** 依強制捨牌限制或手牌中的穩定順序選出一張捨牌。 */
    private fun resolveDiscard(state: TableState, playerId: Uuid): GameCommand? {
        val player = state.players.firstOrNull { it.id == playerId } ?: return null
        val forcedTileId = moduleRegistry.getModule(state.config).forcedDiscardTileId(state, player)
        val tileId = if (forcedTileId != null) {
            forcedTileId.takeIf { candidate -> player.hand.standingTiles.any { it.id == candidate } }
        } else {
            player.hand.lastDrawn?.id ?: player.hand.tiles.firstOrNull()?.id
        }
        return tileId?.let(GameCommand::Discard)
    }
}
