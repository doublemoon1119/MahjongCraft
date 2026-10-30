package com.doublemoon1119.mahjongcraft.flow.common.game.history.replay

import com.doublemoon1119.mahjongcraft.flow.common.game.model.WinSettlementDetailEntry
import com.doublemoon1119.mahjongcraft.flow.common.game.model.WinSettlementQuantity
import com.doublemoon1119.mahjongcraft.logic.base.TileFaceView

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
     * 結算時直接保存的公開牌面，不占用局內牌索引；外觀與特徵是結算當時裁切的結果。
     *
     * @property faces 依規則順序排列的牌面。
     */
    data class TileFaces(val faces: List<TileFaceView>) : HistoryWinDetailValue
}
