package com.doublemoon1119.mahjongcraft.logic.module

import com.doublemoon1119.mahjongcraft.logic.base.GameAction
import kotlin.uuid.Uuid

/**
 * 一個可提交的捨牌反應及其成立效果。
 *
 * @property action 要提交的完整動作值，包含動作本身的選牌 ID。
 * @property priority 數值越大越優先；同分時由 Flow 依規則的座位順序裁定。
 * @property tieBreakPriority 同一優先級內的規則裁定值，數值越大越優先。
 * @property selectedTileIds 動作以外由玩家選出的實體牌 ID。
 * @property claimEffect 鳴取捨牌時的效果；Pass 與 Ron 不需要副露效果。
 */
data class DiscardReactionCandidate(
    val action: GameAction,
    val priority: Int,
    val tieBreakPriority: Int = 0,
    val selectedTileIds: List<Uuid> = emptyList(),
    val claimEffect: MeldClaimEffect? = null,
)
