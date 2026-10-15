package com.doublemoon1119.mahjongcraft.platform.fabric.client.concurrency

import com.doublemoon1119.mahjongcraft.platform.fabric.client.player.ClientPlayerProfileResolver
import com.doublemoon1119.mahjongcraft.platform.fabric.server.concurrency.FabricAppCoroutineScope
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

/**
 * 與客戶端程序同壽命的背景協程作用域，例如供 [ClientPlayerProfileResolver] 查詢玩家外觀。
 *
 * 不同於 [FabricAppCoroutineScope]，此作用域不隨世界或連線切換重建。
 * 需要提前取消的工作由呼叫端保存並取消其 [kotlinx.coroutines.Job]。
 */
class ClientCoroutineScope : CoroutineScope {
    override val coroutineContext = SupervisorJob() + Dispatchers.IO
}
