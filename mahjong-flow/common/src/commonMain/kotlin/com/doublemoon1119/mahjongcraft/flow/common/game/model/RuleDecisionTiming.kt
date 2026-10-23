package com.doublemoon1119.mahjongcraft.flow.common.game.model

/**
 * 規則選擇視窗開啟的時間點，同時決定選完後牌局從哪裡接續。
 *
 * 這些是 Flow 可以暫停再接續的固定位置，不代表選擇的種類；同一個時間點可以有不同規則的各種選擇。
 */
enum class RuleDecisionTiming {
    /** 發牌後、一般摸打前；選完後開始一般摸打。 */
    ROUND_START,

    /** 牌山摸完、沒有其他特殊結果成立，一般流局結算前；選完後進行一般流局結算。 */
    BEFORE_EXHAUSTIVE_DRAW,

    /** 和牌後規則判定本局繼續時；選完後回到一般摸打。 */
    WIN_CONTINUES,

    /** 本局結算完成、開下一局或判定終局前；選完後才開下一局或判定終局。 */
    ROUND_END,
}
