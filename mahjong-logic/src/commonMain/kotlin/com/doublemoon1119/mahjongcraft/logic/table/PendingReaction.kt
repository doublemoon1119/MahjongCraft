package com.doublemoon1119.mahjongcraft.logic.table

import com.doublemoon1119.mahjongcraft.logic.base.GameAction
import kotlin.uuid.Uuid

/**
 * 捨牌後、等待其他玩家回應（吃／碰／槓／榮和／過）的反應視窗狀態。
 *
 * 規則無關的通用型別：只追蹤誰可以回應、誰可以榮和、已收到的回應，以及大家是否都回應完了；
 * 回應是否合法與優先順序由規則的反應 policy 決定。
 *
 * @property discarderId 本次捨牌的玩家 Uuid。
 * @property tileId 本次被捨棄、等待被回應的牌的唯一識別碼。
 * @property eligiblePlayerIds 有資格對本次捨牌做出回應的玩家 Uuid 集合。
 * @property ronPlayerIds 這個視窗允許榮和的玩家；依多家和規則開窗時決定，必須是 [eligiblePlayerIds] 的子集合。
 * 其他玩家即使規則上能和，也只能選擇鳴牌或過。
 * @property id 本次視窗的識別碼；建立後連同回應保存，不在查詢選項時重新產生。
 * @property responses 目前已收到的回應，鍵為玩家 Uuid。
 * @property selectedTileIds 各玩家提交的實體選牌，與動作本身分開保存；鍵必須是已回應的玩家。
 */
data class PendingReaction(
    val discarderId: Uuid,
    val tileId: Uuid,
    val eligiblePlayerIds: Set<Uuid>,
    val ronPlayerIds: Set<Uuid>,
    val id: Uuid,
    val responses: Map<Uuid, GameAction> = emptyMap(),
    val selectedTileIds: Map<Uuid, List<Uuid>> = emptyMap(),
) {
    init {
        require(eligiblePlayerIds.containsAll(ronPlayerIds)) { "Ron players must be eligible to respond" }
        require(responses.keys.containsAll(selectedTileIds.keys)) { "Selected tiles must belong to a submitted response" }
    }

    /** 是否所有有資格的玩家都已經回應。 */
    val isComplete: Boolean get() = responses.keys.containsAll(eligiblePlayerIds)
}
