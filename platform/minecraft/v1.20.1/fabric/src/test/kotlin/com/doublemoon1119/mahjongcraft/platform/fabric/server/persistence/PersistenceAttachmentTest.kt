package com.doublemoon1119.mahjongcraft.platform.fabric.server.persistence

import com.doublemoon1119.mahjongcraft.platform.minecraft.table.TableLocation
import com.doublemoon1119.mahjongcraft.platform.minecraft.table.TableLocationRegistry
import kotlinx.coroutines.CancellationException
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertSame
import kotlin.test.assertTrue
import kotlin.uuid.Uuid

/** 驗證持久化接線在各個失敗點都清理，且不把空索引寫回原始保存資料。 */
class PersistenceAttachmentTest {
    /** 載入與接線任一步驟失敗，都只清理一次而不公開 attached 狀態。 */
    @Test
    fun `failed attachment never publishes state and cleans each failure point once`() {
        for (failurePoint in 0..3) {
            val expected = IllegalStateException("Injected attachment failure")
            var attached: String? = null
            var cleanupCount = 0
            var listenerInstalled = false
            var memoryLoaded = false
            val thrown = assertFailsWith<IllegalStateException> {
                attached = attachPersistenceState(
                    load = {
                        if (failurePoint == 0) throw expected
                        "saved-state"
                    },
                    initialize = {
                        memoryLoaded = true
                        if (failurePoint == 1) throw expected
                        listenerInstalled = true
                        if (failurePoint == 2) throw expected
                        throw expected
                    },
                    cleanupFailedAttach = {
                        cleanupCount++
                        listenerInstalled = false
                        memoryLoaded = false
                    },
                )
            }
            assertSame(expected, thrown)
            assertEquals(null, attached)
            assertEquals(1, cleanupCount)
            assertFalse(listenerInstalled)
            assertFalse(memoryLoaded)
        }
    }

    /** 取消與致命錯誤都保留原始失敗，且不略過清理。 */
    @Test
    fun `cancellation and fatal errors propagate after cleanup`() {
        val failures = listOf(CancellationException("Cancelled attachment"), AssertionError("Fatal attachment"))
        for (expected in failures) {
            var cleaned = false
            val actual = assertFailsWith<Throwable> {
                attachPersistenceState(
                    load = { "saved-state" },
                    initialize = { throw expected },
                    cleanupFailedAttach = { cleaned = true },
                )
            }
            assertSame(expected, actual)
            assertTrue(cleaned)
        }
    }

    /** 清理本身失敗不得覆蓋原始資料載入錯誤。 */
    @Test
    fun `cleanup failure is suppressed on the original failure`() {
        val expected = IllegalArgumentException("Invalid saved data")
        val cleanup = IllegalStateException("Cleanup failed")
        val actual = assertFailsWith<IllegalArgumentException> {
            attachPersistenceState(
                load = { throw expected },
                initialize = { _: String -> error("Initialization must not run") },
                cleanupFailedAttach = { throw cleanup },
            )
        }
        assertSame(expected, actual)
        assertEquals(listOf(cleanup), actual.suppressedExceptions)
    }

    /** 成功接線不觸發清理，之後的獨立接線仍可載入另一份世界資料。 */
    @Test
    fun `successful attachments retain their state without failure cleanup`() {
        val initialized = mutableListOf<String>()
        val cleanup = { error("Successful attachment must not be cleaned") }
        val first = attachPersistenceState({ "first-world" }, { initialized += it }, cleanup)
        val second = attachPersistenceState({ "second-world" }, { initialized += it }, cleanup)
        assertEquals(listOf(first, second), initialized)
    }

    /** 位置接線在安裝 listener 後失敗，清空記憶體不會把空資料寫回存檔。 */
    @Test
    fun `failed location attachment detaches listener before clearing memory`() {
        val registry = TableLocationRegistry()
        val id = Uuid.random()
        val original = mapOf(id to registry.put(id, TableLocation("minecraft:overworld", 1, 64, 2)))
        var saved = original
        assertFailsWith<IllegalStateException> {
            attachPersistenceState(
                load = { original.values },
                initialize = { entries ->
                    registry.load(entries)
                    registry.setDirtyListener { saved = it }
                    error("Injected failure after installing listener")
                },
                cleanupFailedAttach = {
                    registry.setDirtyListener {}
                    registry.clear()
                },
            )
        }
        assertTrue(registry.snapshot().isEmpty())
        registry.put(Uuid.random(), TableLocation("minecraft:overworld", 8, 64, 2))
        assertEquals(original, saved)
        registry.load(original.values)
        assertEquals(original, registry.snapshot())
    }
}
