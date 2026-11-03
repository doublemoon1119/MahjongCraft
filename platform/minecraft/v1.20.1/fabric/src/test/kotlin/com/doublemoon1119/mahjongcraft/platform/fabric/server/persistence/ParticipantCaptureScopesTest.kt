package com.doublemoon1119.mahjongcraft.platform.fabric.server.persistence

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.uuid.Uuid

/** 擷取按房間隔離，取消等待不會釋放別人的工作。 */
@OptIn(ExperimentalCoroutinesApi::class)
class ParticipantCaptureScopesTest {
    /** 同桌不能重入；不同桌不受等待中的發布影響。 */
    @Test
    fun `same room waits while another room can proceed`() = runTest {
        val scopes = ParticipantCaptureScopes()
        val room = Uuid.random()
        val release = CompletableDeferred<Unit>()
        val holder = launch { scopes.withScope(room) { release.await() } }
        runCurrent()
        assertNull(scopes.tryWithScope(room) { "busy" })
        assertEquals("other", scopes.tryWithScope(Uuid.random()) { "other" })
        var entered = false
        val waiting = launch { scopes.withScope(room) { entered = true } }
        runCurrent()
        assertFalse(entered)
        release.complete(Unit)
        holder.join()
        waiting.join()
        assertTrue(entered)
        assertEquals("free", scopes.tryWithScope(room) { "free" })
    }

    /** 等待途中被取消，只清自己的登記，不讓第三份工作越過原持有者。 */
    @Test
    fun `cancelled waiter does not release the active scope`() = runTest {
        val scopes = ParticipantCaptureScopes()
        val room = Uuid.random()
        val release = CompletableDeferred<Unit>()
        val holder = launch { scopes.withScope(room) { release.await() } }
        runCurrent()
        val waiting = launch { scopes.withScope(room) { error("Cancelled work must not run") } }
        runCurrent()
        waiting.cancelAndJoin()
        assertNull(scopes.tryWithScope(room) { "busy" })
        release.complete(Unit)
        holder.join()
        assertEquals("free", scopes.tryWithScope(room) { "free" })
    }
}
