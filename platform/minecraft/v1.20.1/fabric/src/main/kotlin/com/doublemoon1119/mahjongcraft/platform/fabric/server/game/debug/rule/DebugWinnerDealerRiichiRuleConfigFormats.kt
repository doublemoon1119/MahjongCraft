package com.doublemoon1119.mahjongcraft.platform.fabric.server.game.debug.rule

import com.doublemoon1119.mahjongcraft.flow.network.dto.api.registration.NetworkDtoRegistrars
import com.doublemoon1119.mahjongcraft.flow.network.dto.registry.DtoDefinition
import com.doublemoon1119.mahjongcraft.flow.network.dto.rule.MahjongRuleConfigDto
import com.doublemoon1119.mahjongcraft.flow.network.dto.rule.riichi.RiichiRuleConfigDto
import com.doublemoon1119.mahjongcraft.flow.network.dto.rule.riichi.toDomain
import com.doublemoon1119.mahjongcraft.flow.network.dto.rule.riichi.toRiichiDto
import com.doublemoon1119.mahjongcraft.flow.persistence.format.api.registration.PersistenceRegistrars
import com.doublemoon1119.mahjongcraft.flow.persistence.format.core.PersistenceDtoDefinition
import com.doublemoon1119.mahjongcraft.flow.persistence.format.rule.RiichiRuleConfigPersistenceDto
import com.doublemoon1119.mahjongcraft.flow.persistence.format.rule.toDomain
import com.doublemoon1119.mahjongcraft.flow.persistence.format.rule.toPersistenceDto
import com.doublemoon1119.mahjongcraft.metadata.MahjongCraftMetadata
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * [DebugWinnerDealerRiichiRuleConfig] 的網路 DTO。
 *
 * @property base 四人日麻設定的網路 DTO。
 */
@Serializable
@SerialName("mahjongcraft:debug_winner_dealer_riichi/rule_config")
internal data class DebugWinnerDealerRiichiRuleConfigDto(
    val base: RiichiRuleConfigDto,
) : MahjongRuleConfigDto

/**
 * [DebugWinnerDealerRiichiRuleConfig] 的 persistence DTO。
 *
 * @property base 四人日麻設定的 persistence DTO。
 */
@Serializable
internal data class DebugWinnerDealerRiichiRuleConfigPersistenceDto(
    val base: RiichiRuleConfigPersistenceDto,
)

/**
 * 登記開發用規則配置的網路格式。
 *
 * @param registries 網路 DTO registry 集合。
 */
internal fun registerDebugWinnerDealerRiichiNetworkDtos(registries: NetworkDtoRegistrars) {
    registries.ruleConfig.register(
        definition = DtoDefinition(
            domainClass = DebugWinnerDealerRiichiRuleConfig::class,
            dtoClass = DebugWinnerDealerRiichiRuleConfigDto::class,
            serializer = DebugWinnerDealerRiichiRuleConfigDto.serializer(),
            toDto = { value, converter -> DebugWinnerDealerRiichiRuleConfigDto(value.base.toRiichiDto(converter)) },
            toDomain = { dto, converter -> DebugWinnerDealerRiichiRuleConfig(dto.base.toDomain(converter)) },
        ),
    )
}

/**
 * 登記開發用規則配置的存檔格式。
 *
 * @param registries persistence registry 集合。
 */
internal fun registerDebugWinnerDealerRiichiPersistenceDtos(registries: PersistenceRegistrars) {
    registries.ruleConfigs.register(
        definition = PersistenceDtoDefinition(
            typeKey = MahjongCraftMetadata.id("debug_winner_dealer_riichi/rule_config"),
            domainClass = DebugWinnerDealerRiichiRuleConfig::class,
            serializer = DebugWinnerDealerRiichiRuleConfigPersistenceDto.serializer(),
            toDto = { DebugWinnerDealerRiichiRuleConfigPersistenceDto(it.base.toPersistenceDto()) },
            toDomain = { DebugWinnerDealerRiichiRuleConfig(it.base.toDomain()) },
        ),
    )
}
