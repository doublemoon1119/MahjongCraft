package com.doublemoon1119.mahjongcraft.flow.common.game.model.taiwan

import com.doublemoon1119.mahjongcraft.flow.common.game.model.ExtensionRoundOutcomeDetail
import com.doublemoon1119.mahjongcraft.logic.base.NamespacedId
import kotlin.uuid.Uuid

/**
 * 特殊花牌和牌提供給結算呈現的公開詳情。
 *
 * @property taiTypeId 成立的台種識別碼。
 * @property tai 台數。
 * @property winningFlowerId 讓和牌成立的花牌：八仙過海是自己剛公開的花，七搶一是被搶的花。
 */
data class TaiwanSpecialFlowerWinDetail(
    val taiTypeId: String,
    val tai: Int,
    val winningFlowerId: Uuid,
) : ExtensionRoundOutcomeDetail {
    init {
        NamespacedId.requireValid(taiTypeId) { "Taiwan tai type ID must be namespaced: $taiTypeId" }
        require(tai > 0) { "Taiwan special flower win tai must be positive" }
    }
}
