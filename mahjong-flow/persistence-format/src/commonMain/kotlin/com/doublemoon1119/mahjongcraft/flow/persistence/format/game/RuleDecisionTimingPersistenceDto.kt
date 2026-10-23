package com.doublemoon1119.mahjongcraft.flow.persistence.format.game

import kotlinx.serialization.Serializable

/** 規則選擇視窗開啟時間點的持久化表示，依名稱保存。 */
@Serializable
enum class RuleDecisionTimingPersistenceDto {
    /** 發牌後、一般摸打前。 */
    ROUND_START,

    /** 一般流局結算前。 */
    BEFORE_EXHAUSTIVE_DRAW,

    /** 和牌後本局繼續時。 */
    WIN_CONTINUES,

    /** 本局結算完成、開下一局或判定終局前。 */
    ROUND_END,
}
