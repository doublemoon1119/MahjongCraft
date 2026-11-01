package com.doublemoon1119.mahjongcraft.platform.minecraft.character

import com.doublemoon1119.mahjongcraft.flow.common.error.ExtensionFailurePolicy
import com.doublemoon1119.mahjongcraft.platform.minecraft.api.character.AiCharacterOwnership
import kotlin.coroutines.cancellation.CancellationException
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.uuid.Uuid

/** 驗證角色所有權、唯一身分、外部重入與 session 釋放，不依賴世界實例。 */
class AiCharacterLeaseCoordinatorTest {
    /** 可隔離失敗的實際記錄。 */
    private val failures = mutableListOf<Throwable>()

    /** 測試使用的主執行緒協調核心。 */
    private val coordinator = AiCharacterLeaseCoordinator<String>(ExtensionFailurePolicy.Default) { _, cause -> failures += cause }

    /** 每份測試都先啟用一個 session。 */
    init {
        coordinator.startSession()
    }

    /** 索引只回傳目前有效的接管；失效與停止都不保留舊身分。 */
    @Test
    fun `entity index rejects invalid and stopped leases`() {
        val key = key()
        val lease = FakeLease()
        assertNull(coordinator.keyFor(lease.entityId))
        coordinator.ensure(key, { lease }) { true }
        assertEquals(key, coordinator.keyFor(lease.entityId))
        lease.valid = false
        assertNull(coordinator.keyFor(lease.entityId))
        val next = FakeLease()
        coordinator.ensure(key, { next }) { true }
        assertEquals(key, coordinator.keyFor(next.entityId))
        coordinator.stopSession()
        assertNull(coordinator.keyFor(next.entityId))
    }

    /** 自有代理只建立一次，停止後恢復並移除。 */
    @Test
    fun `owned character is prepared once and discarded on stop`() {
        val key = key()
        val lease = FakeLease()
        assertEquals("rider", coordinator.ensure(key, { lease }) { true })
        assertEquals("rider", coordinator.ensure(key, { error("Factory must not be called twice") }) { error("Activation must not repeat") })
        coordinator.stopSession()
        assertEquals(1, lease.prepares)
        assertEquals(1, lease.releases)
        assertEquals(1, lease.discards)
        assertTrue(coordinator.keys().isEmpty())
    }

    /** 借用角色只解除接管，不被框架移除。 */
    @Test
    fun `borrowed character is restored but never discarded`() {
        val lease = FakeLease(ownership = AiCharacterOwnership.BORROWED)
        coordinator.ensure(key(), { lease }) { true }
        coordinator.stopSession()
        assertEquals(1, lease.releases)
        assertEquals(0, lease.discards)
    }

    /** 啟用失敗會清除已準備的自有代理，不保留登記。 */
    @Test
    fun `failed seating releases and discards an owned candidate`() {
        val lease = FakeLease()
        assertNull(coordinator.ensure(key(), { lease }) { false })
        assertEquals(1, lease.releases)
        assertEquals(1, lease.discards)
        assertTrue(coordinator.keys().isEmpty())
    }

    /** prepare 部分改動後拋錯也必須恢復，借用角色仍不可刪除。 */
    @Test
    fun `partly prepared borrowed candidate is restored after failure`() {
        val lease = FakeLease(ownership = AiCharacterOwnership.BORROWED, onPrepare = { error("partly changed") })
        assertNull(coordinator.ensure(key(), { lease }) { error("Must not activate") })
        assertEquals(1, lease.releases)
        assertEquals(0, lease.discards)
        assertEquals("partly changed", failures.single().message)
    }

    /** 同一世界 UUID 不可同時被兩名 AI 接管，第二個 handle 不得干預第一個。 */
    @Test
    fun `duplicate entity is rejected before preparing or releasing its second handle`() {
        val first = FakeLease(ownership = AiCharacterOwnership.BORROWED)
        val second = FakeLease(entityId = first.entityId, ownership = AiCharacterOwnership.BORROWED)
        coordinator.ensure(key(), { first }) { true }
        assertNull(coordinator.ensure(key(), { second }) { error("Must not activate duplicate") })
        assertEquals(0, second.prepares)
        assertEquals(0, second.releases)
        assertEquals(0, second.discards)
        assertEquals(0, first.releases)
    }

    /** 工廠內重入同一代理時，第二次要求不能再建立角色。 */
    @Test
    fun `binding is reserved before calling the factory`() {
        val key = key()
        val lease = FakeLease()
        coordinator.ensure(
            key = key,
            acquire = {
                assertNull(coordinator.ensure(key, { error("Reentrant factory must not execute") }) { true })
                lease
            },
            activate = { true },
        )
        assertEquals(1, lease.prepares)
    }

    /** 啟用途中換 session 時，舊角色須清除，不能安裝到新 session。 */
    @Test
    fun `session changed during activation discards the old candidate`() {
        val lease = FakeLease()
        assertNull(
            coordinator.ensure(key(), { lease }) {
                coordinator.startSession()
                true
            },
        )
        assertEquals(1, lease.releases)
        assertEquals(1, lease.discards)
        assertTrue(coordinator.keys().isEmpty())
    }

