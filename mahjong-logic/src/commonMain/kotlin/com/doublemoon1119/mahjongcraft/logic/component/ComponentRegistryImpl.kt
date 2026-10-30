package com.doublemoon1119.mahjongcraft.logic.component

/** 建立時為空，與其他規則 registry 一樣由外層登記後凍結。 */
class ComponentRegistryImpl : ComponentRegistry {
    /** 依完整 ID 保留定義及登記順序。 */
    private val definitions = linkedMapOf<String, ComponentType<*>>()

    /** 依完整 ID 保存對象定義。 */
    private val owners = linkedMapOf<String, ComponentOwnerType<*>>()

    /** 各效果來源的型別封裝。 */
    private val effects = linkedMapOf<String, EffectEntry<*>>()

    /** 依完整 ID 保存可交付型別。 */
    private val views = linkedMapOf<String, ComponentViewType<*>>()

    /** 各投影定義的型別封裝。 */
    private val projections = linkedMapOf<String, ProjectionEntry<*, *, *>>()

    /** 各組件建立與換局規則的型別封裝。 */
    private val lifecycles = linkedMapOf<String, LifecycleEntry<*, *>>()

    /** 依登記 ID 保存的額外適用範圍。 */
    private val lifecycleScopes = linkedMapOf<String, ComponentLifecycleScope>()

    override var isFrozen: Boolean = false
        private set

    override fun registerOwner(definition: ComponentOwnerType<*>) {
        check(!isFrozen) { "Component registry is frozen" }
        require(definition.id !in owners) { "Duplicate component owner ID: ${definition.id}" }
        owners[definition.id] = definition
    }

    override fun findOwner(id: String): ComponentOwnerType<*>? = owners[id]

    override fun getAllOwners(): List<ComponentOwnerType<*>> = owners.values.toList()

    override fun register(definition: ComponentType<*>) {
        check(!isFrozen) { "Component registry is frozen" }
        require(definition.id !in definitions) { "Duplicate component ID: ${definition.id}" }
        require(owners[definition.target.id] === definition.target) { "Unregistered component owner: ${definition.target.id}" }
        definitions[definition.id] = definition
    }

    override fun <O : Any> registerEffect(definition: ComponentEffectDefinition<O>) {
        check(!isFrozen) { "Component registry is frozen" }
        require(definition.id !in effects) { "Duplicate component effect ID: ${definition.id}" }
        require(owners[definition.ownerType.id] === definition.ownerType) { "Unregistered component effect owner" }
        effects[definition.id] = EffectEntry(definition)
    }

    override fun effectIds(): Set<String> = effects.keys.toSet()

    override fun resolveEffects(context: ComponentEffectContext<*>): ComponentSet {
        require(owners[context.ownerType.id] === context.ownerType) { "Unregistered component effect context owner" }
        val results = effects.values.mapNotNull { it.resolve(context) }
        results.forEach {
            validate(it)
            it.requireTarget(context.ownerType)
        }
        return results.fold(ComponentSet.EMPTY) { combined, values -> combined.mergePeers(values) }
    }

    /**
     * 在型別封裝內驗證情境，對外不暴露未檢查的轉型。
     *
     * @param O 效果對象型別。
     * @property definition 效果定義。
     */
    private class EffectEntry<O : Any>(val definition: ComponentEffectDefinition<O>) {
        /**
         * 只對符合對象與規則條件的來源執行計算。
         *
         * @param context 同一版本的權威輸入。
         * @return 獨立效果；不適用時為 null。
         */
        @Suppress("UNCHECKED_CAST")
        fun resolve(context: ComponentEffectContext<*>): ComponentSet? {
            if (definition.ownerType !== context.ownerType) return null
            if (!definition.appliesTo(context.ruleModuleId, context.tableState.config)) return null
            definition.ownerType.requireInstance(context.owner)
            return definition.resolve(context as ComponentEffectContext<O>)
        }
    }

    override fun registerView(definition: ComponentViewType<*>) {
        check(!isFrozen) { "Component registry is frozen" }
        require(definition.id !in views) { "Duplicate component view ID: ${definition.id}" }
        views[definition.id] = definition
    }

    override fun getAllViews(): List<ComponentViewType<*>> = views.values.toList()

