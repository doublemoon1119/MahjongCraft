package com.doublemoon1119.mahjongcraft.platform.fabric.server.concurrency

import com.doublemoon1119.mahjongcraft.platform.fabric.server.FabricServerHolder
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlin.coroutines.CoroutineContext

/**
 * 把協程排程丟回 Minecraft 伺服器主執行緒（tick thread）——透過
 * [net.minecraft.server.MinecraftServer.execute] 把工作丟進伺服器自己的任務佇列，在下個 tick 開始
 * 前執行，不是另開執行緒。伺服器尚未啟動（[FabricServerHolder] 還沒被設值）時退回
 * [Dispatchers.Default]，避免任務憑空消失；正常運作下走不到這個分支——真的走到代表伺服器還沒啟動
 * 就有協程想切到主執行緒，屬於呼叫端的邏輯錯誤。
 */
class ServerThreadCoroutineDispatcher(private val serverHolder: FabricServerHolder) : CoroutineDispatcher() {
    override fun dispatch(context: CoroutineContext, block: Runnable) {
        val server = serverHolder.current()
        if (server != null) {
            server.execute(block)
        } else {
            Dispatchers.Default.dispatch(context, block)
        }
    }
}
