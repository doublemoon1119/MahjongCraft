package com.doublemoon1119.mahjongcraft.platform.minecraft.mod.client.concurrency

import com.doublemoon1119.mahjongcraft.platform.minecraft.mod.server.concurrency.ServerThreadCoroutineDispatcher
import kotlinx.coroutines.CoroutineDispatcher
import net.minecraft.client.Minecraft
import kotlin.coroutines.CoroutineContext

/**
 * 透過 [Minecraft.execute] 將協程工作送回客戶端的繪製／tick 執行緒。
 *
 * 已在該執行緒時不另行排程；不同於 [ServerThreadCoroutineDispatcher]，此處不實作以伺服器 tick 計時的延遲。
 */
class ClientThreadCoroutineDispatcher : CoroutineDispatcher() {
    override fun isDispatchNeeded(context: CoroutineContext): Boolean = !Minecraft.getInstance().isSameThread

    override fun dispatch(context: CoroutineContext, block: Runnable) {
        Minecraft.getInstance().execute(block)
    }
}
