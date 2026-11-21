package com.doublemoon1119.mahjongcraft.platform.fabric.server.game.debug.presentation

import com.doublemoon1119.mahjongcraft.flow.common.game.model.ContinuingWinSettlementDetail

/**
 * `/mahjongcraft debug continuing_win <mode>` 目前選定的中途胡牌模式。
 *
 * @property settlementDetail 中途胡牌時採用的結算面板模式；`null` 代表功能關閉。
 */
enum class DebugWinRoundContinuationMode(val settlementDetail: ContinuingWinSettlementDetail?) {
    /** 關閉開發覆寫，交由正式規則決定和牌後是否結束本局。 */
    OFF(settlementDetail = null),

    /** 中途胡牌，胡牌演出照常，接完整結算面板。 */
    FULL(settlementDetail = ContinuingWinSettlementDetail.WINNER_DETAILS),

    /** 中途胡牌，胡牌演出照常，接精簡結算面板（只有贏家、放銃者與分數）。 */
    BRIEF(settlementDetail = ContinuingWinSettlementDetail.SCORE_CHANGES_ONLY),
}
