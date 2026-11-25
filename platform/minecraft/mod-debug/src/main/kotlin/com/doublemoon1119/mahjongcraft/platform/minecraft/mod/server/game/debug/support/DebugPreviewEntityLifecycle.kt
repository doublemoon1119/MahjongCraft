package com.doublemoon1119.mahjongcraft.platform.minecraft.mod.server.game.debug.support

import com.doublemoon1119.mahjongcraft.platform.minecraft.mod.loader.ServerEvents
import com.doublemoon1119.mahjongcraft.platform.minecraft.server.game.debug.support.DebugPreviewCleanupQueue
import net.minecraft.server.level.ServerLevel
import net.minecraft.world.entity.Entity

/**
 * 集中保管所有 debug 預覽指令排定的臨時 entity 清除工作。
 *
 * 每個子指令的動畫總時長各不相同，呼叫端各自算好自己那套動畫（含收尾緩衝）實際播完的時刻後呼叫
 * [schedule]。純記憶體佇列，不寫進世界存檔：這些本來就是全新生成、不影響任何真實對局狀態的臨時
 * entity，伺服器重啟後這批清除排程單純消失，頂多留下沒清乾淨的臨時裝飾牌，不影響任何遊戲邏輯。
 *
 * 佇列由本類別獨佔，各 command family 只透過 [schedule] 排入工作，避免 child command 反向依賴 debug
 * root 才能清除自己生成的 entity。
 */
class DebugPreviewEntityLifecycle {
    /** 到期判定與佇列語意見 [DebugPreviewCleanupQueue]。 */
    private val cleanupQueue = DebugPreviewCleanupQueue()

    /**
     * 登記每 tick 一次的清除驅動。
     *
     * 只由 debug root 在註冊指令樹時呼叫一次。各 command family 不自行重複登記 server tick callback。
     *
     * @param events 登記事件用的伺服器事件來源。
     */
    fun registerTicking(events: ServerEvents) {
        events.onServerTickEnd { cleanupQueue.discardExpired() }
    }

    /**
     * 排定 [entities] 在 [endGameTime]（[world] 的絕對 game time）到期時自動清除。
     *
     * @param world 提供絕對 game time 的世界。
     * @param endGameTime 清除工作的到期 game time。
     * @param entities 到期時要清除的 entity。
     */
    fun schedule(
        world: ServerLevel,
        endGameTime: Long,
        entities: List<Entity>,
    ) {
        cleanupQueue.schedule(
            endGameTime = endGameTime,
            currentGameTime = { world.gameTime },
            discard = { entities.forEach(Entity::discard) },
        )
    }
}
