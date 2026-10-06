package com.doublemoon1119.mahjongcraft.bundled

import com.doublemoon1119.mahjongcraft.extension.MahjongExtension
import com.doublemoon1119.mahjongcraft.flow.network.dto.rule.NetworkDtoRegistries
import com.doublemoon1119.mahjongcraft.flow.network.dto.rule.riichi.toDomain
import com.doublemoon1119.mahjongcraft.flow.network.dto.rule.taiwan.TaiwanDiscardPileDto
import com.doublemoon1119.mahjongcraft.flow.network.dto.rule.taiwan.TaiwanGameLengthDto
import com.doublemoon1119.mahjongcraft.flow.network.dto.rule.taiwan.TaiwanRuleConfigDto
import com.doublemoon1119.mahjongcraft.flow.network.dto.rule.taiwan.TaiwanScoreConfigDto
import com.doublemoon1119.mahjongcraft.flow.network.dto.rule.taiwan.toDomain
import com.doublemoon1119.mahjongcraft.flow.network.dto.rule.taiwan.toTaiwanDto
import com.doublemoon1119.mahjongcraft.flow.persistence.format.game.toDomain
import com.doublemoon1119.mahjongcraft.flow.persistence.format.game.toPersistenceDto
import com.doublemoon1119.mahjongcraft.flow.persistence.format.history.replay.HistoryReplayProjectionRegistry
import com.doublemoon1119.mahjongcraft.flow.persistence.format.registry.PersistenceRegistries
import com.doublemoon1119.mahjongcraft.flow.persistence.format.rule.BuiltInDiscardPilePersistenceKeys
import com.doublemoon1119.mahjongcraft.flow.persistence.format.rule.BuiltinHistoryReplayDiscardCodecs
import com.doublemoon1119.mahjongcraft.flow.persistence.format.rule.TaiwanDiscardPilePersistenceDto
import com.doublemoon1119.mahjongcraft.flow.persistence.format.rule.TaiwanRuleConfigPersistenceDto
import com.doublemoon1119.mahjongcraft.flow.persistence.format.rule.toDomain
import com.doublemoon1119.mahjongcraft.flow.persistence.format.rule.toPersistenceDto
import com.doublemoon1119.mahjongcraft.logic.module.BuiltInRuleModuleIds
import com.doublemoon1119.mahjongcraft.logic.module.MahjongModuleRegistry
import com.doublemoon1119.mahjongcraft.logic.rules.taiwan.TaiwanDiscardPile
import com.doublemoon1119.mahjongcraft.logic.rules.taiwan.TaiwanGameLength
import com.doublemoon1119.mahjongcraft.logic.rules.taiwan.TaiwanRuleConfig
import com.doublemoon1119.mahjongcraft.logic.rules.taiwan.TaiwanRuleModule
import com.doublemoon1119.mahjongcraft.logic.rules.taiwan.TaiwanScoreConfig
import com.doublemoon1119.mahjongcraft.logic.rules.taiwan.tile.TaiwanTileTypes
import com.doublemoon1119.mahjongcraft.logic.tile.TileTypeDefinition
import com.doublemoon1119.mahjongcraft.logic.tile.TileTypeRegistry
import com.doublemoon1119.mahjongcraft.metadata.MahjongCraftMetadata

/**
 * 隨 MahjongCraft 發布的台麻 extension；台麻在平台無關層的所有登記都寫在這裡，透過與第三方相同的 [MahjongExtension] 回呼登記。
 *
 * 包含規則模組、花牌牌種、規則設定與牌河的網路及存檔格式，以及牌譜牌河格式。
 */
object BundledTaiwanExtension : MahjongExtension {
    override val id: String = MahjongCraftMetadata.id("taiwan")

    override fun registerRuleModules(registry: MahjongModuleRegistry) {
        registry.register(TaiwanRuleConfig::class, BuiltInRuleModuleIds.TAIWAN) { config, id -> TaiwanRuleModule(id, config) }
    }

    override fun registerTileTypes(registry: TileTypeRegistry) {
        TaiwanTileTypes.ALL.forEach { id -> registry.register(TileTypeDefinition(id)) }
    }

    override fun registerNetworkDtos(registries: NetworkDtoRegistries) {
        registries.ruleConfig.register(
            TaiwanRuleConfig::class,
            TaiwanRuleConfigDto::class,
            TaiwanRuleConfigDto.serializer(),
            { it.toTaiwanDto(registries) },
            { it.toDomain(registries) },
        )
        registries.scoreConfig.register(
            TaiwanScoreConfig::class,
            TaiwanScoreConfigDto::class,
            TaiwanScoreConfigDto.serializer(),
            TaiwanScoreConfig::toTaiwanDto,
            TaiwanScoreConfigDto::toDomain,
        )
        registries.gameLength.register(
            TaiwanGameLength.OneGame::class,
            TaiwanGameLengthDto.OneGame::class,
            TaiwanGameLengthDto.OneGame.serializer(),
            { TaiwanGameLengthDto.OneGame },
            { TaiwanGameLength.OneGame },
        )
        registries.gameLength.register(
            TaiwanGameLength.East::class,
            TaiwanGameLengthDto.East::class,
            TaiwanGameLengthDto.East.serializer(),
            { TaiwanGameLengthDto.East },
            { TaiwanGameLength.East },
        )
        registries.gameLength.register(
            TaiwanGameLength.TwoWinds::class,
            TaiwanGameLengthDto.TwoWinds::class,
            TaiwanGameLengthDto.TwoWinds.serializer(),
            { TaiwanGameLengthDto.TwoWinds },
            { TaiwanGameLength.TwoWinds },
        )
        registries.gameLength.register(
            TaiwanGameLength.FourWinds::class,
            TaiwanGameLengthDto.FourWinds::class,
            TaiwanGameLengthDto.FourWinds.serializer(),
            { TaiwanGameLengthDto.FourWinds },
            { TaiwanGameLength.FourWinds },
        )
        registries.discardPile.register(
            TaiwanDiscardPile::class,
            TaiwanDiscardPileDto::class,
            TaiwanDiscardPileDto.serializer(),
            TaiwanDiscardPile::toTaiwanDto,
            TaiwanDiscardPileDto::toDomain,
        )
    }

    override fun registerPersistenceDtos(registries: PersistenceRegistries) {
        registries.ruleConfigs.register(
            typeKey = "mahjongcraft:taiwan/rule_config",
            domainClass = TaiwanRuleConfig::class,
            serializer = TaiwanRuleConfigPersistenceDto.serializer(),
            toDto = TaiwanRuleConfig::toPersistenceDto,
            toDomain = TaiwanRuleConfigPersistenceDto::toDomain,
            compactInReplay = true,
        )
        registries.discardPiles.register(
            typeKey = BuiltInDiscardPilePersistenceKeys.TAIWAN,
            domainClass = TaiwanDiscardPile::class,
            serializer = TaiwanDiscardPilePersistenceDto.serializer(),
            toDto = TaiwanDiscardPile::toPersistenceDto,
            toDomain = TaiwanDiscardPilePersistenceDto::toDomain,
            compactInReplay = true,
        )
    }

    override fun registerHistoryReplayProjections(registry: HistoryReplayProjectionRegistry) {
        registry.registerDiscard(BuiltInDiscardPilePersistenceKeys.TAIWAN, BuiltinHistoryReplayDiscardCodecs.taiwan)
    }
}
