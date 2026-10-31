package com.doublemoon1119.mahjongcraft.logic.module

import com.doublemoon1119.mahjongcraft.logic.api.registration.MahjongModuleRegistrar
import com.doublemoon1119.mahjongcraft.logic.config.MahjongRuleConfig
import kotlin.reflect.KClass

/**
 * 麻將規則模組註冊中心介面。
 * 負責管理不同麻將規則配置 (Config) 與其對應模組 (Module) 的映射關係。
 * 擴充只透過 [MahjongModuleRegistrar] 登記；查詢與凍結由 MahjongCraft 使用。
 */
interface MahjongModuleRegistry : MahjongModuleRegistrar {

    /** 凍結註冊表；凍結後不得再新增或覆寫規則模組。 */
    fun freeze()

    /**
     * 根據傳入的配置獲取已綁定該配置的規則模組實例。
     *
     * @param T 傳入的規則配置型別。
     * @param config 要查詢模組的規則配置。
     * @return 與該配置類別綁定的模組實例。
     * @throws IllegalStateException 尚未註冊此配置類別的規則模組時拋出。
     */
    fun <T : MahjongRuleConfig> getModule(config: T): MahjongRuleModule<T>

    /**
     * 獲取所有已註冊模組的唯一識別碼集合。
     *
     * @return 已註冊模組的 ID 集合。
     */
    fun getAllModuleIds(): Set<String>

    /**
     * 根據 ID 獲取對應的配置類別。
     *
     * @param id 欲查詢的規則模組 ID。
     * @return 對應的配置類別；ID 尚未註冊時為 null。
     */
    fun getConfigClass(id: String): KClass<out MahjongRuleConfig>?
}
