package com.doublemoon1119.mahjongcraft.platform.minecraft.mod.loader

/**
 * 等待載入器依登記表分批寫入的項目，以及全部寫入後要執行的工作。
 *
 * 某個登記表取出項目後就視為已經登記，之後不能再加入該登記表的項目。
 *
 * @param K 識別登記表的型別。
 * @param I 識別項目的型別。
 */
internal class RegistrationQueue<K : Any, I : Any> {
    /** 尚未寫入的項目，依加入順序排列。 */
    private val pending = mutableListOf<PendingRegistration<K, I>>()

    /** 已經取出過項目的登記表。 */
    private val completedRegistries = mutableSetOf<K>()

    /** 所有項目寫入後要執行的工作。 */
    private val afterRegistrationActions = mutableListOf<() -> Unit>()

    /**
     * 加入一個項目。
     *
     * @param registry 要寫入的登記表。
     * @param id 項目在登記表中的 ID。
     * @param create 建立要寫入的物件。
     * @throws IllegalStateException 該登記表已經登記過。
     */
    fun add(registry: K, id: I, create: () -> Any) = synchronized(this) {
        check(registry !in completedRegistries) { "Registry $registry is already registered; $id was submitted too late" }
        pending += PendingRegistration(registry = registry, id = id, create = create)
    }

    /**
     * 取出某個登記表的項目，並記錄該登記表已經登記。
     *
     * @param registry 正在登記的登記表。
     * @return 依加入順序排列的項目。
     */
    fun take(registry: K): List<PendingRegistration<K, I>> = synchronized(this) {
        completedRegistries += registry
        pending.filter { it.registry == registry }.also(pending::removeAll)
    }

    /**
     * 加入全部項目寫入後要執行的工作。
     *
     * @param action 要執行的工作。
     */
    fun afterRegistration(action: () -> Unit) = synchronized(this) {
        afterRegistrationActions += action
    }

    /** 依加入順序執行全部項目寫入後的工作。每個工作只執行一次。 */
    fun runAfterRegistration() {
        val actions = synchronized(this) { afterRegistrationActions.toList().also { afterRegistrationActions.clear() } }
        actions.forEach { it() }
    }
}

/**
 * 等待寫入的一個項目。
 *
 * @param K 識別登記表的型別。
 * @param I 識別項目的型別。
 * @property registry 要寫入的登記表。
 * @property id 項目在登記表中的 ID。
 * @property create 建立要寫入的物件。
 */
internal class PendingRegistration<K : Any, I : Any>(
    val registry: K,
    val id: I,
    val create: () -> Any,
)
