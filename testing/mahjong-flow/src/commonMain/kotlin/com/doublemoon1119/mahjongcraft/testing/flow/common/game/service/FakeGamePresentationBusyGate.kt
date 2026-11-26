package com.doublemoon1119.mahjongcraft.testing.flow.common.game.service

import com.doublemoon1119.mahjongcraft.flow.common.game.service.GamePresentationBusyGate
import kotlin.uuid.Uuid

/**
 * 供測試使用的 [GamePresentationBusyGate] 模擬實作。
 *
 * 預設一律回傳不忙碌，需要驗證「動畫播放期間自動操作鏈路會暫停」的測試可以用 [setBusy] 指定特定
 * 對局為忙碌狀態。
 */
class FakeGamePresentationBusyGate : GamePresentationBusyGate {
    private val busyGameIds = mutableSetOf<Uuid>()
    private val presentingContinuingWinGameIds = mutableSetOf<Uuid>()

    override fun isBusy(gameId: Uuid): Boolean = gameId in busyGameIds

    override fun isPresentingContinuingWin(gameId: Uuid): Boolean = gameId in presentingContinuingWinGameIds

    /** 將 [gameId] 標記為忙碌（[busy] 為 `true`）或不忙碌。 */
    fun setBusy(gameId: Uuid, busy: Boolean) {
        if (busy) busyGameIds += gameId else busyGameIds -= gameId
    }

    /** 將 [gameId] 標記為正在播放中途胡牌演出（[presenting] 為 `true`）或已播完。 */
    fun setPresentingContinuingWin(gameId: Uuid, presenting: Boolean) {
        if (presenting) presentingContinuingWinGameIds += gameId else presentingContinuingWinGameIds -= gameId
    }
}