    override fun <O : Any, V : Any, P : Any> registerProjection(definition: ComponentProjectionDefinition<O, V, P>) {
        check(!isFrozen) { "Component registry is frozen" }
        require(definition.id !in projections) { "Duplicate component projection ID: ${definition.id}" }
        require(owners[definition.ownerType.id] === definition.ownerType) { "Unregistered projection owner" }
        require(definitions[definition.component.id] === definition.component) { "Unregistered projection component" }
        require(views[definition.viewType.id] === definition.viewType) { "Unregistered projection view" }
        projections[definition.id] = ProjectionEntry(definition)
    }

    override fun projectionIds(): Set<String> = projections.keys.toSet()

    override fun project(context: ComponentProjectionContext<*>, components: ComponentSet): ComponentViewSet {
        require(owners[context.ownerType.id] === context.ownerType) { "Unregistered projection context owner" }
        components.requireTarget(context.ownerType)
        validate(components)
        return projections.values.fold(ComponentViewSet.EMPTY) { result, entry -> entry.project(context, components, result) }
    }

    /**
     * 保留權威與輸出的型別關係，只在驗證對象後使用情境。
     *
     * @param O 對象型別。
     * @param V 權威值型別。
     * @param P 輸出值型別。
     * @property definition 投影定義。
     */
    private class ProjectionEntry<O : Any, V : Any, P : Any>(val definition: ComponentProjectionDefinition<O, V, P>) {
        /**
         * 收集單一授權輸出，不適用或不可見時維持原集合。
         *
         * @param context 已授權的讀取情境。
         * @param components 權威組件。
         * @param result 已收集的公開值。
         * @return 新的輸出集合。
         */
        @Suppress("UNCHECKED_CAST")
        fun project(
            context: ComponentProjectionContext<*>,
            components: ComponentSet,
            result: ComponentViewSet,
        ): ComponentViewSet {
            if (context.ownerType !== definition.ownerType) return result
            if (!definition.appliesTo(context.ruleModuleId, context.tableState.config)) return result
            context.owner?.let(definition.ownerType::requireInstance)
            val value = components[definition.component] ?: return result
            val projected = definition.project(context as ComponentProjectionContext<O>, value) ?: return result
            return result.with(definition.viewType, projected)
        }
    }

    override fun <O : Any, V : Any> registerLifecycle(definition: ComponentLifecycleDefinition<O, V>) {
        check(!isFrozen) { "Component registry is frozen" }
        require(definition.component.id !in lifecycles) { "Duplicate component lifecycle: ${definition.component.id}" }
        require(owners[definition.ownerType.id] === definition.ownerType) { "Unregistered component lifecycle owner" }
        require(definitions[definition.component.id] === definition.component) { "Unregistered lifecycle component" }
        lifecycles[definition.component.id] = LifecycleEntry(definition)
    }

    override fun registerLifecycleScope(definition: ComponentLifecycleScope) {
        check(!isFrozen) { "Component registry is frozen" }
        require(definition.id !in lifecycleScopes) { "Duplicate component lifecycle scope: ${definition.id}" }
        lifecycleScopes[definition.id] = definition
    }

    override fun lifecycleIds(): Set<String> = lifecycles.keys.toSet()

    override fun initialize(context: ComponentLifecycleContext<*>): ComponentSet {
        require(owners[context.ownerType.id] === context.ownerType) { "Unregistered lifecycle context owner" }
        return lifecycles.values.fold(ComponentSet.EMPTY) { values, entry -> entry.initialize(context, values) }
    }

    override fun transitionRound(context: ComponentLifecycleContext<*>, previous: ComponentSet): ComponentSet {
        require(context.previousTableState != null) { "Round transition requires the previous table state" }
        require(owners[context.ownerType.id] === context.ownerType) { "Unregistered lifecycle context owner" }
        previous.requireTarget(context.ownerType)
        validate(previous)
        require(previous.types.all { it.id in lifecycles }) { "Stored component has no round lifecycle" }
        return lifecycles.values.fold(ComponentSet.EMPTY) { values, entry -> entry.transition(context, previous, values) }
    }

