package com.doublemoon1119.mahjongcraft.logic.component

/**
 * 一次明確的組件設定與移除，帶著改動前預期的舊值，不持有呼叫端集合的別名。
 *
 * 每個被設定或移除的組件都以 [expected] 比對改動前的值：有列出時必須相等，沒有列出時必須原本不存在。
 * 舊值不符代表這次變更是根據過時的狀態算出，套用時拒絕，不覆寫另一個已提交的結果。
 *
 * @property values 要設定的獨立值。
 * @param removals 要移除的組件定義，不能同時出現在 values。
 * @property expected 被設定或移除的組件在改動前的值；只能列出本次改動的組件。
 */
class ComponentPatch(
    val values: ComponentSet = ComponentSet.EMPTY,
    removals: Set<ComponentType<*>> = emptySet(),
    val expected: ComponentSet = ComponentSet.EMPTY,
) {
    /** 已捕捉的移除定義，不直接交給外部修改。 */
    private val removedTypes = removals.toSet()

    /** 移除定義的獨立快照。 */
    val removals: Set<ComponentType<*>> get() = removedTypes.toSet()

    /** 沒有任何設定或移除時為 true。 */
    val isEmpty: Boolean get() = values.isEmpty && removedTypes.isEmpty()

    init {
        require(removedTypes.map { it.id }.distinct().size == removedTypes.size) { "Conflicting component removals" }
        require(values.types.none { value -> removedTypes.any { it.id == value.id } }) { "A component cannot be set and removed together" }
        val changedIds = values.types.map { it.id } + removedTypes.map { it.id }
        require(expected.types.all { it.id in changedIds }) { "Expected component values must belong to changed components" }
    }

    /**
     * 確認舊值與對象後套用變更，保留沒有被指定的值。
     *
     * @param original 變更前的不可變集合。
     * @param ownerType 接受變更的對象型別。
     * @return 變更後的集合。
     */
    fun applyTo(original: ComponentSet, ownerType: ComponentOwnerType<*>): ComponentSet {
        original.requireTarget(ownerType)
        values.requireTarget(ownerType)
        require(removedTypes.all { it.target === ownerType }) { "Component removal target does not match ${ownerType.id}" }
        (values.types + removedTypes).forEach { type ->
            require(matchesExpected(original, type)) { "Stale component patch: ${type.id}" }
        }
        return values.types.fold(removedTypes.fold(original) { set, type -> set.without(type) }) { set, type ->
            copyComponent(set, values, type)
        }
    }

    /** 設定值、移除定義與預期舊值都相同的變更相等。 */
    override fun equals(other: Any?): Boolean = other is ComponentPatch && values == other.values && removedTypes == other.removedTypes && expected == other.expected

    /** 與相等規則一致的雜湊值。 */
    override fun hashCode(): Int = (values.hashCode() * 31 + removedTypes.hashCode()) * 31 + expected.hashCode()

    /** 只列出組件 ID，不將私有組件值加入一般診斷字串。 */
    override fun toString(): String = "ComponentPatch(values=$values, removals=${removedTypes.map { it.id }}, expected=$expected)"

    /**
     * 比對一個組件改動前的值與預期值。
     *
     * @param T 組件值型別。
     * @param original 變更前的集合。
     * @param type 要比對的定義。
     * @return 兩邊相等，或兩邊都不存在時為 true。
     */
    private fun <T : Any> matchesExpected(original: ComponentSet, type: ComponentType<T>): Boolean = original[type] == expected[type]

    /**
     * 保留捕捉到的值型別，複製一個指定組件。
     *
     * @param T 組件值型別。
     * @param target 目的集合。
     * @param source 來源集合。
     * @param type 要複製的定義。
     * @return 包含該值的新集合。
     */
    private fun <T : Any> copyComponent(
        target: ComponentSet,
        source: ComponentSet,
        type: ComponentType<T>,
    ): ComponentSet = target.with(type, checkNotNull(source[type]))

    /** 建立變更的共用入口。 */
    companion object {
        /**
         * 以目前集合中的值作為預期舊值，建立設定與移除。
         *
         * @param current 計算這次變更時讀到的集合。
         * @param values 要設定的值。
         * @param removals 要移除的定義。
         * @return 帶著改動組件目前值的變更。
         */
        fun basedOn(
            current: ComponentSet,
            values: ComponentSet = ComponentSet.EMPTY,
            removals: Set<ComponentType<*>> = emptySet(),
        ): ComponentPatch = ComponentPatch(
            values = values,
            removals = removals,
            expected = (values.types + removals).fold(ComponentSet.EMPTY) { result, type -> copyPresent(result, current, type) },
        )

        /**
         * 若來源有值，複製一個組件。
         *
         * @param T 組件值型別。
         * @param target 目的集合。
         * @param source 來源集合。
         * @param type 要複製的定義。
         * @return 來源有值時包含該值的新集合，否則為原集合。
         */
        private fun <T : Any> copyPresent(
            target: ComponentSet,
            source: ComponentSet,
            type: ComponentType<T>,
        ): ComponentSet = source[type]?.let { target.with(type, it) } ?: target
    }
}
