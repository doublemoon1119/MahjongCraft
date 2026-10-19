package com.doublemoon1119.mahjongcraft.example.format

import com.doublemoon1119.mahjongcraft.example.action.ExamplePairingDeclarationAction
import com.doublemoon1119.mahjongcraft.example.action.ExamplePairingDeclarationCommand
import com.doublemoon1119.mahjongcraft.example.format.network.ExamplePairingDeclarationActionDto
import com.doublemoon1119.mahjongcraft.example.format.network.ExamplePairingDeclarationCommandDto
import com.doublemoon1119.mahjongcraft.example.format.network.ExamplePairingDiscardPileDto
import com.doublemoon1119.mahjongcraft.example.format.network.ExamplePairingExhaustiveDrawReasonDto
import com.doublemoon1119.mahjongcraft.example.format.network.ExamplePairingGameLengthDto
import com.doublemoon1119.mahjongcraft.example.format.network.ExamplePairingPlayerStateDto
import com.doublemoon1119.mahjongcraft.example.format.network.ExamplePairingRuleConfigDto
import com.doublemoon1119.mahjongcraft.example.format.network.ExamplePairingScoreConfigDto
import com.doublemoon1119.mahjongcraft.example.pairing.ExamplePairingDiscardPile
import com.doublemoon1119.mahjongcraft.example.pairing.ExamplePairingExhaustiveDrawReason
import com.doublemoon1119.mahjongcraft.example.pairing.ExamplePairingGameLength
import com.doublemoon1119.mahjongcraft.example.pairing.ExamplePairingPlayerState
import com.doublemoon1119.mahjongcraft.example.pairing.ExamplePairingRuleConfig
import com.doublemoon1119.mahjongcraft.example.pairing.ExamplePairingScoreConfig
import com.doublemoon1119.mahjongcraft.flow.network.dto.registry.DtoDefinition
import com.doublemoon1119.mahjongcraft.flow.network.dto.rule.NetworkDtoRegistries

/**
 * 登記範例自己使用的網路型別，不修改本體的序列化清單。
 *
 * @param registries 啟動期間尚未凍結的網路註冊表。
 */
fun registerExampleNetworkDtos(registries: NetworkDtoRegistries) {
    registries.ruleConfig.register(
        definition = DtoDefinition(
            domainClass = ExamplePairingRuleConfig::class,
            dtoClass = ExamplePairingRuleConfigDto::class,
            serializer = ExamplePairingRuleConfigDto.serializer(),
            toDto = { it.toNetworkDto() },
            toDomain = { it.toDomain() },
        ),
    )
    registries.scoreConfig.register(
        definition = DtoDefinition(
            domainClass = ExamplePairingScoreConfig::class,
            dtoClass = ExamplePairingScoreConfigDto::class,
            serializer = ExamplePairingScoreConfigDto.serializer(),
            toDto = { it.toNetworkDto() },
            toDomain = { it.toDomain() },
        ),
    )
    registries.gameLength.register(
        definition = DtoDefinition(
            domainClass = ExamplePairingGameLength::class,
            dtoClass = ExamplePairingGameLengthDto::class,
            serializer = ExamplePairingGameLengthDto.serializer(),
            toDto = { it.toNetworkDto() },
            toDomain = { it.toDomain() },
        ),
    )
    registries.discardPile.register(
        definition = DtoDefinition(
            domainClass = ExamplePairingDiscardPile::class,
            dtoClass = ExamplePairingDiscardPileDto::class,
            serializer = ExamplePairingDiscardPileDto.serializer(),
            toDto = { it.toNetworkDto() },
            toDomain = { it.toDomain() },
        ),
    )
    registries.playerRuleState.register(
        definition = DtoDefinition(
            domainClass = ExamplePairingPlayerState::class,
            dtoClass = ExamplePairingPlayerStateDto::class,
            serializer = ExamplePairingPlayerStateDto.serializer(),
            toDto = { it.toNetworkDto() },
            toDomain = { it.toDomain() },
        ),
    )
    registries.exhaustiveDrawReason.register(
        definition = DtoDefinition(
            domainClass = ExamplePairingExhaustiveDrawReason::class,
            dtoClass = ExamplePairingExhaustiveDrawReasonDto::class,
            serializer = ExamplePairingExhaustiveDrawReasonDto.serializer(),
            toDto = { ExamplePairingExhaustiveDrawReasonDto },
            toDomain = { ExamplePairingExhaustiveDrawReason },
        ),
    )
    registries.extensionGameAction.register(
        definition = DtoDefinition(
            domainClass = ExamplePairingDeclarationAction::class,
            dtoClass = ExamplePairingDeclarationActionDto::class,
            serializer = ExamplePairingDeclarationActionDto.serializer(),
            toDto = { ExamplePairingDeclarationActionDto },
            toDomain = { ExamplePairingDeclarationAction },
        ),
    )
    registries.extensionGameCommand.register(
        definition = DtoDefinition(
            domainClass = ExamplePairingDeclarationCommand::class,
            dtoClass = ExamplePairingDeclarationCommandDto::class,
            serializer = ExamplePairingDeclarationCommandDto.serializer(),
            toDto = { ExamplePairingDeclarationCommandDto },
            toDomain = { ExamplePairingDeclarationCommand },
        ),
    )
}
