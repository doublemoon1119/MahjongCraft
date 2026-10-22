package com.doublemoon1119.mahjongcraft.logic.module

import com.doublemoon1119.mahjongcraft.logic.base.GameAction
import com.doublemoon1119.mahjongcraft.logic.base.IdentifiedTile
import com.doublemoon1119.mahjongcraft.logic.table.TableState
import kotlin.uuid.Uuid

/**
 * 捨牌反應 policy 的純資料輸入。
 *
 * @property tableState 反應視窗尚未套用任何回應的桌況。
 * @property discarderId 打出來源牌的玩家。
 * @property responderId 正在查詢反應的玩家。
 * @property discardedTile 目前等待反應的實體捨牌。
 * @property legalActions 規則合法動作判定器對此玩家回傳的動作。
 */
data class DiscardReactionPolicyContext(
    val tableState: TableState,
    val discarderId: Uuid,
    val responderId: Uuid,
    val discardedTile: IdentifiedTile,
    val legalActions: List<GameAction>,
)
