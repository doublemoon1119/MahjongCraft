package com.doublemoon1119.mahjongcraft.platform.fabric.server.event

import com.doublemoon1119.mahjongcraft.flow.api.event.MatchEvent
import com.doublemoon1119.mahjongcraft.platform.fabric.logging.mahjongCraftLogger
import com.doublemoon1119.mahjongcraft.platform.minecraft.event.GameEventDeliveryReporter
import org.koin.core.annotation.Single
import kotlin.uuid.Uuid

/** 將事件交付中的非致命錯誤寫入 Fabric mod logger。 */
@Single(binds = [GameEventDeliveryReporter::class])
internal class FabricGameEventDeliveryReporter : GameEventDeliveryReporter {
    /** 事件交付診斷使用的 mod logger。 */
    private val logger = mahjongCraftLogger(FabricGameEventDeliveryReporter::class)

    /**
     * 記錄事件產生時找不到場地位置的情況。
     * @param matchId 事件所屬場次。
     * @param venueId 缺少位置的場地。
     */
    override fun missingLocation(matchId: Uuid, venueId: Uuid) {
        logger.warn("Game event location was not found for match {} at venue {}", matchId, venueId)
    }

    /**
     * 記錄單一第三方事件監聽者失敗。
     * @param event 交付中的事件。
     * @param cause 監聽者拋出的例外。
     */
    override fun listenerFailed(event: MatchEvent, cause: Throwable) {
        logger.warn(
            "Game event listener failed for event {} in match {} at venue {}",
            event.eventId,
            event.matchId,
            event.venueId,
            cause,
        )
    }

    /**
     * 記錄無法將事件交付工作排入 server 佇列的錯誤。
     * @param cause 排程器拋出的例外。
     */
    override fun schedulingFailed(cause: Throwable) {
        logger.warn("Scheduling MahjongCraft game event delivery failed", cause)
    }
}
