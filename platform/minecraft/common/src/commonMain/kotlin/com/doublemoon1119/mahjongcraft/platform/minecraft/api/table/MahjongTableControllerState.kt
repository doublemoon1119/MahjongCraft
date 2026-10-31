package com.doublemoon1119.mahjongcraft.platform.minecraft.api.table

import com.doublemoon1119.mahjongcraft.platform.minecraft.api.MahjongCraftInternalApi
import kotlin.uuid.Uuid

/**
 * 麻將桌控制點持有的 MahjongCraft 狀態。
 *
 * 除穩定 [tableId] 外，也保存兩條呈現忙碌時間軸：[presentationBusyUntilGameTime] 是目前所有已排定整桌呈現的
 * 最晚結束絕對遊戲時間；[continuingWinPresentationBusyUntilGameTime] 是中途胡牌演出專用的時間軸，不列入整桌
 * 忙碌判定，讓已完成本局的玩家播放演出時，其他仍在本局中的玩家能照常行動。兩條時間軸各自只會延長，因此同一條上的
 * 多筆演出依序播放；查詢時直接與世界時間比較，不需要每 tick 更新。
 *
 * 實作者只建立這份狀態並在存讀檔時轉交給 MahjongCraft；修改與還原由 MahjongCraft 進行。
 *
 * @param markDirty 狀態改變時通知所屬方塊實體需要存檔的動作，通常是方塊實體的 `markDirty`。
 */
class MahjongTableControllerState(
    private val markDirty: () -> Unit,
) {
    /** 同時作為等待階段房間 ID 與開局後牌局桌況 ID 的穩定識別碼。 */
    var tableId: Uuid = Uuid.random()
        private set

    /** 整桌呈現保持忙碌的最晚絕對遊戲時間；`0` 代表沒有額外桌級呈現等待。 */
    var presentationBusyUntilGameTime: Long = 0L
        private set

    /** 中途胡牌呈現保持忙碌的最晚絕對遊戲時間；`0` 代表沒有中途胡牌演出等待。 */
    var continuingWinPresentationBusyUntilGameTime: Long = 0L
        private set

    /**
     * 以 [currentGameTime] 判斷整桌呈現時間軸是否尚未結束。
     *
     * @param currentGameTime 目前絕對遊戲時間。
     * @return 目前時間早於已排定期限時為 `true`。
     */
    fun isPresenting(currentGameTime: Long): Boolean = currentGameTime < presentationBusyUntilGameTime

    /**
     * 以 [currentGameTime] 判斷中途胡牌呈現時間軸是否尚未結束。
     *
     * @param currentGameTime 目前絕對遊戲時間。
     * @return 目前時間早於已排定期限時為 `true`。
     */
    fun isPresentingContinuingWin(currentGameTime: Long): Boolean = currentGameTime < continuingWinPresentationBusyUntilGameTime

    /**
     * 將整桌呈現時間軸延長到 [endGameTime]，已有更晚結束時間時保留原值。
     *
     * @param endGameTime 新的絕對遊戲時間期限。
     */
    @MahjongCraftInternalApi
    fun extendPresentationUntil(endGameTime: Long) {
        if (endGameTime <= presentationBusyUntilGameTime) return
        presentationBusyUntilGameTime = endGameTime
        markDirty()
    }

    /**
     * 將中途胡牌呈現時間軸延長到 [endGameTime]，已有更晚結束時間時保留原值。
     *
     * @param endGameTime 新的絕對遊戲時間期限。
     */
    @MahjongCraftInternalApi
    fun extendContinuingWinPresentationUntil(endGameTime: Long) {
        if (endGameTime <= continuingWinPresentationBusyUntilGameTime) return
        continuingWinPresentationBusyUntilGameTime = endGameTime
        markDirty()
    }

    /**
     * 以存檔內容還原狀態，不通知方塊實體存檔。
     *
     * @param tableId 存檔中的桌子 ID；null 表示存檔沒有可用的 ID，保留目前的 ID。
     * @param presentationBusyUntilGameTime 存檔中的整桌呈現期限。
     * @param continuingWinPresentationBusyUntilGameTime 存檔中的中途胡牌呈現期限。
     */
    @MahjongCraftInternalApi
    fun restore(
        tableId: Uuid?,
        presentationBusyUntilGameTime: Long,
        continuingWinPresentationBusyUntilGameTime: Long,
    ) {
        if (tableId != null) this.tableId = tableId
        this.presentationBusyUntilGameTime = presentationBusyUntilGameTime
        this.continuingWinPresentationBusyUntilGameTime = continuingWinPresentationBusyUntilGameTime
    }
}
