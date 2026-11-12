package com.doublemoon1119.mahjongcraft.flow.server.game.usecase

import com.doublemoon1119.mahjongcraft.flow.common.game.history.HistoryEventDraft
import com.doublemoon1119.mahjongcraft.flow.common.game.history.HistoryFact
import com.doublemoon1119.mahjongcraft.flow.common.game.model.RuleDecisionTiming
import com.doublemoon1119.mahjongcraft.flow.server.game.orchestration.RuleDecisionResolution

/**
 * 規則選擇步驟中每一輪公開換牌的牌譜事件，依輪次排列；換牌由規則流程處理，事件不歸屬任何玩家的操作。
 *
 * @param timing 規則選擇視窗開啟的時間點。
 * @return 每一輪各一筆事件；沒有換牌時為空清單。
 */
internal fun RuleDecisionResolution.replacementHistoryDrafts(timing: RuleDecisionTiming): List<HistoryEventDraft> = replacementRounds.map { round ->
    HistoryEventDraft(actorPlayerId = null, fact = HistoryFact.TileReplacementRoundResolved(timing, round))
}
