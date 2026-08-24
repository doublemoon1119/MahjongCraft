package com.doublemoon1119.mahjongcraft.extension

import com.doublemoon1119.mahjongcraft.flow.common.game.service.WinCelebrationCueResolverRegistry
import com.doublemoon1119.mahjongcraft.flow.network.dto.rule.NetworkDtoRegistries
import com.doublemoon1119.mahjongcraft.flow.persistence.dto.registry.PersistenceRegistries
import com.doublemoon1119.mahjongcraft.logic.module.MahjongModuleRegistry
import com.doublemoon1119.mahjongcraft.logic.module.MahjongRuleModule
import com.doublemoon1119.mahjongcraft.logic.tile.TileTypeRegistry

/**
 * 第三方麻將規則在 MahjongCraft runtime 啟動前登記所有必要整合的共用契約。
 *
 * 這裡只涵蓋跟遊戲平台無關的規則邏輯本身：規則配置與計算（[registerRuleModules]）、自訂牌種
 * （[registerTileTypes]）、以及網路／存檔層的資料轉換（[registerNetworkDtos]／
 * [registerPersistenceDtos]）。任何跟特定遊戲平台相關的整合（例如 Minecraft 的貼圖 asset key、
 * 顯示名稱、演出效果等）都不屬於這裡，應該由該平台各自定義的對應介面負責——Minecraft 平台是
 * `com.doublemoon1119.mahjongcraft.platform.minecraft.extension.MinecraftMahjongExtension`。這個
 * 分工是刻意的：`mahjong-extension-api` 本身不依賴任何遊戲平台，同一份規則邏輯才有機會被不同平台
 * 重複使用，不會被綁死在單一平台的概念（如 Minecraft 的 asset key）上。
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

    /** 登記此規則模組的胡牌展示提示解析器。 */
    fun registerWinCelebrationCueResolvers(registry: WinCelebrationCueResolverRegistry) = Unit

    /** 登記所有第三方規則需要的 network DTO mapper。 */
    fun registerNetworkDtos(registries: NetworkDtoRegistries)

    /** 登記所有第三方規則需要的 persistence DTO mapper。 */
    fun registerPersistenceDtos(registries: PersistenceRegistries)
}
