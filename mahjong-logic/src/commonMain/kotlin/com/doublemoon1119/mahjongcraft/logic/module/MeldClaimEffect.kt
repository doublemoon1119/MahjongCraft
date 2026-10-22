package com.doublemoon1119.mahjongcraft.logic.module

import com.doublemoon1119.mahjongcraft.logic.base.MeldType
import kotlin.uuid.Uuid

/**
 * 規則判定成立後，Flow 套用一個鳴牌副露所需的純資料效果。
 *
 * 來源捨牌由反應情境提供；這裡只列出玩家要交出的實體牌，以及成立後哪些牌可公開或需要蓋牌。
 * Flow 會再次檢查所有 ID 確實屬於正確玩家與目前牌河，不能把這個資料當成已驗證的授權。
 *
 * @property meldType 副露種類，包含規則提供的 [MeldType.Extension]。
 * @property handTileIds 玩家手中要交出的實體牌 ID，不包含來源捨牌。
 * @property publiclyRevealedTileIds 成立後可向其他玩家公開牌面的牌 ID。
 * @property faceDownTileIds 成立後在桌面上朝下的牌 ID；它與牌面資訊公開集合分開表示。
 * @property requiresSupplementalDraw 成立後是否要交給規則的補牌 policy 取得後續牌。
 */
data class MeldClaimEffect(
    val meldType: MeldType,
    val handTileIds: List<Uuid>,
    val publiclyRevealedTileIds: Set<Uuid>,
    val faceDownTileIds: Set<Uuid>,
    val requiresSupplementalDraw: Boolean,
) {
    init {
        require(handTileIds.size == handTileIds.toSet().size) { "Meld claim hand tile IDs must be unique" }
    }
}
