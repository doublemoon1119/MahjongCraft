package com.doublemoon1119.mahjongcraft.logic.component

import com.doublemoon1119.mahjongcraft.logic.api.registration.ComponentRegistrar

/** 管理已登記的組件定義；擴充只透過 [ComponentRegistrar] 登記，查詢、解析與凍結由 MahjongCraft 使用。 */
interface ComponentRegistry : ComponentRegistrar {

    /**
     * 取得對象定義。
     *
     * @param id 完整對象識別碼。
     * @return 已登記定義；找不到時為 null。
     */
    fun findOwner(id: String): ComponentOwnerType<*>?

    /** 取得與登記來源獨立的對象定義清單。 */
    fun getAllOwners(): List<ComponentOwnerType<*>>

    /** 取得與登記來源獨立的效果識別碼集合。 */
    fun effectIds(): Set<String>

    /**
     * 以同一輸入收集符合條件的來源，驗證後合併同層結果。
     *
     * @param context 權威輸入及目標對象。
     * @return 不回寫權威狀態的衍生組件。
     */
    fun resolveEffects(context: ComponentEffectContext<*>): ComponentSet

    /** 取得獨立的對外值定義清單。 */
    fun getAllViews(): List<ComponentViewType<*>>

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