    override fun validateState(context: ComponentLifecycleContext<*>, components: ComponentSet) {
        require(owners[context.ownerType.id] === context.ownerType) { "Unregistered component state owner" }
        components.requireTarget(context.ownerType)
        validate(components)
        components.types.forEach { type ->
            val lifecycle = lifecycles[type.id]
            require(lifecycle != null || !context.ownerType.requiresLifecycle) { "Stored component has no lifecycle: ${type.id}" }
            require(lifecycle == null || lifecycle.applies(context)) { "Stored component does not apply to this rule: ${type.id}" }
        }
        lifecycles.values.forEach { it.validateState(context, components) }
    }

    /**
     * 捕捉單一組件的生命週期型別。
     *
     * @param O 對象型別。
     * @param V 值型別。
     * @property definition 生命週期定義。
     */
    private inner class LifecycleEntry<O : Any, V : Any>(val definition: ComponentLifecycleDefinition<O, V>) {
        /**
         * 初始化適用對象，不修改其他已建立項目。
         *
         * @param context 開局情境。
         * @param result 已建立的組件。
         * @return 包含本項初值的集合。
         */
        @Suppress("UNCHECKED_CAST")
        fun initialize(context: ComponentLifecycleContext<*>, result: ComponentSet): ComponentSet {
            if (!applies(context)) return result
            definition.ownerType.requireInstance(context.owner)
            return addValue(result, definition.initialValue(context as ComponentLifecycleContext<O>))
        }

        /**
         * 對適用的單一項目套用換局規則。
         *
         * @param context 新一局情境。
         * @param previous 上一局組件。
         * @param result 已轉換的組件。
         * @return 包含本項換局結果的集合。
         */
        @Suppress("UNCHECKED_CAST")
        fun transition(
            context: ComponentLifecycleContext<*>,
            previous: ComponentSet,
            result: ComponentSet,
        ): ComponentSet {
            if (!applies(context)) return result
            definition.ownerType.requireInstance(context.owner)
            val typed = context as ComponentLifecycleContext<O>
            val old = previous[definition.component]
            require(!definition.required || old != null) { "Missing required component: ${definition.component.id}" }
            val value = when (definition.roundTransition) {
                ComponentRoundTransition.RESET -> definition.initialValue(typed)
                ComponentRoundTransition.RETAIN -> old
                ComponentRoundTransition.TRANSFORM -> old?.let { checkNotNull(definition.transform).invoke(typed, it) }
            }
            return addValue(result, value)
        }

        /**
         * 確認適用對象的必要值存在，不呼叫建立或換局函式。
         *
         * @param context 目前對象與規則。
         * @param values 要提交的組件。
         */
        fun validateState(context: ComponentLifecycleContext<*>, values: ComponentSet) {
            if (!applies(context)) return
            require(!definition.required || values[definition.component] != null) {
                "Missing required component: ${definition.component.id}"
            }
        }

        /**
         * 檢查適用對象與規則設定；原登記或任一額外範圍適用即可。
         *
         * @param context 目前建立情境。
         * @return 這項定義適用時為 true。
         */
        fun applies(context: ComponentLifecycleContext<*>): Boolean {
            if (context.ownerType !== definition.ownerType) return false
            val ruleModuleId = context.ruleModuleId
            val config = context.tableState.config
            return definition.appliesTo(ruleModuleId, config) ||
                lifecycleScopes.values.any { it.component.id == definition.component.id && it.appliesTo(ruleModuleId, config) }
        }

        /**
         * 加入經驗證的值；缺少必要值時拒絕建立。
         *
         * @param result 已收集集合。
         * @param value 本項結果。
         * @return 新集合。
         */
        private fun addValue(result: ComponentSet, value: V?): ComponentSet {
            require(!definition.required || value != null) { "Missing required component: ${definition.component.id}" }
            return value?.let { result.with(definition.component, it) } ?: result
        }
    }

    override fun freeze() {
        lifecycleScopes.values.forEach { scope ->
            require(definitions[scope.component.id] === scope.component) { "Unregistered component in lifecycle scope: ${scope.id}" }
            require(scope.component.id in lifecycles) { "Component lifecycle scope has no lifecycle: ${scope.id}" }
        }
        isFrozen = true
    }

    override fun find(id: String): ComponentType<*>? = definitions[id]

    override fun getAll(): List<ComponentType<*>> = definitions.values.toList()
}
