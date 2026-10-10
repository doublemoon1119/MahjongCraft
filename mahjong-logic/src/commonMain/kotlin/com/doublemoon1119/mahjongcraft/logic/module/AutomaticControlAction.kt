package com.doublemoon1119.mahjongcraft.logic.module

import com.doublemoon1119.mahjongcraft.logic.base.GameAction
import kotlin.uuid.Uuid

/**
 * 規則要求立即採用的一個完整動作。
 *
 * @property action 要提交的動作；一般特殊動作必須存在於目前合法動作中，規則允許的普通捨牌除外。
 * @property selectedTileIds 擴充動作需要額外選牌時使用的牌 ID；不需選牌時為空。
 */
data class AutomaticControlAction(
    val action: GameAction,
    val selectedTileIds: List<Uuid> = emptyList(),
)
