package com.doublemoon1119.mahjongcraft.logic.component

/**
 * 已裁切的不可變輸出集合，不提供回寫權威組件的操作。
 *
 * @property entries 只保存本次允許交付的值。
 */
class ComponentViewSet private constructor(private val entries: Map<String, Entry<*>>) {
    /** 依完整 ID 排列的可交付型別，不包含被裁掉的項目。 */
    val types: List<ComponentViewType<*>> get() = entries.values.map { it.type }.sortedBy { it.id }

    /** 是否沒有可交付內容。 */
    val isEmpty: Boolean get() = entries.isEmpty()

    /**
     * 讀取獨立的輸出值。
     *
     * @param V 值型別。
     * @param type 已登記的輸出定義。
     * @return 可讀值；沒有輸出該項時為 null。
     */
    @Suppress("UNCHECKED_CAST")
    operator fun <V : Any> get(type: ComponentViewType<V>): V? {
        val entry = entries[type.id] ?: return null
        require(entry.type === type) { "Conflicting component view definition: ${type.id}" }
        return (entry as Entry<V>).snapshot()
    }

    /**
     * 建立包含指定公開值的新集合，拒絕同 ID 的不同值或定義。
     *
     * @param V 值型別。
     * @param type 輸出定義。
     * @param value 已由投影允許交付的值。
     * @return 不含原始權威組件的新集合。
     */
    fun <V : Any> with(type: ComponentViewType<V>, value: V): ComponentViewSet {
        val previous = entries[type.id]
        require(previous == null || previous.type === type) { "Conflicting component view definition: ${type.id}" }
        val detached = type.snapshot(value)
        require(previous == null || get(type) == detached) { "Conflicting component projection values: ${type.id}" }
        return ComponentViewSet(entries + (type.id to Entry(type, detached)))
    }

    /** 依輸出型別及獨立值比較，不依來源順序。 */
    override fun equals(other: Any?): Boolean = other is ComponentViewSet && entries == other.entries

    /** 與輸出相等規則一致的雜湊值。 */
    override fun hashCode(): Int = entries.hashCode()

    /** 不在一般診斷字串輸出資料內容。 */
    override fun toString(): String = "ComponentViewSet(${entries.keys.sorted().joinToString()})"

    /**
     * 捕捉一個輸出值的具體型別。
     *
     * @param V 值型別。
     * @property type 輸出定義。
     * @property value 與來源獨立的值。
     */
    private data class Entry<V : Any>(val type: ComponentViewType<V>, private val value: V) {
        /** 產生讀取者可持有的獨立值。 */
        fun snapshot(): V = type.snapshot(value)
    }

    companion object {
        /** 沒有任何可交付內容的集合。 */
        val EMPTY = ComponentViewSet(emptyMap())
    }
}
