package com.doublemoon1119.mahjongcraft.platform.fabric.server.game.debug.rule

import com.doublemoon1119.mahjongcraft.flow.network.dto.api.registration.NetworkDtoRegistrars
import com.doublemoon1119.mahjongcraft.flow.network.dto.registry.DtoDefinition
import com.doublemoon1119.mahjongcraft.flow.network.dto.rule.ExtensionGameActionDto
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
 * [DebugNotenDeclarationRiichiRuleConfig] 的網路 DTO。
 *
 * @property base 四人日麻設定的網路 DTO。
 */
@Serializable
@SerialName("mahjongcraft:debug/noten_declaration_riichi_rule_config")
internal data class DebugNotenDeclarationRiichiRuleConfigDto(
    val base: RiichiRuleConfigDto,
) : MahjongRuleConfigDto

/**
 * [DebugNotenDeclarationRiichiRuleConfig] 的 persistence DTO。
 *
 * @property base 四人日麻設定的 persistence DTO。
 */
@Serializable
internal data class DebugNotenDeclarationRiichiRuleConfigPersistenceDto(
    val base: RiichiRuleConfigPersistenceDto,
)

/** [DebugDeclareNotenAction] 的網路 DTO。 */
@Serializable
@SerialName("mahjongcraft:debug_declare_noten")
internal data object DebugDeclareNotenActionDto : ExtensionGameActionDto

/** [DebugDeclareNotenAction] 的 persistence DTO。 */
@Serializable
internal data object DebugDeclareNotenActionPersistenceDto

/**
 * 登記開發用可宣告不聽四人日麻的規則配置與宣告不聽動作的網路格式。
 *
 * @param registries 網路 DTO registry 集合。
 */
internal fun registerDebugNotenDeclarationRiichiNetworkDtos(registries: NetworkDtoRegistrars) {
    registries.ruleConfig.register(
        definition = DtoDefinition(
            domainClass = DebugNotenDeclarationRiichiRuleConfig::class,
            dtoClass = DebugNotenDeclarationRiichiRuleConfigDto::class,
            serializer = DebugNotenDeclarationRiichiRuleConfigDto.serializer(),
            toDto = { value, converter -> DebugNotenDeclarationRiichiRuleConfigDto(value.base.toRiichiDto(converter)) },
            toDomain = { dto, converter -> DebugNotenDeclarationRiichiRuleConfig(dto.base.toDomain(converter)) },
        ),
    )
    registries.extensionGameAction.register(
        definition = DtoDefinition(
            domainClass = DebugDeclareNotenAction::class,
            dtoClass = DebugDeclareNotenActionDto::class,
            serializer = DebugDeclareNotenActionDto.serializer(),
            toDto = { DebugDeclareNotenActionDto },
            toDomain = { DebugDeclareNotenAction },
        ),
    )
}

/**
 * 登記開發用可宣告不聽四人日麻的規則配置與宣告不聽動作的存檔格式。
 *
 * @param registries persistence registry 集合。
 */
internal fun registerDebugNotenDeclarationRiichiPersistenceDtos(registries: PersistenceRegistrars) {
    registries.ruleConfigs.register(
        definition = PersistenceDtoDefinition(
            typeKey = MahjongCraftMetadata.id("debug/noten_declaration_riichi_rule_config"),
            domainClass = DebugNotenDeclarationRiichiRuleConfig::class,
            serializer = DebugNotenDeclarationRiichiRuleConfigPersistenceDto.serializer(),
            toDto = { DebugNotenDeclarationRiichiRuleConfigPersistenceDto(it.base.toPersistenceDto()) },
            toDomain = { DebugNotenDeclarationRiichiRuleConfig(it.base.toDomain()) },
        ),
    )
    registries.extensionGameActions.register(
        definition = PersistenceDtoDefinition(
            typeKey = DebugDeclareNotenAction.id,
            domainClass = DebugDeclareNotenAction::class,
            serializer = DebugDeclareNotenActionPersistenceDto.serializer(),
            toDto = { DebugDeclareNotenActionPersistenceDto },
            toDomain = { DebugDeclareNotenAction },
            compactInReplay = true,
        ),
    )
}
