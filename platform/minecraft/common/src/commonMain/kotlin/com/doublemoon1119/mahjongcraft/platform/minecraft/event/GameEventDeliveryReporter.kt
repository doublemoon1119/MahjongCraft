package com.doublemoon1119.mahjongcraft.platform.minecraft.event

import com.doublemoon1119.mahjongcraft.flow.api.event.MatchEvent
import kotlin.uuid.Uuid

/** 報告事件交付中不應影響對局的診斷狀態。 */
interface GameEventDeliveryReporter {
    /**
     * 報告事件產生時找不到對應桌子位置。
     *
     * @param matchId 找不到位置的對局 UUID。
     * @param venueId 找不到位置的場地 UUID。
     */
    fun missingLocation(matchId: Uuid, venueId: Uuid)

    /**
     * 報告單一監聽者失敗；其他監聽者與後續事件仍會繼續。
     *
     * @param event 交付失敗時正在處理的事件。
     * @param cause 監聽者丟出的例外。
     */
    fun listenerFailed(event: MatchEvent, cause: Throwable)

    /**
     * 報告事件交付工作無法排入平台佇列。
     *
     * @param cause 排程器丟出的例外。
     */
    fun schedulingFailed(cause: Throwable)
}
