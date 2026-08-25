package com.doublemoon1119.mahjongcraft.flow.network.dto.registry

import com.doublemoon1119.mahjongcraft.flow.common.game.model.riichi.RiichiGameCommand
import com.doublemoon1119.mahjongcraft.flow.network.dto.rule.NetworkDtoRegistries
import com.doublemoon1119.mahjongcraft.flow.network.dto.rule.riichi.RiichiDiscardPileDto
import com.doublemoon1119.mahjongcraft.flow.network.dto.rule.riichi.RiichiDynamicStateDto
import com.doublemoon1119.mahjongcraft.flow.network.dto.rule.riichi.RiichiExhaustiveDrawReasonDto
import com.doublemoon1119.mahjongcraft.flow.network.dto.rule.riichi.RiichiGameActionDto
import com.doublemoon1119.mahjongcraft.flow.network.dto.rule.riichi.RiichiGameCommandDto
import com.doublemoon1119.mahjongcraft.flow.network.dto.rule.riichi.RiichiGameLengthDto
import com.doublemoon1119.mahjongcraft.flow.network.dto.rule.riichi.RiichiPlayerStateDto
import com.doublemoon1119.mahjongcraft.flow.network.dto.rule.riichi.RiichiRuleConfigDto
import com.doublemoon1119.mahjongcraft.flow.network.dto.rule.riichi.RiichiScoreConfigDto
import com.doublemoon1119.mahjongcraft.flow.network.dto.rule.riichi.toDomain
import com.doublemoon1119.mahjongcraft.flow.network.dto.rule.riichi.toRiichiDto
import com.doublemoon1119.mahjongcraft.flow.network.dto.rule.taiwan.TaiwanDiscardPileDto
import com.doublemoon1119.mahjongcraft.flow.network.dto.rule.taiwan.TaiwanGameLengthDto
import com.doublemoon1119.mahjongcraft.flow.network.dto.rule.taiwan.TaiwanRuleConfigDto
import com.doublemoon1119.mahjongcraft.flow.network.dto.rule.taiwan.TaiwanScoreConfigDto
import com.doublemoon1119.mahjongcraft.flow.network.dto.rule.taiwan.toDomain
import com.doublemoon1119.mahjongcraft.flow.network.dto.rule.taiwan.toTaiwanDto
import com.doublemoon1119.mahjongcraft.logic.rules.riichi.RiichiDiscardPile
import com.doublemoon1119.mahjongcraft.logic.rules.riichi.RiichiDynamicState
import com.doublemoon1119.mahjongcraft.logic.rules.riichi.RiichiExhaustiveDrawReason
import com.doublemoon1119.mahjongcraft.logic.rules.riichi.RiichiGameAction
import com.doublemoon1119.mahjongcraft.logic.rules.riichi.RiichiGameLength
import com.doublemoon1119.mahjongcraft.logic.rules.riichi.RiichiPlayerState
import com.doublemoon1119.mahjongcraft.logic.rules.riichi.RiichiRuleConfig
import com.doublemoon1119.mahjongcraft.logic.rules.riichi.RiichiScoreConfig
import com.doublemoon1119.mahjongcraft.logic.rules.taiwan.TaiwanDiscardPile
import com.doublemoon1119.mahjongcraft.logic.rules.taiwan.TaiwanGameLength
import com.doublemoon1119.mahjongcraft.logic.rules.taiwan.TaiwanRuleConfig
import com.doublemoon1119.mahjongcraft.logic.rules.taiwan.TaiwanScoreConfig
import kotlin.uuid.Uuid

/**
 * 註冊 `:mahjong-flow-network-dto` 內建支援的規則模組（日麻、台麻）的 DTO 對照。
 *
 * 比照 `:mahjong-flow-common` 的 `registerBuiltInRuleModules()`：註冊方式與第三方規則相同，皆透過
 * [NetworkDtoRegistries] 底下各個 `DtoRegistry.register(...)`，不具特權；第三方規則要支援
 * 序列化，可在各自的組裝處另行呼叫對應 registry 的 `register(...)`。
 */
