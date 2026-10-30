package com.doublemoon1119.mahjongcraft.logic.component

/**
 * 不可變的組件集合，透過組件定義讀寫具型別的值。
 *
 * 存入與讀出時皆建立獨立值。相同 ID 必須使用同一個已登記定義，不允許以另一個轉型或複製函式冒充。
 *
 * @property entries 依識別碼索引的內部組件值。
 */
class ComponentSet private constructor(
    private val entries: Map<String, Entry<*>>,
) {
    /** 此集合使用的組件定義，依識別碼穩定排序；回傳獨立清單。 */
    val types: List<ComponentType<*>> get() = entries.values.map { it.type }.sortedBy { it.id }

    /** 是否沒有任何組件。 */
    val isEmpty: Boolean get() = entries.isEmpty()

    /**
     * 取得指定組件的獨立值。
     *
     * @param T 組件值型別。
     * @param type 已登記的組件定義。
     * @return 組件值；沒有設定時為 null。
     */
    @Suppress("UNCHECKED_CAST")
    operator fun <T : Any> get(type: ComponentType<T>): T? {
        val entry = entries[type.id] ?: return null
        require(entry.type === type) { "Conflicting component definition: ${type.id}" }
        return (entry as Entry<T>).snapshot()
    }

    /**
     * 設定一個組件，不修改目前集合。
     *
     * @param T 組件值型別。
     * @param type 已登記的組件定義。
     * @param value 要設定的值。
     * @return 包含獨立組件值的新集合。
     */
    fun <T : Any> with(type: ComponentType<T>, value: T): ComponentSet {
        require(entries[type.id]?.type?.let { it === type } != false) { "Conflicting component definition: ${type.id}" }
        return ComponentSet(entries + (type.id to Entry(type, type.snapshot(value))))
    }

    /**
     * 移除指定組件，不修改目前集合。
     *
     * @param type 已登記的組件定義。
     * @return 不含該組件的新集合。
     */
    fun without(type: ComponentType<*>): ComponentSet {
        require(entries[type.id]?.type?.let { it === type } != false) { "Conflicting component definition: ${type.id}" }
        return if (type.id in entries) ComponentSet(entries - type.id) else this
    }

    /**
     * 按組件定義合併高層資料；未宣告合併方式的衝突會被拒絕。
     *
     * @param higher 較高層的不可變集合。
     * @return 合併後的獨立集合。
     */
    fun merge(higher: ComponentSet): ComponentSet = higher.types.fold(this) { result, type -> mergeComponent(result, higher, type) }

    /**
     * 保留型別參數並按定義合併一個值。
     *
     * @param T 組件值型別。
     * @param lower 低層集合。
     * @param higher 高層集合。
     * @param type 組件定義。
     * @return 合併這個值後的集合。
     */
    private fun <T : Any> mergeComponent(
        lower: ComponentSet,
        higher: ComponentSet,
        type: ComponentType<T>,
    ): ComponentSet {
        val value = checkNotNull(higher[type])
        return lower.with(type, lower[type]?.let { type.merge(it, value) } ?: value)
    }

    /**
     * 合併同層來源，不使用高層覆寫規則處理衝突。
     *
     * @param other 另一來源的獨立結果。
     * @return 合併後的集合。
     */
    fun mergePeers(other: ComponentSet): ComponentSet = other.types.fold(this) { result, type ->
        mergePeerComponent(result, other, type)
    }

    /**
     * 保留值型別並合併一個同層項目。
     *
     * @param T 值型別。
     * @param first 已收集的結果。
     * @param second 新來源結果。
     * @param type 組件定義。
     * @return 合併後的結果。
     */
    private fun <T : Any> mergePeerComponent(
        first: ComponentSet,
        second: ComponentSet,
        type: ComponentType<T>,
    ): ComponentSet {
        val value = checkNotNull(second[type])
        return first.with(type, first[type]?.let { type.mergePeers(it, value) } ?: value)
    }

    /** 明確由組件定義開放的公共標記，不包含私有 key 或值。 */
    val publicMarkerIds: Set<String> get() = entries.values.flatMap { it.publicMarkerIds() }.toSet()

    /**
     * 確認所有組件都適用於即將附加的資料對象。
     *
     * @param target 接收組件的資料對象。
     */
    fun requireTarget(target: ComponentOwnerType<*>) {
        require(types.all { it.target === target }) { "Component target does not match ${target.id}" }
    }

    /** 以同一組型別定義與組件值比較集合，不依插入順序。 */
    override fun equals(other: Any?): Boolean = other is ComponentSet && entries == other.entries

    /** 與組件值相等規則一致的雜湊值。 */
    override fun hashCode(): Int = entries.hashCode()

    /** 僅列出組件 ID，不將私有組件值加入一般診斷字串。 */
    override fun toString(): String = "ComponentSet(${entries.keys.sorted().joinToString()})"

    /**
     * 內部保存一個型別明確的獨立值。
     *
     * @param T 組件值型別。
     * @property type 組件定義。
     * @property value 已與來源斷開別名的值。
     */
    private data class Entry<T : Any>(val type: ComponentType<T>, private val value: T) {
        /** 以原定義建立可交給讀取者的獨立副本。 */
        fun snapshot(): T = type.snapshot(value)

        /** 透過同一個定義建立公共標記投影。 */
        fun publicMarkerIds(): Set<String> = type.publicMarkerIds(value)
    }

    companion object {
        /** 沒有組件時使用的空集合。 */
        val EMPTY: ComponentSet = ComponentSet(emptyMap())
    }
}
