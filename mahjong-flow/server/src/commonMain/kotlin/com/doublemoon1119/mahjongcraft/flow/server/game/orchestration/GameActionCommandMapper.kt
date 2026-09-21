package com.doublemoon1119.mahjongcraft.flow.server.game.orchestration

import com.doublemoon1119.mahjongcraft.flow.common.game.model.GameCommand
import com.doublemoon1119.mahjongcraft.flow.server.game.service.PlayerActionContext
import com.doublemoon1119.mahjongcraft.logic.base.GameAction
import org.koin.core.annotation.Single
import kotlin.uuid.Uuid

/** 將已通過規則驗證的玩家動作轉換為 Flow 可執行的命令。 */
@Single
class GameActionCommandMapper(
    private val extensionRegistry: ExtensionGameActionCommandFactoryRegistry,
) {
    /**
     * 依玩家目前的操作情境轉換 [action]；無法在該情境執行或選牌資料不合法時回傳 null。
     */
    fun toCommand(
        context: PlayerActionContext,
        action: GameAction,
        selectedTileIds: List<Uuid> = emptyList(),
    ): GameCommand? {
        if (action is GameAction.Extension) {
            extensionRegistry.createCommand(action.value, selectedTileIds)?.let { return GameCommand.Extension(it) }
            if (selectedTileIds.isNotEmpty()) return null
        } else if (selectedTileIds.isNotEmpty()) {
            return null
        }
        return when (context) {
            is PlayerActionContext.KanReaction -> GameCommand.RespondToKan(action)
            is PlayerActionContext.DiscardReaction -> GameCommand.RespondToDiscard(action)
            is PlayerActionContext.OwnTurn -> action.toOwnTurnCommand()
        }
    }

    /** 將自己回合的內建動作轉換成命令。 */
    private fun GameAction.toOwnTurnCommand(): GameCommand? = when (this) {
        is GameAction.Discard -> GameCommand.Discard(tileId)
        GameAction.Tsumo -> GameCommand.Tsumo
        is GameAction.Kan -> GameCommand.Kan(type, tileId)
        is GameAction.ExhaustiveDraw -> GameCommand.DeclareExhaustiveDraw(reason)
        else -> null
    }
}