fun NetworkDtoRegistries.registerBuiltInRuleConfigDtos() {
    registerBuiltInRuleDtos()
}

/** 登記內建立直 extension action 與 command 的網路 DTO。 */
fun NetworkDtoRegistries.registerRiichiGameActionDtos() {
    extensionGameAction.register(
        RiichiGameAction.Riichi::class,
        RiichiGameActionDto::class,
        RiichiGameActionDto.serializer(),
        { RiichiGameActionDto },
        { RiichiGameAction.Riichi },
    )
    extensionGameCommand.register(
        RiichiGameCommand::class,
        RiichiGameCommandDto::class,
        RiichiGameCommandDto.serializer(),
        { RiichiGameCommandDto(it.tileId.toString()) },
        { RiichiGameCommand(Uuid.parse(it.tileId)) },
    )
}

/** 登記內建日麻與台麻規則狀態的網路 DTO。 */
private fun NetworkDtoRegistries.registerBuiltInRuleDtos() {
    this.ruleConfig.register(
        RiichiRuleConfig::class,
        RiichiRuleConfigDto::class,
        RiichiRuleConfigDto.serializer(),
        { it.toRiichiDto(this) },
        { it.toDomain(this) },
    )
    this.ruleConfig.register(
        TaiwanRuleConfig::class,
        TaiwanRuleConfigDto::class,
        TaiwanRuleConfigDto.serializer(),
        { it.toTaiwanDto(this) },
        { it.toDomain(this) },
    )

    this.scoreConfig.register(
        RiichiScoreConfig::class,
        RiichiScoreConfigDto::class,
        RiichiScoreConfigDto.serializer(),
        RiichiScoreConfig::toRiichiDto,
        RiichiScoreConfigDto::toDomain,
    )
    this.scoreConfig.register(
        TaiwanScoreConfig::class,
        TaiwanScoreConfigDto::class,
        TaiwanScoreConfigDto.serializer(),
        TaiwanScoreConfig::toTaiwanDto,
        TaiwanScoreConfigDto::toDomain,
    )

    this.gameLength.register(
        RiichiGameLength.OneGame::class,
        RiichiGameLengthDto.OneGame::class,
        RiichiGameLengthDto.OneGame.serializer(),
        { RiichiGameLengthDto.OneGame },
        { RiichiGameLength.OneGame },
    )
    this.gameLength.register(
        RiichiGameLength.East::class,
        RiichiGameLengthDto.East::class,
        RiichiGameLengthDto.East.serializer(),
        { RiichiGameLengthDto.East },
        { RiichiGameLength.East },
    )
    this.gameLength.register(
        RiichiGameLength.TwoWinds::class,
        RiichiGameLengthDto.TwoWinds::class,
        RiichiGameLengthDto.TwoWinds.serializer(),
        { RiichiGameLengthDto.TwoWinds },
        { RiichiGameLength.TwoWinds },
    )
    this.gameLength.register(
        TaiwanGameLength.OneGame::class,
        TaiwanGameLengthDto.OneGame::class,
        TaiwanGameLengthDto.OneGame.serializer(),
        { TaiwanGameLengthDto.OneGame },
        { TaiwanGameLength.OneGame },
    )
    this.gameLength.register(
        TaiwanGameLength.East::class,
        TaiwanGameLengthDto.East::class,
        TaiwanGameLengthDto.East.serializer(),
        { TaiwanGameLengthDto.East },
        { TaiwanGameLength.East },
    )
    this.gameLength.register(
        TaiwanGameLength.TwoWinds::class,
        TaiwanGameLengthDto.TwoWinds::class,
        TaiwanGameLengthDto.TwoWinds.serializer(),
        { TaiwanGameLengthDto.TwoWinds },
        { TaiwanGameLength.TwoWinds },
    )
    this.gameLength.register(
        TaiwanGameLength.FourWinds::class,
        TaiwanGameLengthDto.FourWinds::class,
        TaiwanGameLengthDto.FourWinds.serializer(),
        { TaiwanGameLengthDto.FourWinds },
        { TaiwanGameLength.FourWinds },
    )

    this.dynamicRuleState.register(
        RiichiDynamicState::class,
        RiichiDynamicStateDto::class,
        RiichiDynamicStateDto.serializer(),
        RiichiDynamicState::toRiichiDto,
        RiichiDynamicStateDto::toDomain,
    )

    this.playerRuleState.register(
        RiichiPlayerState::class,
        RiichiPlayerStateDto::class,
        RiichiPlayerStateDto.serializer(),
        RiichiPlayerState::toRiichiDto,
        RiichiPlayerStateDto::toDomain,
    )

    this.discardPile.register(
        RiichiDiscardPile::class,
        RiichiDiscardPileDto::class,
        RiichiDiscardPileDto.serializer(),
        RiichiDiscardPile::toRiichiDto,
        RiichiDiscardPileDto::toDomain,
    )
    this.discardPile.register(
        TaiwanDiscardPile::class,
        TaiwanDiscardPileDto::class,
        TaiwanDiscardPileDto.serializer(),
        TaiwanDiscardPile::toTaiwanDto,
        TaiwanDiscardPileDto::toDomain,
    )

    this.exhaustiveDrawReason.register(
        RiichiExhaustiveDrawReason.Normal::class,
        RiichiExhaustiveDrawReasonDto.Normal::class,
        RiichiExhaustiveDrawReasonDto.Normal.serializer(),
        { RiichiExhaustiveDrawReasonDto.Normal },
        { RiichiExhaustiveDrawReason.Normal },
    )
    this.exhaustiveDrawReason.register(
        RiichiExhaustiveDrawReason.KyuushuKyuuhai::class,
        RiichiExhaustiveDrawReasonDto.KyuushuKyuuhai::class,
        RiichiExhaustiveDrawReasonDto.KyuushuKyuuhai.serializer(),
        { RiichiExhaustiveDrawReasonDto.KyuushuKyuuhai },
        { RiichiExhaustiveDrawReason.KyuushuKyuuhai },
    )
    this.exhaustiveDrawReason.register(
        RiichiExhaustiveDrawReason.SuufonRenda::class,
        RiichiExhaustiveDrawReasonDto.SuufonRenda::class,
        RiichiExhaustiveDrawReasonDto.SuufonRenda.serializer(),
        { RiichiExhaustiveDrawReasonDto.SuufonRenda },
        { RiichiExhaustiveDrawReason.SuufonRenda },
    )
    this.exhaustiveDrawReason.register(
        RiichiExhaustiveDrawReason.SuukanNagare::class,
        RiichiExhaustiveDrawReasonDto.SuukanNagare::class,
        RiichiExhaustiveDrawReasonDto.SuukanNagare.serializer(),
        { RiichiExhaustiveDrawReasonDto.SuukanNagare },
        { RiichiExhaustiveDrawReason.SuukanNagare },
    )
    this.exhaustiveDrawReason.register(
        RiichiExhaustiveDrawReason.SuuchaRiichi::class,
        RiichiExhaustiveDrawReasonDto.SuuchaRiichi::class,
        RiichiExhaustiveDrawReasonDto.SuuchaRiichi.serializer(),
        { RiichiExhaustiveDrawReasonDto.SuuchaRiichi },
        { RiichiExhaustiveDrawReason.SuuchaRiichi },
    )
    this.exhaustiveDrawReason.register(
        RiichiExhaustiveDrawReason.SanchaHou::class,
        RiichiExhaustiveDrawReasonDto.SanchaHou::class,
        RiichiExhaustiveDrawReasonDto.SanchaHou.serializer(),
        { RiichiExhaustiveDrawReasonDto.SanchaHou },
        { RiichiExhaustiveDrawReason.SanchaHou },
    )
}
