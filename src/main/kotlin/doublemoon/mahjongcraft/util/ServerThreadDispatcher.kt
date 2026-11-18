package doublemoon.mahjongcraft.util

import kotlinx.coroutines.CoroutineDispatcher
import net.minecraft.server.MinecraftServer
import net.minecraft.server.ServerTask
import kotlin.coroutines.CoroutineContext

/**
 * Runs every coroutine continuation through [enqueue], which must hand the block to a single owning thread.
 *
 * It always enqueues, even when called from the owning thread, so `launch` never runs the new
 * coroutine inline inside its caller.
 */
class QueueingDispatcher(private val enqueue: (Runnable) -> Unit) : CoroutineDispatcher() {
    override fun isDispatchNeeded(context: CoroutineContext): Boolean = true
    override fun dispatch(context: CoroutineContext, block: Runnable) = enqueue(block)
}

/**
 * A dispatcher that resumes coroutines on this server's main thread.
 *
 * Game logic touches entities and the world, which must only happen on the server thread.
 * `MinecraftServer.execute` would run the block inline when already on the server thread,
 * so this queues a [ServerTask] instead.
 */
fun MinecraftServer.coroutineDispatcher(): CoroutineDispatcher =
    QueueingDispatcher { block -> send(ServerTask(ticks, block)) }
