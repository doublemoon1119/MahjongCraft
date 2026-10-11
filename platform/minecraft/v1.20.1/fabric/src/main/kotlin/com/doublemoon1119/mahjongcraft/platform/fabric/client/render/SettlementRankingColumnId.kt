package com.doublemoon1119.mahjongcraft.platform.fabric.client.render

/** 結算排名列可出現的欄位。 */
enum class SettlementRankingColumnId {
    /** 名次，靠右對齊。 */
    RANK,

    /** 玩家頭像。 */
    FACE,

    /** 玩家名稱，靠左對齊。 */
    NAME,

    /** 結算狀態文字，置中對齊。 */
    STATUS,

    /** 分數，靠右對齊。 */
    SCORE,

    /** 分數增減，靠右對齊。 */
    DELTA,

    /** 付款原因文字，靠左對齊。 */
    PAYMENT_REASON,
}
