package com.doublemoon1119.mahjongcraft.platform.minecraft.mod.loader

import java.util.concurrent.CopyOnWriteArrayList

/**
 * 每一幀整個世界畫完、手上物品與介面還沒畫的時間點。
 *
 * 原版與各載入器都沒有這個時間點的事件，由共用的 `GameRendererMixin` 呼叫 [fire]。各載入器的 [ClientEvents.onWorldRenderFinished]
 * 把監聽器登記到這裡。
 */
object WorldRenderFinishedHook {
    /** 世界畫完時的監聽器。 */
    private val listeners = CopyOnWriteArrayList<() -> Unit>()

    /**
     * 登記世界畫完時的處理。
     *
     * @param listener 畫完時的處理。
     */
    fun addListener(listener: () -> Unit) {
        listeners += listener
    }

    /** 通知世界已畫完。只由 `GameRendererMixin` 在原版畫完整個世界、畫手上物品之前呼叫。 */
    @JvmStatic
    fun fire() {
        listeners.forEach { it() }
    }
}
