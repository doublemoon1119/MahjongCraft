package com.doublemoon1119.mahjongcraft.flow.api.event

/**
 * 不可修改的清單快照：建立時複製元素，之後與來源無關。Kotlin 自己實作的唯讀集合在 Java 呼叫修改方法時一律丟出
 * [UnsupportedOperationException]，因此 Java 或轉型都無法修改。
 *
 * @param T 元素型別。
 * @param elements 要複製的元素。
 */
internal class ReadOnlyListSnapshot<T>(elements: Collection<T>) : AbstractList<T>() {
    /** 建立時複製的元素。 */
    private val items: List<T> = elements.toList()

    override val size: Int get() = items.size

    override fun get(index: Int): T = items[index]
}

/**
 * 不可修改的集合快照：建立時複製元素並保留原本的順序，之後與來源無關。迭代器同樣由 Kotlin 實作，無法經由它移除元素。
 *
 * @param T 元素型別。
 * @param elements 要複製的元素。
 */
internal class ReadOnlySetSnapshot<T>(elements: Iterable<T>) : AbstractSet<T>() {
    /** 建立時複製的元素。 */
    private val items: Set<T> = elements.toCollection(LinkedHashSet())

    override val size: Int get() = items.size

    override fun contains(element: T): Boolean = element in items

    override fun iterator(): Iterator<T> {
        val source = items.iterator()
        return object : Iterator<T> {
            override fun hasNext(): Boolean = source.hasNext()

            override fun next(): T = source.next()
        }
    }
}

/**
 * 複製來源並只暴露不可修改的鍵值項目。
 *
 * @param K 鍵型別。
 * @param V 值型別。
 * @param source 要複製的映射。
 */
internal class ReadOnlyMapSnapshot<K, V>(source: Map<K, V>) : AbstractMap<K, V>() {
    override val entries: Set<Map.Entry<K, V>> = ReadOnlySetSnapshot(
        source.map { (entryKey, entryValue) ->
            object : Map.Entry<K, V> {
                override val key: K = entryKey

                override val value: V = entryValue

                override fun equals(other: Any?): Boolean = other is Map.Entry<*, *> && key == other.key && value == other.value

                override fun hashCode(): Int = (key?.hashCode() ?: 0) xor (value?.hashCode() ?: 0)

                override fun toString(): String = "$key=$value"
            }
        },
    )
}
