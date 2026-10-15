package com.doublemoon1119.mahjongcraft.platform.fabric.server.event

import com.doublemoon1119.mahjongcraft.flow.api.event.MatchEvent
import com.doublemoon1119.mahjongcraft.platform.fabric.logging.mahjongCraftLogger
import com.doublemoon1119.mahjongcraft.platform.minecraft.event.GameEventDeliveryReporter
import kotlin.uuid.Uuid

/** 將事件交付中的非致命錯誤寫入 Fabric mod logger。 */
internal class FabricGameEventDeliveryReporter : GameEventDeliveryReporter {
    /** 事件交付診斷使用的 mod logger。 */
    private val logger = mahjongCraftLogger(FabricGameEventDeliveryReporter::class)

    /**
     * 記錄事件產生時找不到場地位置的情況。
     *
     * @param matchId 事件所屬場次。
     * @param venueId 缺少位置的場地。
     */
    override fun missingLocation(matchId: Uuid, venueId: Uuid) {
        logger.warn("Game event location was not found for match {} at venue {}", matchId, venueId)
    }

    /**
     * 記錄單一第三方事件監聽者失敗。
     *
     * @param event 交付中的事件。
     * @param listener 失敗的監聽者；以類別名稱辨識它來自哪個擴充。
     * @param cause 監聽者拋出的例外。
     */
    override fun listenerFailed(
        event: MatchEvent,
        listener: Any,
        cause: Throwable,
    ) {
        logger.warn(
            "Game event listener {} failed for event {} in match {} at venue {}",
            listener.javaClass.name,
            event.eventId,
            event.matchId,
            event.venueId,
            cause,
        )
    }

    /**
     * 記錄無法將事件交付工作排入 server 佇列的錯誤。
     *
     * @param cause 排程器拋出的例外。
     */
    override fun schedulingFailed(cause: Throwable) {
        logger.warn("Scheduling MahjongCraft game event delivery failed", cause)
    }
}
