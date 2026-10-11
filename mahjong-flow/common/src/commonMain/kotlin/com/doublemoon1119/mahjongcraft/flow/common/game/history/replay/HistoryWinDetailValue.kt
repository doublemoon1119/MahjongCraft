package com.doublemoon1119.mahjongcraft.flow.common.game.history.replay

import com.doublemoon1119.mahjongcraft.flow.common.game.model.WinSettlementDetailEntry
import com.doublemoon1119.mahjongcraft.flow.common.game.model.WinSettlementQuantity

/** 歷史胡牌詳情值；與對局中的胡牌詳情相同，只保存規則算出的語意資料。 */
sealed interface HistoryWinDetailValue {
    /**
     * 有單位的數值。
     *
     * @property quantities 依規則順序排列的數值。
     */
    data class Quantities(val quantities: List<WinSettlementQuantity>) : HistoryWinDetailValue

    /**
     * 依規則順序排列的條目。
     *
     * @property entries 條目列表。
     */
    data class Entries(val entries: List<WinSettlementDetailEntry>) : HistoryWinDetailValue

    /**
     * 局內牌參照集合。
     *
     * @property tiles 局內牌參照列表。
     */
    data class Tiles(val tiles: List<HistoryTileReference>) : HistoryWinDetailValue
}
