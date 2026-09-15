package com.doublemoon1119.mahjongcraft.platform.fabric.server.game.debug.support

import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents
import net.minecraft.entity.Entity
import net.minecraft.server.world.ServerWorld
import org.koin.core.annotation.Single

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
@Single
class DebugPreviewEntityLifecycle {
    /** 到期判定與佇列語意見 [DebugPreviewCleanupQueue]。 */
    private val cleanupQueue = DebugPreviewCleanupQueue()

    /**
     * 向 Fabric 登記每 tick 一次的清除驅動。
     *
     * 只由 debug root 在註冊指令樹時呼叫一次；各 command family 不自行重複登記 server tick callback。
     */
    fun registerTicking() {
        ServerTickEvents.END_SERVER_TICK.register { cleanupQueue.discardExpired() }
    }

    /**
     * 排定 [entities] 在 [endGameTime]（[world] 的絕對 game time）到期時自動清除。
     */
    fun schedule(
        world: ServerWorld,
        endGameTime: Long,
        entities: List<Entity>,
    ) {
        cleanupQueue.schedule(
            endGameTime = endGameTime,
            currentGameTime = { world.time },
            discard = { entities.forEach(Entity::discard) },
        )
    }
}
