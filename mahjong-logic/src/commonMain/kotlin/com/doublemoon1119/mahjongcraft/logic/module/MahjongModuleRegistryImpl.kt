package com.doublemoon1119.mahjongcraft.logic.module

import com.doublemoon1119.mahjongcraft.logic.config.MahjongRuleConfig
import kotlin.reflect.KClass

/**
 * [MahjongModuleRegistry] 的預設實作。
 *
 * 建構時是空的對照表，不會預先塞入任何規則。要新增規則，直接呼叫 [register] 即可，
 * 不需要修改這個類別。內建規則也由外層組裝時呼叫 [register] 註冊，跟第三方規則走同一套流程，
 * 這個類別本身不知道、也不在乎誰註冊了什麼。
 *
 * `:mahjong-logic` 不依賴 DI 框架；把這個類別綁定成 [MahjongModuleRegistry] 由外層組裝負責。
 */
class MahjongModuleRegistryImpl : MahjongModuleRegistry {

    private class Entry(
        val id: String,
        val factory: (MahjongRuleConfig, String) -> MahjongRuleModule<*>,
    )

    private val entriesByConfigClass = mutableMapOf<KClass<out MahjongRuleConfig>, Entry>()

    /** 是否已禁止後續註冊。 */
    private var frozen = false

    override fun <T : MahjongRuleConfig> register(
        configClass: KClass<T>,
        id: String,
        factory: (T, id: String) -> MahjongRuleModule<T>,
    ) {
        check(!frozen) { "Mahjong module registry is frozen" }
        require(configClass !in entriesByConfigClass) { "Mahjong module already registered for $configClass" }
        require(entriesByConfigClass.values.none { it.id == id }) { "Mahjong module ID already registered: $id" }
        @Suppress("UNCHECKED_CAST")
        entriesByConfigClass[configClass] = Entry(id, factory as (MahjongRuleConfig, String) -> MahjongRuleModule<*>)
    }

    override fun freeze() {
        frozen = true
    }

    override fun <T : MahjongRuleConfig> getModule(config: T): MahjongRuleModule<T> {
        val entry = entriesByConfigClass[config::class]
            ?: error("No MahjongRuleModule registered for config class ${config::class}")

        @Suppress("UNCHECKED_CAST")
        return entry.factory(config, entry.id) as MahjongRuleModule<T>
    }

    override fun getAllModuleIds(): Set<String> = entriesByConfigClass.values.map { it.id }.toSet()

    override fun getConfigClass(id: String): KClass<out MahjongRuleConfig>? = entriesByConfigClass.entries.find { it.value.id == id }?.key
}
