package com.doublemoon1119.mahjongcraft.example.format

import com.doublemoon1119.mahjongcraft.example.action.ExamplePairingDeclarationAction
import com.doublemoon1119.mahjongcraft.example.format.persistence.ExamplePairingDeclarationActionPersistenceDto
import com.doublemoon1119.mahjongcraft.example.format.persistence.ExamplePairingDiscardPilePersistenceDto
import com.doublemoon1119.mahjongcraft.example.format.persistence.ExamplePairingExhaustiveDrawReasonPersistenceDto
import com.doublemoon1119.mahjongcraft.example.format.persistence.ExamplePairingPlayerStatePersistenceDto
import com.doublemoon1119.mahjongcraft.example.format.persistence.ExamplePairingRuleConfigPersistenceDto
import com.doublemoon1119.mahjongcraft.example.pairing.ExamplePairingDiscardPile
import com.doublemoon1119.mahjongcraft.example.pairing.ExamplePairingExhaustiveDrawReason
import com.doublemoon1119.mahjongcraft.example.pairing.ExamplePairingIds
import com.doublemoon1119.mahjongcraft.example.pairing.ExamplePairingPlayerState
import com.doublemoon1119.mahjongcraft.example.pairing.ExamplePairingRuleConfig
import com.doublemoon1119.mahjongcraft.flow.persistence.format.core.PersistenceDtoDefinition
import com.doublemoon1119.mahjongcraft.flow.persistence.format.registry.PersistenceRegistries

/**
 * 登記範例自己的保存型別；網路 DTO 不作為存檔格式使用。
 *
 * @param registries 啟動期間尚未凍結的保存註冊表。
 */
fun registerExamplePersistenceDtos(registries: PersistenceRegistries) {
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
            toDto = { it.toPersistenceDto() },
            toDomain = { it.toDomain() },
            compactInReplay = true,
        ),
    )
    registries.playerRuleStates.register(
        definition = PersistenceDtoDefinition(
            typeKey = ExampleFormatIds.PLAYER_STATE,
            domainClass = ExamplePairingPlayerState::class,
            serializer = ExamplePairingPlayerStatePersistenceDto.serializer(),
            toDto = { it.toPersistenceDto() },
            toDomain = { it.toDomain() },
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
            typeKey = ExamplePairingIds.DECLARE_ACTION,
            domainClass = ExamplePairingDeclarationAction::class,
            serializer = ExamplePairingDeclarationActionPersistenceDto.serializer(),
            toDto = { ExamplePairingDeclarationActionPersistenceDto },
            toDomain = { ExamplePairingDeclarationAction },
            compactInReplay = true,
        ),
    )
}
