package com.doublemoon1119.mahjongcraft.application.usecase.factory

import com.doublemoon1119.mahjongcraft.domain.factory.MahjongRuleModule
import com.doublemoon1119.mahjongcraft.domain.config.MahjongRuleConfig
import kotlin.reflect.KClass

/**
 * 麻將規則模組註冊中心。
 *
 * 負責管理所有已載入的麻將規則模組。
 * 提供基於規則配置 (Config) 或模組 ID 的查找功能。
 */
class MahjongModuleRegistry {
    /**
     * 主儲存：ID 對應 Module (為了存檔與擴充識別)。
     */
    private val idMap = mutableMapOf<String, MahjongRuleModule<*>>()

    /**
     * 輔助儲存：Config Class 對應 Module (為了程式碼方便調用)。
     */
    private val classMap = mutableMapOf<KClass<out MahjongRuleConfig>, MahjongRuleModule<*>>()

    /**
     * 註冊一個新的規則模組。
     *
     * @param configClass 規則配置類別。
     * @param module 對應的規則模組。
     * @throws IllegalArgumentException 如果該模組 ID 已經被註冊。
     */
    fun <T : MahjongRuleConfig> register(configClass: KClass<T>, module: MahjongRuleModule<T>) {
        if (idMap.containsKey(module.id)) {
            throw IllegalArgumentException("Duplicate Mahjong Module ID: ${module.id}")
        }
        idMap[module.id] = module
        classMap[configClass] = module
    }

    /**
     * 根據配置獲取規則模組。
     *
     * @param config 規則配置實例。
     * @return 對應的模組。
     * @throws IllegalStateException 如果找不到對應的模組。
     */
    @Suppress("UNCHECKED_CAST")
    fun <T : MahjongRuleConfig> getModule(config: T): MahjongRuleModule<T> {
        return classMap[config::class] as? MahjongRuleModule<T>
            ?: throw IllegalStateException("No MahjongRuleModule registered for configuration: ${config::class.simpleName}")
    }

    /**
     * 根據模組 ID 獲取規則模組。
     *
     * 用於讀取存檔或網路封包處理。
     *
     * @param id 規則模組 ID。
     * @return 對應的模組，若無則返回 null。
     */
    fun getModule(id: String): MahjongRuleModule<*>? {
        return idMap[id]
    }

    /**
     * 獲取所有已註冊的規則模組。
     *
     * 通常用於 UI 顯示規則列表供玩家選擇。
     *
     * @return 所有已註冊模組的集合。
     */
    fun getAllModules(): Collection<MahjongRuleModule<*>> {
        return idMap.values
    }
}
