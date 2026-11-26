package com.doublemoon1119.mahjongcraft.flow.persistence.format.rule

import com.doublemoon1119.mahjongcraft.logic.rules.taiwan.TaiwanExhaustiveDrawReason
import kotlinx.serialization.Serializable

/** [TaiwanExhaustiveDrawReason] 種類的 persistence 值。 */
@Serializable
enum class TaiwanExhaustiveDrawReasonPersistenceValue {
    /** 牌牆摸盡且無人和牌。 */
    NORMAL,

    /** 公開花牌後沒有合法補牌可取。 */
    NO_SUPPLEMENTAL_TILE,

    /** 全桌合計完成四個槓。 */
    FOUR_KANS,

    /** 第一巡四位玩家依序打出相同風牌。 */
    FOUR_WINDS,
}
