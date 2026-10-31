package com.doublemoon1119.mahjongcraft.example.format

import com.doublemoon1119.mahjongcraft.example.action.ExamplePairingRevealHandAction
import com.doublemoon1119.mahjongcraft.example.format.persistence.ExamplePairingDiscardPilePersistenceDto
import com.doublemoon1119.mahjongcraft.example.format.persistence.ExamplePairingExhaustiveDrawReasonPersistenceDto
import com.doublemoon1119.mahjongcraft.example.format.persistence.ExamplePairingRevealHandActionPersistenceDto
import com.doublemoon1119.mahjongcraft.example.format.persistence.ExamplePairingRuleConfigPersistenceDto
import com.doublemoon1119.mahjongcraft.example.pairing.ExamplePairingDiscardPile
import com.doublemoon1119.mahjongcraft.example.pairing.ExamplePairingExhaustiveDrawReason
import com.doublemoon1119.mahjongcraft.example.pairing.ExamplePairingIds
import com.doublemoon1119.mahjongcraft.example.pairing.ExamplePairingRuleConfig
import com.doublemoon1119.mahjongcraft.flow.persistence.format.api.registration.PersistenceRegistrars
import com.doublemoon1119.mahjongcraft.flow.persistence.format.core.PersistenceDtoDefinition

/**
 * 登記範例自己的保存型別；網路 DTO 不作為存檔格式使用。
 *
 * @param registries 啟動期間尚未凍結的保存註冊表。
 */
fun registerExamplePersistenceDtos(registries: PersistenceRegistrars) {
    registerExampleComponentPersistenceDtos(registries)
    registries.ruleConfigs.register(
        definition = PersistenceDtoDefinition(
            typeKey = ExampleFormatIds.RULE_CONFIG,
            domainClass = ExamplePairingRuleConfig::class,
            serializer = ExamplePairingRuleConfigPersistenceDto.serializer(),
            toDto = { it.toPersistenceDto() },
            toDomain = { it.toDomain() },
            compactInReplay = true,
        ),
    )
    registries.discardPiles.register(
        definition = PersistenceDtoDefinition(
            typeKey = ExampleFormatIds.DISCARD_PILE,
            domainClass = ExamplePairingDiscardPile::class,
            serializer = ExamplePairingDiscardPilePersistenceDto.serializer(),
            toDto = { value, converter -> value.toPersistenceDto(converter.components) },
            toDomain = { dto, converter -> dto.toDomain(converter.components) },
            compactInReplay = true,
        ),
    )

    registries.exhaustiveDrawReasons.register(
        definition = PersistenceDtoDefinition(
            typeKey = ExampleFormatIds.EXHAUSTIVE_DRAW,
            domainClass = ExamplePairingExhaustiveDrawReason::class,
            serializer = ExamplePairingExhaustiveDrawReasonPersistenceDto.serializer(),
            toDto = { ExamplePairingExhaustiveDrawReasonPersistenceDto },
            toDomain = { ExamplePairingExhaustiveDrawReason },
            compactInReplay = true,
        ),
    )
    registries.extensionGameActions.register(
        definition = PersistenceDtoDefinition(
            typeKey = ExamplePairingIds.REVEAL_HAND_ACTION,
            domainClass = ExamplePairingRevealHandAction::class,
            serializer = ExamplePairingRevealHandActionPersistenceDto.serializer(),
            toDto = { ExamplePairingRevealHandActionPersistenceDto },
            toDomain = { ExamplePairingRevealHandAction },
            compactInReplay = true,
        ),
    )
}
