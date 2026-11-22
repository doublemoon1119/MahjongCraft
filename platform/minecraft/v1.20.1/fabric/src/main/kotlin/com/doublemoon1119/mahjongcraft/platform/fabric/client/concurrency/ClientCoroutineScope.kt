package com.doublemoon1119.mahjongcraft.platform.fabric.client.concurrency

import com.doublemoon1119.mahjongcraft.platform.fabric.server.concurrency.ServerLifecycleAppCoroutineScope
import com.doublemoon1119.mahjongcraft.platform.minecraft.mod.client.player.ClientPlayerProfileResolver
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

/**
 * 與客戶端程序同壽命的背景協程作用域，例如供 [ClientPlayerProfileResolver] 查詢玩家外觀。
 *
 * 不同於 [ServerLifecycleAppCoroutineScope]，此作用域不隨世界或連線切換重建。
 * 需要提前取消的工作由呼叫端保存並取消其 [kotlinx.coroutines.Job]。
 */
class ClientCoroutineScope : CoroutineScope {
    override val coroutineContext = SupervisorJob() + Dispatchers.IO
}
