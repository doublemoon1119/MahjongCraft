package com.doublemoon1119.mahjongcraft.flow.api.event

/**
 * 不可修改的清單快照：建立時複製元素，之後與來源無關。Kotlin 自己實作的唯讀集合在 Java 呼叫修改方法時一律丟出
 * [UnsupportedOperationException]，因此 Java 或轉型都無法修改。
 *
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
