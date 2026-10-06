package com.doublemoon1119.mahjongcraft.bundled

import com.doublemoon1119.mahjongcraft.extension.MahjongExtension
import com.doublemoon1119.mahjongcraft.flow.common.di.registerTaiwanRuleModule
import com.doublemoon1119.mahjongcraft.flow.common.di.registerTaiwanTileTypes
import com.doublemoon1119.mahjongcraft.flow.network.dto.registry.registerTaiwanRuleDtos
import com.doublemoon1119.mahjongcraft.flow.network.dto.rule.NetworkDtoRegistries
import com.doublemoon1119.mahjongcraft.flow.persistence.format.history.replay.HistoryReplayProjectionRegistry
import com.doublemoon1119.mahjongcraft.flow.persistence.format.history.replay.registerTaiwanHistoryReplayProjections
import com.doublemoon1119.mahjongcraft.flow.persistence.format.registry.PersistenceRegistries
import com.doublemoon1119.mahjongcraft.flow.persistence.format.registry.registerTaiwanPersistenceDtos
import com.doublemoon1119.mahjongcraft.logic.module.MahjongModuleRegistry
import com.doublemoon1119.mahjongcraft.logic.tile.TileTypeRegistry
import com.doublemoon1119.mahjongcraft.metadata.MahjongCraftMetadata

/**
 * 隨 MahjongCraft 發布的台麻 extension，透過與第三方相同的 [MahjongExtension] 回呼登記台麻的平台無關整合。
 *
 * 包含規則模組、花牌牌種、規則設定與牌河的網路及存檔格式，以及牌譜牌河格式。
 */
object BundledTaiwanExtension : MahjongExtension {
    override val id: String = MahjongCraftMetadata.id("taiwan")

    override fun registerRuleModules(registry: MahjongModuleRegistry) {
        registry.registerTaiwanRuleModule()
    }

    override fun registerTileTypes(registry: TileTypeRegistry) {
        registry.registerTaiwanTileTypes()
    }

    override fun registerNetworkDtos(registries: NetworkDtoRegistries) {
        registries.registerTaiwanRuleDtos()
    }

    override fun registerPersistenceDtos(registries: PersistenceRegistries) {
        registries.registerTaiwanPersistenceDtos()
    }

    override fun registerHistoryReplayProjections(registry: HistoryReplayProjectionRegistry) {
        registry.registerTaiwanHistoryReplayProjections()
    }
}
