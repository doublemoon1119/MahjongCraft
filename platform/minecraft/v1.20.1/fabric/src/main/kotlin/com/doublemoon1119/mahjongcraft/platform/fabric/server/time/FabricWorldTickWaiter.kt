package com.doublemoon1119.mahjongcraft.platform.fabric.server.time

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.suspendCancellableCoroutine
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents
import net.minecraft.server.MinecraftServer
import net.minecraft.server.world.ServerWorld
import kotlin.coroutines.resume

/**
 * 依指定世界的實際遊戲 tick 等待，不把 tick 換算成真實毫秒，也不依賴背景計時器。
 *
 * 每次伺服器 tick 都重新讀取目標世界的 [ServerWorld.time]；伺服器落後、世界暫停或切換時，等待
 * 不會誤提前。伺服器停止時所有等待中的協程會被取消。
 */
class FabricWorldTickWaiter {
    /** 保護一次性事件登記，避免重複加入生命週期回呼。 */
    private val lock = Any()

    /** 尚待指定世界時間到達的等待項目。 */
    private val pending = FabricWorldTickWaitQueue<ServerWorld>()

    /** 是否已登記 tick 與停止回呼。 */
    private var registered = false

    /** 註冊世界 tick 與伺服器停止事件；同一個實例只註冊一次。 */
    fun registerEvents() {
        synchronized(lock) {
            if (registered) return
            registered = true
        }
        ServerTickEvents.END_SERVER_TICK.register(::onEndServerTick)
        ServerLifecycleEvents.SERVER_STOPPING.register(::onServerStopping)
    }

    /**
     * 等待 [world] 的遊戲時間到達 [targetWorldTime]。
     *
     * 呼叫端必須在伺服器主執行緒建立等待；tick 回呼同樣在主執行緒執行，因此不會與登記流程交錯。
     *
     * @param world 要觀察遊戲時間的世界。
     * @param targetWorldTime 要到達的絕對世界 tick。
     */
    suspend fun await(world: ServerWorld, targetWorldTime: Long) {
        check(world.server.isOnThread) { "World tick waits must be registered on the server thread" }
        if (world.time >= targetWorldTime) return
        suspendCancellableCoroutine { continuation ->
            val handle = pending.register(
                key = world,
                targetTick = targetWorldTime,
                onReady = { if (continuation.isActive) continuation.resume(Unit) },
                onCancelled = {
                    if (continuation.isActive) {
                        continuation.cancel(CancellationException("Minecraft server is stopping"))
                    }
                },
            )
            continuation.invokeOnCancellation { handle.dispose() }
        }
    }

    /** 在伺服器主執行緒交付各世界已到期的等待。 */
    private fun onEndServerTick(server: MinecraftServer) {
        server.worlds.forEach { world ->
            pending.pollReady(world, world.time).forEach { complete -> complete() }
        }
    }

    /** 停止指定伺服器各世界的等待，不交付尚未到期的完成回呼。 */
    private fun onServerStopping(server: MinecraftServer) {
        server.worlds.forEach { world ->
            pending.cancel(world).forEach { cancel -> cancel() }
        }
    }
}
