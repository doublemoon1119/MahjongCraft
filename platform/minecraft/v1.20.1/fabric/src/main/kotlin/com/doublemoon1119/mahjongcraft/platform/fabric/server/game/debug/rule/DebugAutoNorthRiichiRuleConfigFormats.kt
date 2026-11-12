package com.doublemoon1119.mahjongcraft.platform.fabric.server.game.debug.rule

import com.doublemoon1119.mahjongcraft.flow.network.dto.api.registration.NetworkDtoRegistrars
import com.doublemoon1119.mahjongcraft.flow.network.dto.registry.DtoDefinition
import com.doublemoon1119.mahjongcraft.flow.network.dto.rule.MahjongRuleConfigDto
import com.doublemoon1119.mahjongcraft.flow.network.dto.rule.riichi.ThreePlayerRiichiRuleConfigDto
import com.doublemoon1119.mahjongcraft.flow.network.dto.rule.riichi.toDomain
import com.doublemoon1119.mahjongcraft.flow.network.dto.rule.riichi.toThreePlayerRiichiDto
import com.doublemoon1119.mahjongcraft.flow.persistence.format.api.registration.PersistenceRegistrars
import com.doublemoon1119.mahjongcraft.flow.persistence.format.core.PersistenceDtoDefinition
import com.doublemoon1119.mahjongcraft.flow.persistence.format.rule.ThreePlayerRiichiRuleConfigPersistenceDto
import com.doublemoon1119.mahjongcraft.flow.persistence.format.rule.toDomain
import com.doublemoon1119.mahjongcraft.flow.persistence.format.rule.toPersistenceDto
import com.doublemoon1119.mahjongcraft.metadata.MahjongCraftMetadata
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * [DebugAutoNorthRiichiRuleConfig] 的網路 DTO。
 *
 * @property base 三人日麻設定的網路 DTO。
 */
@Serializable
@SerialName("mahjongcraft:debug_auto_north_riichi/rule_config")
internal data class DebugAutoNorthRiichiRuleConfigDto(
    val base: ThreePlayerRiichiRuleConfigDto,
) : MahjongRuleConfigDto

/**
 * [DebugAutoNorthRiichiRuleConfig] 的 persistence DTO。
 *
 * @property base 三人日麻設定的 persistence DTO。
 */
@Serializable
internal data class DebugAutoNorthRiichiRuleConfigPersistenceDto(
    val base: ThreePlayerRiichiRuleConfigPersistenceDto,
)

/**
 * 登記開發用規則配置的網路格式。
 *
 * @param registries 網路 DTO registry 集合。
 */
internal fun registerDebugAutoNorthRiichiNetworkDtos(registries: NetworkDtoRegistrars) {
    registries.ruleConfig.register(
        definition = DtoDefinition(
            domainClass = DebugAutoNorthRiichiRuleConfig::class,
            dtoClass = DebugAutoNorthRiichiRuleConfigDto::class,
            serializer = DebugAutoNorthRiichiRuleConfigDto.serializer(),
            toDto = { value, converter -> DebugAutoNorthRiichiRuleConfigDto(value.base.toThreePlayerRiichiDto(converter)) },
            toDomain = { dto, converter -> DebugAutoNorthRiichiRuleConfig(dto.base.toDomain(converter)) },
        ),
    )
}

/**
 * 登記開發用規則配置的存檔格式。
 *
 * @param registries persistence registry 集合。
 */
internal fun registerDebugAutoNorthRiichiPersistenceDtos(registries: PersistenceRegistrars) {
    registries.ruleConfigs.register(
        definition = PersistenceDtoDefinition(
            typeKey = MahjongCraftMetadata.id("debug_auto_north_riichi/rule_config"),
            domainClass = DebugAutoNorthRiichiRuleConfig::class,
            serializer = DebugAutoNorthRiichiRuleConfigPersistenceDto.serializer(),
            toDto = { DebugAutoNorthRiichiRuleConfigPersistenceDto(it.base.toPersistenceDto()) },
            toDomain = { DebugAutoNorthRiichiRuleConfig(it.base.toDomain()) },
        ),
    )
}
