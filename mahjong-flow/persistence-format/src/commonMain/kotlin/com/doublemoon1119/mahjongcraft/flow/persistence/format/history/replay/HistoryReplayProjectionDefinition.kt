package com.doublemoon1119.mahjongcraft.flow.persistence.format.history.replay

import com.doublemoon1119.mahjongcraft.flow.common.game.history.replay.HistoryReplayDiscard
import com.doublemoon1119.mahjongcraft.flow.common.game.history.replay.HistoryReplayFact
import com.doublemoon1119.mahjongcraft.flow.common.game.history.replay.HistoryReplayRuleInformation
import com.doublemoon1119.mahjongcraft.flow.common.game.history.replay.HistoryTileReference

/** 回放投影的登記描述；以不同型別區分必要牌河、事件、動作及規則資訊。 */
sealed interface HistoryReplayProjectionDefinition {
    /** 對應保存資料的種類名稱；不因登記方式改變而重新命名。 */
    val typeKey: String

    /**
     * 將保存的牌河資料轉成回放可讀的捨牌清單；缺少此轉換時不能重建必要牌河。
     *
     * @property typeKey 牌河資料所使用的持久化種類 ID。
     * @property codec 從保存內容取出捨牌與標記的轉換器。
     */
    data class Discard(
        override val typeKey: String,
        val codec: HistoryReplayProjectionCodec<List<HistoryReplayDiscard>>,
    ) : HistoryReplayProjectionDefinition

    /**
     * 將額外保存的語意事實轉成回放事件，不改變既有事實的種類名稱。
     *
     * @property typeKey 已保存事實的種類名稱，可為既有的非命名空間格式 token。
     * @property codec 將事實內容轉為回放讀模型的轉換器。
     */
    data class Fact(
        override val typeKey: String,
        val codec: HistoryReplayProjectionCodec<HistoryReplayFact>,
    ) : HistoryReplayProjectionDefinition

    /**
     * 將擴充動作的保存內容轉成可公開的回放事件。
     *
     * @property typeKey 擴充動作所使用的持久化種類 ID。
     * @property codec 只輸出可公開資訊的動作轉換器。
     */
    data class Action(
        override val typeKey: String,
        val codec: HistoryReplayProjectionCodec<HistoryReplayFact>,
    ) : HistoryReplayProjectionDefinition

    /**
     * 從保存的規則狀態取出可選公開資訊，不要求所有規則都提供。
     *
     * @property typeKey 規則狀態所使用的持久化種類 ID。
     * @property codec 建立回放公開規則資訊的轉換器。
     */
    data class OptionalRule(
        override val typeKey: String,
        val codec: HistoryReplayProjectionCodec<HistoryReplayRuleInformation>,
    ) : HistoryReplayProjectionDefinition

    /**
     * 從玩家規則狀態取出已移出手牌的牌，例如拔北，保留其原始順序。
     *
     * @property typeKey 玩家規則狀態所使用的持久化種類 ID。
     * @property codec 依移出順序回傳牌參照的轉換器。
     */
    data class SetAsideTiles(
        override val typeKey: String,
        val codec: HistoryReplayProjectionCodec<List<HistoryTileReference>>,
    ) : HistoryReplayProjectionDefinition
}
