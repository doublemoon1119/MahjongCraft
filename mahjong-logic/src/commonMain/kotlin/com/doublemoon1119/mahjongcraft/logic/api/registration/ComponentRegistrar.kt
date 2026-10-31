package com.doublemoon1119.mahjongcraft.logic.api.registration

import com.doublemoon1119.mahjongcraft.logic.component.ComponentEffectDefinition
import com.doublemoon1119.mahjongcraft.logic.component.ComponentLifecycleDefinition
import com.doublemoon1119.mahjongcraft.logic.component.ComponentLifecycleScope
import com.doublemoon1119.mahjongcraft.logic.component.ComponentOwnerType
import com.doublemoon1119.mahjongcraft.logic.component.ComponentProjectionDefinition
import com.doublemoon1119.mahjongcraft.logic.component.ComponentType
import com.doublemoon1119.mahjongcraft.logic.component.ComponentViewType

/** 擴充登記組件、效果、投影與生命週期時取得的登記介面；查詢、解析與凍結由 MahjongCraft 進行。 */
interface ComponentRegistrar {
    /** 是否禁止後續登記。 */
    val isFrozen: Boolean

    /**
     * 登記可持有組件的對象；相同 ID 不能重複。
     *
     * @param definition 對象定義。
     */
    fun registerOwner(definition: ComponentOwnerType<*>)

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

    /**
     * 登記一種對外值。
     *
     * @param definition 輸出值定義。
     */
    fun registerView(definition: ComponentViewType<*>)

    /**
     * 登記權威值到對外值的投影。
     *
     * @param O 對象型別。
     * @param V 權威值型別。
     * @param P 輸出值型別。
     * @param definition 投影定義。
     */
    fun <O : Any, V : Any, P : Any> registerProjection(definition: ComponentProjectionDefinition<O, V, P>)

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
}
