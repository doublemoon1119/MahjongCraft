package com.doublemoon1119.mahjongcraft.logic.module

import com.doublemoon1119.mahjongcraft.logic.config.MahjongRuleConfig
import kotlin.reflect.KClass

/**
 * [MahjongModuleRegistry] 的預設實作。
 *
 * 建構時是空的對照表，不會預先塞入任何規則。要新增規則，直接呼叫 [register] 即可，
 * 不需要修改這個類別。連日麻/台麻這兩個內建規則，也是由 `:mahjong-flow-common` 的
 * `registerBuiltInRuleModules()` 呼叫 [register] 註冊進來，跟第三方規則走同一套流程，
 * 這個類別本身不知道、也不在乎誰註冊了什麼。
 *
 * `:mahjong-logic` 不依賴 Koin，所以「把這個類別綁定成 [MahjongModuleRegistry] 介面」
 * 這件事是由外層（`:mahjong-flow-common` 的 `FlowCommonModule`）的 DI 負責，不是這裡。
 */
class MahjongModuleRegistryImpl : MahjongModuleRegistry {

    private class Entry(
        val id: String,
        val factory: (MahjongRuleConfig, String) -> MahjongRuleModule<*>,
    )

    private val entriesByConfigClass = mutableMapOf<KClass<out MahjongRuleConfig>, Entry>()

    override fun <T : MahjongRuleConfig> register(
        configClass: KClass<T>,
        id: String,
        factory: (T, id: String) -> MahjongRuleModule<T>,
    ) {
        @Suppress("UNCHECKED_CAST")
        entriesByConfigClass[configClass] = Entry(id, factory as (MahjongRuleConfig, String) -> MahjongRuleModule<*>)
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
