package com.doublemoon1119.mahjongcraft.flow.server.game.orchestration

import com.doublemoon1119.mahjongcraft.logic.base.GameAction
import com.doublemoon1119.mahjongcraft.logic.table.TableState
import kotlin.uuid.Uuid

/**
 * 一次玩家動作完成、且已到達可判定後續規則結果的權威上下文。
 *
 * [action] 是實際完成的動作，不以規則專用 trigger 類型代替；各 resolver 應自行辨認是否關心該動作。
 * 呼叫端必須等候相關反應與必要的後續胡牌機會結束後才建立 context，避免提早終止合法流程。
 *
 * @property actorPlayerId 完成動作的玩家 ID。
 * @property action 實際完成的權威動作。
 * @property tableState 動作及其必要反應處理完成後的權威桌況。
 */
data class CompletedGameActionContext(
    val actorPlayerId: Uuid,
    val action: GameAction,
    val tableState: TableState,
)
