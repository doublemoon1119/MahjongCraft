package com.doublemoon1119.mahjongcraft.platform.fabric.development

import com.doublemoon1119.mahjongcraft.flow.common.game.model.WinCelebrationRequest
import kotlin.uuid.Uuid

/** 在和牌演出開始前調整展示請求，例如開發時指定下一次和牌要播的展示。 */
fun interface WinCelebrationRequestAdjuster {
    /**
     * 調整一桌即將開始的和牌演出。
     *
     * @param tableId 演出所屬的牌桌。
     * @param request 目前的演出請求。
     * @return 調整後的請求；不調整時回傳原請求。
     */
    fun adjust(tableId: Uuid, request: WinCelebrationRequest): WinCelebrationRequest
}
