package com.doublemoon1119.mahjongcraft.extension

import com.doublemoon1119.mahjongcraft.flow.network.dto.rule.NetworkDtoRegistries
import com.doublemoon1119.mahjongcraft.flow.persistence.dto.registry.PersistenceRegistries
import com.doublemoon1119.mahjongcraft.logic.module.MahjongModuleRegistry
import com.doublemoon1119.mahjongcraft.logic.module.MahjongRuleModule
import com.doublemoon1119.mahjongcraft.logic.tile.TileTypeRegistry

/**
 * 第三方麻將規則在 MahjongCraft runtime 啟動前登記所有必要整合的共用契約。
 *
 * loader adapter 負責發現實作並交給 [MahjongExtensionRegistrar]；extension 不應自行取得 Koin 或依賴
 * MahjongCraft 的初始化順序。
 */
interface MahjongExtension {
    /** 第三方 extension 的穩定識別字串，用於診斷註冊錯誤。 */
    val id: String

    /** 登記規則配置與 [MahjongRuleModule] factory。 */
    fun registerRuleModules(registry: MahjongModuleRegistry)

    /**
     * 登記第三方規則使用的擴充牌種。
     *
     * 預設不註冊任何牌，使只提供既有 Numeric／Honor 規則的 extension 不必加入空實作。
     */
    fun registerTileTypes(registry: TileTypeRegistry) = Unit

    /** 登記所有第三方規則需要的 network DTO mapper。 */
    fun registerNetworkDtos(registries: NetworkDtoRegistries)

    /** 登記所有第三方規則需要的 persistence DTO mapper。 */
    fun registerPersistenceDtos(registries: PersistenceRegistries)
}
