package com.doublemoon1119.mahjongcraft.flow.common.game.model.taiwan

import com.doublemoon1119.mahjongcraft.flow.common.game.model.ExtensionRoundOutcomeDetail
import com.doublemoon1119.mahjongcraft.logic.base.NamespacedId

/**
 * 特殊花牌和牌提供給結算呈現的公開詳情。
 *
 * @property taiTypeId 成立的台種識別碼。
 * @property tai 台數。
 */
data class TaiwanSpecialFlowerWinDetail(
    val taiTypeId: String,
    val tai: Int,
) : ExtensionRoundOutcomeDetail {
    init {
        NamespacedId.requireValid(taiTypeId) { "Taiwan tai type ID must be namespaced: $taiTypeId" }
        require(tai > 0) { "Taiwan special flower win tai must be positive" }
    }
}
