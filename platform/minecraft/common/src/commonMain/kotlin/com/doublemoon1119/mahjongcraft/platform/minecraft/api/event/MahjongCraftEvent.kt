package com.doublemoon1119.mahjongcraft.platform.minecraft.api.event

import kotlin.concurrent.atomics.AtomicReference
import kotlin.concurrent.atomics.ExperimentalAtomicApi
import kotlin.jvm.JvmSynthetic

/**
 * 供第三方註冊單一事件類型監聽者的訂閱點。
 *
 * 監聽者只能在第一次伺服器 session 啟動前註冊；啟動時鎖定註冊點，後續 session 不會重新開放註冊。
 * 交付時會依註冊順序呼叫監聽者。
 * 監聽者在來源交易釋放鎖後於伺服器主執行緒執行，必須盡快返回；耗時工作應自行排程。
 * 單次監聽者失敗不影響其他監聽者與後續通知。通知不持久化、不補發，session 結束會捨棄尚未交付的事件。
 *
 * @param L 監聽者介面型別。
 */
@OptIn(ExperimentalAtomicApi::class)
class MahjongCraftEvent<L : Any> private constructor() {
    /** 以不可變註冊狀態保存監聽者與鎖定狀態。 */
    private val state = AtomicReference(RegistrationState<L>(listeners = emptyList(), locked = false))

    /**
     * 註冊監聽者；初始化完成後註冊會被拒絕。
     *
     * @param listener 要依註冊順序接收事件的監聽者。
     */
    fun register(listener: L) {
        requireNotNull(listener) { "Event listener must not be null" }
        while (true) {
            val current = state.load()
            check(!current.locked) { "Event registration is already locked" }
            val next = current.copy(listeners = current.listeners + listener)
            if (state.compareAndSet(current, next)) return
        }
    }

    /** 鎖定註冊；之後不可再加入監聽者。 */
    internal fun lockRegistration() {
        while (true) {
            val current = state.load()
            if (current.locked) return
            if (state.compareAndSet(current, current.copy(locked = true))) return
        }
    }

    /** 取得依註冊順序排列的監聽者快照。 */
    internal fun listenersSnapshot(): List<L> = ListenerSnapshot(state.load().listeners)

    /** MahjongCraft 內部建立訂閱點的入口。 */
    internal companion object {
        /** 建立尚未鎖定的訂閱點；此方法不暴露給 Java 第三方。 */
        @JvmSynthetic
        fun <L : Any> create(): MahjongCraftEvent<L> = MahjongCraftEvent()
    }

    /**
     * 訂閱點的不可變狀態。
     *
     * @property listeners 依註冊順序排列的監聽者。
     * @property locked 是否已禁止後續註冊。
     */
    private data class RegistrationState<L>(
        val listeners: List<L>,
        val locked: Boolean,
    )

    /**
     * 監聽者的不可修改快照，避免交付端暴露可變的底層清單。
     *
     * @param listeners 要複製到快照的監聽者。
     */
    private class ListenerSnapshot<L>(listeners: Collection<L>) : AbstractList<L>() {
        /** 建立快照時複製監聽者。 */
        private val items: List<L> = listeners.toList()

        override val size: Int get() = items.size

        override fun get(index: Int): L = items[index]
    }
}
