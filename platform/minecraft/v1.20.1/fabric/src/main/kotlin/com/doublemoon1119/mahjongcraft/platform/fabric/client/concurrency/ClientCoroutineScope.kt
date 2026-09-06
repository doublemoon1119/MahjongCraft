package com.doublemoon1119.mahjongcraft.platform.fabric.client.concurrency

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import org.koin.core.annotation.Single

/**
 * Client-only 長期背景協程作用域，生命週期等於整個 client 程序；用於不需要跟著世界或連線 session
 * 重啟的背景工作（例如
 * [com.doublemoon1119.mahjongcraft.platform.fabric.client.player.ClientPlayerProfileResolver] 的
 * Mojang 查詢）。跟伺服器端的
 * [com.doublemoon1119.mahjongcraft.platform.fabric.server.concurrency.FabricAppCoroutineScope] 不同，
 * 這裡不提供 session 重啟或優雅關閉語意——client 沒有「原地重啟」的需求；真的需要取消特定任務時，
 * 應該由呼叫端自己管理各自啟動的 [kotlinx.coroutines.Job]。
 */
@Single
class ClientCoroutineScope : CoroutineScope {
    override val coroutineContext = SupervisorJob() + Dispatchers.IO
}
