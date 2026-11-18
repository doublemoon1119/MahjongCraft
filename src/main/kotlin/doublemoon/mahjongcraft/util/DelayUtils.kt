package doublemoon.mahjongcraft.util

import doublemoon.mahjongcraft.scheduler.client.ClientScheduler
import doublemoon.mahjongcraft.scheduler.server.ServerScheduler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume
import net.fabricmc.api.EnvType
import net.fabricmc.api.Environment

/**
 * 用來在 [CoroutineScope] 之中,對 伺服器主線程 上使用 [delay]
 */
suspend fun delayOnServer(timeMills: Long) = suspendCancellableCoroutine { continuation ->
    val action = ServerScheduler.scheduleDelayAction(delay = timeMills) { continuation.resume(Unit) }
    continuation.invokeOnCancellation { action.stop = true }
}

/**
 * 用來在 [CoroutineScope] 之中,對 客戶端主線程 上使用 [delay]
 */
@Environment(EnvType.CLIENT)
suspend fun delayOnClient(timeMills: Long) {
    var completed = false
    ClientScheduler.scheduleDelayAction(delay = timeMills) { completed = true }
    while (!completed) delay(10)
}