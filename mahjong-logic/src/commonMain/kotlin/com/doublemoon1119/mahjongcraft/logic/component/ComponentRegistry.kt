package com.doublemoon1119.mahjongcraft.logic.component

/** 管理已登記的組件定義。 */
interface ComponentRegistry {
    /** 是否禁止後續登記。 */
    val isFrozen: Boolean

    /**
     * 登記可持有組件的對象；相同 ID 不能重複。
     *
     * @param definition 對象定義。
     */
    fun registerOwner(definition: ComponentOwnerType<*>)

    /**
     * 取得對象定義。
     *
     * @param id 完整對象識別碼。
     * @return 已登記定義；找不到時為 null。
     */
    fun findOwner(id: String): ComponentOwnerType<*>?

    /** 取得與登記來源獨立的對象定義清單。 */
    fun getAllOwners(): List<ComponentOwnerType<*>>

    /**
     * 登記一種完整組件定義，重複 ID 或凍結後登記均拒絕。
     *
     * @param definition 組件的型別、驗證與複製規則。
     */
    fun register(definition: ComponentType<*>)

    /**
     * 登記一個純資料效果來源。
     *
     * @param O 對象型別。
     * @param definition 效果來源定義。
     */
    fun <O : Any> registerEffect(definition: ComponentEffectDefinition<O>)

    /** 取得與登記來源獨立的效果識別碼集合。 */
    fun effectIds(): Set<String>

    /**
     * 以同一輸入收集符合條件的來源，驗證後合併同層結果。
     *
     * @param context 權威輸入及目標對象。
     * @return 不回寫權威狀態的衍生組件。
     */
    fun resolveEffects(context: ComponentEffectContext<*>): ComponentSet

    /**
     * 登記一種對外值。
     *
     * @param definition 輸出值定義。
     */
    fun registerView(definition: ComponentViewType<*>)

    /** 取得獨立的對外值定義清單。 */
    fun getAllViews(): List<ComponentViewType<*>>

    /**
     * 登記權威值到對外值的投影。
     *
     * @param O 對象型別。
     * @param V 權威值型別。
     * @param P 輸出值型別。
     * @param definition 投影定義。
     */
    fun <O : Any, V : Any, P : Any> registerProjection(definition: ComponentProjectionDefinition<O, V, P>)

    /** 取得獨立的投影識別碼集合。 */
    fun projectionIds(): Set<String>

    /**
     * 依明確的投影定義裁切資料，不自動輸出未登記的項目。
     *
     * @param context 已授權的讀取情境。
     * @param components 該對象的權威組件。
     * @return 不含原始組件的輸出集合。
     */
    fun project(context: ComponentProjectionContext<*>, components: ComponentSet): ComponentViewSet

    /**
     * 登記單一組件的建立及換局規則。
     *
     * @param O 對象型別。
     * @param V 組件值型別。
     * @param definition 生命週期定義。
     */
    fun <O : Any, V : Any> registerLifecycle(definition: ComponentLifecycleDefinition<O, V>)

    /**
     * 讓既有組件的生命週期額外適用其他規則；組件與其生命週期可以由其他擴充稍後登記，凍結時檢查。
     *
     * @param definition 額外的適用範圍。
     */
    fun registerLifecycleScope(definition: ComponentLifecycleScope)

    /** 取得已登記生命週期的組件識別碼。 */
    fun lifecycleIds(): Set<String>

    /**
     * 正式開局時建立適用的組件，不從讀取路徑補值。
     *
     * @param context 對象與新一局情境。
     * @return 經驗證的初始組件。
     */
    fun initialize(context: ComponentLifecycleContext<*>): ComponentSet

    /**
     * 逐項重建、保留或轉換組件，不讓單一規則覆蓋其他擴充資料。
     *
     * @param context 新一局情境。
     * @param previous 上一局的已保存組件。
     * @return 下一局組件。
     */
    fun transitionRound(context: ComponentLifecycleContext<*>, previous: ComponentSet): ComponentSet

    /**
     * 驗證對象現有的組件定義、適用規則及必要值，不建立初值或改動資料。
     *
     * 有登記生命週期的組件只能出現在適用的規則與設定中；對象要求生命週期時，沒有登記的組件也會被拒絕。
     *
     * @param context 對象與規則設定。
     * @param components 對象目前攜帶的完整組件。
     */
    fun validateState(context: ComponentLifecycleContext<*>, components: ComponentSet)

    /** 凍結 registry，後續不能新增或覆寫。 */
    fun freeze()

    /**
     * 依完整 ID 查詢組件定義。
     *
     * @param id 完整組件識別碼。
     * @return 已登記定義；不存在時為 null。
     */
    fun find(id: String): ComponentType<*>?

    /** 依登記順序取得獨立的定義清單。 */
    fun getAll(): List<ComponentType<*>>

    /**
     * 確認整個集合使用已登記的同一組定義。
     *
     * @param components 要檢查的組件集合。
     */
    fun validate(components: ComponentSet) {
        components.types.forEach { type ->
            require(find(type.id) === type) { "Unregistered or conflicting component: ${type.id}" }
        }
    }
}
