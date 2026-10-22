package com.doublemoon1119.mahjongcraft.logic.module

import com.doublemoon1119.mahjongcraft.logic.base.GameAction
import com.doublemoon1119.mahjongcraft.logic.table.TableState
import kotlin.uuid.Uuid

/**
 * 動作即時計分 policy 的完整輸入。
 *
 * @property tableStateBeforeAction 動作套用前的權威桌況。
 * @property tableStateAfterAction 已套用動作本身、尚未補牌的候選桌況。
 * @property actorPlayerId 執行動作的玩家。
 * @property action 已確定成立的動作，例如吃、碰、槓或移出手牌。
 * @property sourcePlayerId 鳴取他家捨牌時被鳴牌的玩家；不是鳴取捨牌的動作為 null。
 */
data class ActionScoringContext(
    val tableStateBeforeAction: TableState,
    val tableStateAfterAction: TableState,
    val actorPlayerId: Uuid,
    val action: GameAction,
    val sourcePlayerId: Uuid?,
)
