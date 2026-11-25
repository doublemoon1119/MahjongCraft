package com.doublemoon1119.mahjongcraft.platform.minecraft.mod.client.history

import com.doublemoon1119.mahjongcraft.flow.network.dto.message.HistoryRoundStateDto

/** 結算卡片畫出和牌手牌所需的那一筆交易後牌面。 */
internal sealed interface HistoryWinningHandSource {
    /**
     * 牌面已經取得。
     *
     * @property state 那一筆交易後的牌面。
     */
    data class Ready(val state: HistoryRoundStateDto) : HistoryWinningHandSource

    /** 正在讀取牌面。 */
    data object Loading : HistoryWinningHandSource

    /**
     * 讀取牌面失敗。
     *
     * @property reason 失敗原因。
     */
    data class Failed(val reason: HistoryBrowseFailure) : HistoryWinningHandSource

    /** 還沒讀取，畫出讀取按鈕。 */
    data object NotLoaded : HistoryWinningHandSource
}