    /** 舊工廠返回時不能清除新 session 已安裝的同名代理。 */
    @Test
    fun `old acquisition cannot clear the new session binding`() {
        val key = key()
        val old = FakeLease()
        val current = FakeLease()
        assertNull(
            coordinator.ensure(
                key = key,
                acquire = {
                    coordinator.startSession()
                    coordinator.ensure(key, { current }) { true }
                    old
                },
                activate = { error("Old session must not activate") },
            ),
        )
        assertEquals("rider", coordinator.find(key))
        assertEquals(0, current.releases)
        assertEquals(1, old.discards)
    }

    /** 外部恢復失敗仍應刪除自有代理。 */
    @Test
    fun `owned character is discarded even when release throws`() {
        val lease = FakeLease(onRelease = { error("release failed") })
        coordinator.ensure(key(), { lease }) { true }
        coordinator.stopSession()
        assertEquals(1, lease.discards)
        assertEquals("release failed", failures.single().message)
    }

    /** 取消不能被吞掉，但自有代理的清理仍須完成。 */
    @Test
    fun `cancellation propagates after owned cleanup`() {
        val lease = FakeLease(onPrepare = { throw CancellationException("cancelled") })
        assertFailsWith<CancellationException> { coordinator.ensure(key(), { lease }) { true } }
        assertEquals(1, lease.releases)
        assertEquals(1, lease.discards)
        assertTrue(failures.isEmpty())
    }

    /** 解除全部代理時，一個取消不會阻止其他角色清理。 */
    @Test
    fun `stopping releases every character before propagating cancellation`() {
        val first = FakeLease(onRelease = { throw CancellationException("cancelled") })
        val second = FakeLease()
        coordinator.ensure(key(), { first }) { true }
        coordinator.ensure(key(), { second }) { true }
        assertFailsWith<CancellationException> { coordinator.stopSession() }
        assertEquals(1, first.discards)
        assertEquals(1, second.discards)
        assertTrue(coordinator.keys().isEmpty())
    }

    /** 離開場次時釋放角色，換局保留相同場次的代理。 */
    @Test
    fun `retaining a match keeps its actor and removes absent bindings`() {
        val keptKey = key()
        val kept = FakeLease()
        val removed = FakeLease()
        coordinator.ensure(keptKey, { kept }) { true }
        coordinator.ensure(key(), { removed }) { true }
        coordinator.retain(setOf(keptKey))
        assertEquals("rider", coordinator.find(keptKey))
        assertEquals(0, kept.releases)
        assertEquals(1, removed.discards)
        assertEquals(setOf(keptKey), coordinator.keys())
    }

    /** 區塊卸載或角色已移除時不回傳失效代理，借用角色仍由原系統管理。 */
    @Test
    fun `invalid borrowed actor is detached without deletion`() {
        val key = key()
        val lease = FakeLease(ownership = AiCharacterOwnership.BORROWED)
        coordinator.ensure(key, { lease }) { true }
        lease.valid = false
        assertNull(coordinator.find(key))
        assertEquals(1, lease.releases)
        assertEquals(0, lease.discards)
    }

    /** 恢復舊行為尚未完成時，不允許重入接管同一個世界角色。 */
    @Test
    fun `release reserves entity until restoration has completed`() {
        val entityId = Uuid.random()
        val key = key()
        val next = FakeLease(entityId = entityId, ownership = AiCharacterOwnership.BORROWED)
        val old = FakeLease(
            entityId = entityId,
            ownership = AiCharacterOwnership.BORROWED,
            onRelease = { assertNull(coordinator.ensure(key(), { next }) { error("Cannot activate during release") }) },
        )
        coordinator.ensure(key, { old }) { true }
        coordinator.remove(key)
        assertEquals(0, next.prepares)
        assertEquals(0, next.releases)
        assertEquals("rider", coordinator.ensure(key(), { next }) { true })
    }

    /** 停止後不呼叫新工廠。 */
    @Test
    fun `stopped session refuses acquisition`() {
        coordinator.stopSession()
        assertNull(coordinator.ensure(key(), { error("No acquisition after stop") }) { true })
    }

    /**
     * 建立互不重疊的代理身分。
     *
     * @return 隨機桌子、場次及 AI 身分。
     */
    private fun key(): AiCharacterBindingKey = AiCharacterBindingKey(Uuid.random(), Uuid.random(), Uuid.random())

    /**
     * 可觀察外部接管與釋放的假能力。
     *
     * @property entityId 世界角色 UUID。
     * @property ownership 自有或借用。
     * @property onPrepare 準備時的外部回呼。
     * @property onRelease 恢復時的外部回呼。
     */
    private class FakeLease(
        override val entityId: Uuid = Uuid.random(),
        override val ownership: AiCharacterOwnership = AiCharacterOwnership.OWNED,
        private val onPrepare: () -> Unit = {},
        private val onRelease: () -> Unit = {},
    ) : AiCharacterLease<String> {
        override val rider: String = "rider"

        /** 角色是否仍有效。 */
        var valid: Boolean = true

        /** 準備次數。 */
        var prepares: Int = 0

        /** 恢復次數。 */
        var releases: Int = 0

        /** 移除次數。 */
        var discards: Int = 0

        override fun isValid(): Boolean = valid

        override fun prepare(): Boolean {
            prepares++
            onPrepare()
            return true
        }

        override fun release() {
            releases++
            onRelease()
        }

        override fun discardOwned() {
            discards++
        }
    }
}
