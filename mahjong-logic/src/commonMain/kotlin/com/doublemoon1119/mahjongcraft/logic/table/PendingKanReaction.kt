package com.doublemoon1119.mahjongcraft.logic.table

import com.doublemoon1119.mahjongcraft.logic.base.GameAction
import com.doublemoon1119.mahjongcraft.logic.base.IdentifiedTile
import com.doublemoon1119.mahjongcraft.logic.judgment.LegalActionValidator
import kotlin.uuid.Uuid

/**
 * 暗槓/加槓宣告後、等待其他玩家搶槓（榮和）的反應視窗狀態。
 *
 * 跟 [PendingReaction] 的關鍵差異：[robbedTile] 尚未真正套用進 [declarerId] 的副露（副露套用與
 * 補摸嶺上牌都刻意延後到反應視窗解析完畢之後才執行——若提早套用，全員放過時固然不影響結果，
 * 但一旦真的被搶槓，提早套用等於讓寶牌指示牌/副露提前曝光，不符合規則對翻寶牌時機的要求）。
 *
 * 規則無關的通用型別：誰有資格搶槓由呼叫端（如 use case）搭配 [LegalActionValidator] 決定，
 * 這裡只負責追蹤「誰還沒回應」與「大家是否都回應完了」。
 *
 * @property declarerId 宣告暗槓/加槓的玩家 Uuid。
 * @property kanAction 本次宣告的具體動作（含 [GameAction.KanType]，決定反應視窗解析完畢、
 *           全員放過時要套用副露的方式是暗槓還是加槓）。
 * @property robbedTile 被搶的那張牌（暗槓/加槓觸發牌），尚未套用進副露。
 * @property eligiblePlayerIds 有資格搶槓的玩家 Uuid 集合。
 * @property responses 目前已收到的回應（只會是 [GameAction.Ron] 或 [GameAction.Pass]），鍵為玩家 Uuid。
 */
data class PendingKanReaction(
    val declarerId: Uuid,
    val kanAction: GameAction.Kan,
    val robbedTile: IdentifiedTile,
    val eligiblePlayerIds: Set<Uuid>,
    val responses: Map<Uuid, GameAction> = emptyMap(),
) {
    /** 是否所有有資格的玩家都已經回應。 */
    val isComplete: Boolean get() = responses.keys.containsAll(eligiblePlayerIds)
}
