package com.doublemoon1119.mahjongcraft.platform.fabric.client.concurrency

import kotlinx.coroutines.CoroutineDispatcher
import net.minecraft.client.MinecraftClient
import org.koin.core.annotation.Single
import kotlin.coroutines.CoroutineContext

/**
 * 把協程排程丟回 Minecraft client 執行緒（render/tick thread）——透過 [MinecraftClient.execute] 把工作
 * 丟進 client 自己的任務佇列。不像伺服器端的
 * [com.doublemoon1119.mahjongcraft.platform.fabric.server.concurrency.ServerThreadCoroutineDispatcher]，
 * client 不會「原地重啟」一個 session，也沒有世界暫停時背景執行緒仍照常計時的問題，所以不需要額外實作
 * [kotlinx.coroutines.Delay]，內建的延遲機制就夠。
 */
@Single
class ClientThreadCoroutineDispatcher : CoroutineDispatcher() {
    override fun isDispatchNeeded(context: CoroutineContext): Boolean = !MinecraftClient.getInstance().isOnThread

    override fun dispatch(context: CoroutineContext, block: Runnable) {
        MinecraftClient.getInstance().execute(block)
    }
}
