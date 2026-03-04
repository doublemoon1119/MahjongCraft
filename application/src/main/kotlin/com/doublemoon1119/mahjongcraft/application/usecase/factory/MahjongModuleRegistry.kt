package com.doublemoon1119.mahjongcraft.application.usecase.factory

import com.doublemoon1119.mahjongcraft.domain.factory.MahjongRuleModule
import com.doublemoon1119.mahjongcraft.domain.config.MahjongRuleConfig
import kotlin.reflect.KClass

/**
 * 麻將規則模組註冊中心。
 *
 * 負責管理所有已載入的麻將規則模組。
 */
class MahjongModuleRegistry {
    private val modules = mutableMapOf<KClass<out MahjongRuleConfig>, MahjongRuleModule<*>>()

    /**
     * 註冊一個新的規則模組。
     *
     * @param configClass 規則配置類別。
     * @param module 對應的規則模組。
     */
    fun <T : MahjongRuleConfig> register(configClass: KClass<T>, module: MahjongRuleModule<T>) {
        modules[configClass] = module
    }

    /**
     * 根據配置獲取規則模組。
     *
     * @param config 規則配置實例。
     * @return 對應的模組。
     */
    @Suppress("UNCHECKED_CAST")
    fun <T : MahjongRuleConfig> getModule(config: T): MahjongRuleModule<T> {
        return modules[config::class] as? MahjongRuleModule<T>
            ?: throw IllegalStateException("No MahjongRuleModule registered for configuration: ${config::class.simpleName}")
    }
}
