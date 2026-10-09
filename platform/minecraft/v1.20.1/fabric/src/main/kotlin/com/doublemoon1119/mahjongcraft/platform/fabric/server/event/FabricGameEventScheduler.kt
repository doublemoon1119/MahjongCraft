package com.doublemoon1119.mahjongcraft.platform.fabric.server.event

import com.doublemoon1119.mahjongcraft.platform.minecraft.event.GameEventScheduler
import net.minecraft.server.MinecraftServer
import net.minecraft.server.ServerTask

/**
 * 將遊戲事件交付工作排入指定 Fabric server 的主執行緒佇列。
 * @property server 接收工作且執行交付的伺服器。
 */
internal class FabricGameEventScheduler(
    private val server: MinecraftServer,
) : GameEventScheduler {
    /**
     * 一律建立新的 Minecraft 任務再送入佇列，避免在目前已是主執行緒時同步執行監聽者。
     *
     * @param task 要稍後在此 server 主執行緒執行的工作。
     */
    override fun enqueue(task: () -> Unit) {
        server.send(ServerTask(server.ticks, Runnable(task)))
    }
}
