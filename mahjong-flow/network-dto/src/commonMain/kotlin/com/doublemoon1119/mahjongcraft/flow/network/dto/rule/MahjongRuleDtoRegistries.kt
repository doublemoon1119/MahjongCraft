package com.doublemoon1119.mahjongcraft.flow.network.dto.rule

import com.doublemoon1119.mahjongcraft.flow.common.game.model.ExtensionGameCommand
import com.doublemoon1119.mahjongcraft.flow.network.dto.registry.DtoRegistry
import com.doublemoon1119.mahjongcraft.logic.base.ExhaustiveDrawReason
import com.doublemoon1119.mahjongcraft.logic.base.ExtensionGameAction
import com.doublemoon1119.mahjongcraft.logic.config.DynamicRuleState
import com.doublemoon1119.mahjongcraft.logic.config.GameLength
import com.doublemoon1119.mahjongcraft.logic.config.MahjongRuleConfig
import com.doublemoon1119.mahjongcraft.logic.config.ScoreConfig
import com.doublemoon1119.mahjongcraft.logic.module.MahjongModuleRegistry
import com.doublemoon1119.mahjongcraft.logic.module.MahjongModuleRegistryImpl
import com.doublemoon1119.mahjongcraft.logic.table.DiscardPile
import com.doublemoon1119.mahjongcraft.logic.table.PlayerRuleState
import kotlinx.serialization.Polymorphic
import kotlinx.serialization.modules.SerializersModule
import kotlinx.serialization.modules.SerializersModuleBuilder
import kotlin.reflect.KClass

/**
 * [MahjongRuleConfig] 的網路 DTO——刻意維持開放介面（不是 sealed），對應領域層本身就是開放介面、
 * 讓第三方能透過 [MahjongModuleRegistry] 註冊自己
 * 的規則模組這件事。序列化用的多型清單改用 [NetworkDtoRegistries] 動態組成，不是編譯期窮舉；
 * `@Polymorphic` 標記在介面上，讓任何用到這個型別的欄位都自動走 `SerializersModule` 查表，
 * 不需要在每個使用處各自標註。
 */
@Polymorphic
interface MahjongRuleConfigDto

/** 見 [MahjongRuleConfigDto] 的說明，[ScoreConfig] 的網路 DTO。 */
@Polymorphic
interface ScoreConfigDto

/** 見 [MahjongRuleConfigDto] 的說明，[GameLength] 的網路 DTO。 */
@Polymorphic
interface GameLengthDto

/** 見 [MahjongRuleConfigDto] 的說明，[DynamicRuleState] 的網路 DTO。 */
@Polymorphic
interface DynamicRuleStateDto

/** 見 [MahjongRuleConfigDto] 的說明，[PlayerRuleState] 的網路 DTO。 */
@Polymorphic
interface PlayerRuleStateDto

/** 見 [MahjongRuleConfigDto] 的說明，[DiscardPile] 的網路 DTO。 */
@Polymorphic
interface DiscardPileDto

/** 見 [MahjongRuleConfigDto] 的說明，[ExhaustiveDrawReason] 的網路 DTO。 */
@Polymorphic
interface ExhaustiveDrawReasonDto

/** 見 [MahjongRuleConfigDto] 的說明，[ExtensionGameAction] 的網路 DTO。 */
@Polymorphic
interface ExtensionGameActionDto

/** 見 [MahjongRuleConfigDto] 的說明，[ExtensionGameCommand] 的網路 DTO。 */
@Polymorphic
interface ExtensionGameCommandDto

/**
 * 領域層開放介面 ↔ DTO 的註冊表集合。建構時全部是空的，比照 [MahjongModuleRegistryImpl] 的既有精神——
 * [com.doublemoon1119.mahjongcraft.flow.network.dto.registry.registerBuiltInRuleConfigDtos] 把日麻/台麻已有的實作註冊進來，第三方規則模組要支援序列化，
 * 一樣呼叫對應 registry 的 `register(...)`，不需要修改這個檔案。
 */
interface NetworkDtoRegistries {
    /** 規則配置 DTO registry。 */
    val ruleConfig: DtoRegistry<MahjongRuleConfig, MahjongRuleConfigDto>

    /** 計分配置 DTO registry。 */
    val scoreConfig: DtoRegistry<ScoreConfig, ScoreConfigDto>

    /** 遊戲長度 DTO registry。 */
    val gameLength: DtoRegistry<GameLength, GameLengthDto>

    /** 動態牌桌狀態 DTO registry。 */
    val dynamicRuleState: DtoRegistry<DynamicRuleState, DynamicRuleStateDto>

    /** 玩家規則狀態 DTO registry。 */
    val playerRuleState: DtoRegistry<PlayerRuleState, PlayerRuleStateDto>

    /** 牌河 DTO registry。 */
    val discardPile: DtoRegistry<DiscardPile<*>, DiscardPileDto>

    /** 流局原因 DTO registry。 */
    val exhaustiveDrawReason: DtoRegistry<ExhaustiveDrawReason, ExhaustiveDrawReasonDto>

    /** 擴充遊戲動作 DTO registry。 */
    val extensionGameAction: DtoRegistry<ExtensionGameAction, ExtensionGameActionDto>

    /** 擴充遊戲命令 DTO registry。 */
    val extensionGameCommand: DtoRegistry<ExtensionGameCommand, ExtensionGameCommandDto>

    /** 凍結所有 registry；凍結後不得新增或覆寫 mapper。 */
    fun freeze()
}

/** MahjongCraft runtime 目前使用的 network DTO registry 集合。 */
class DefaultNetworkDtoRegistries : NetworkDtoRegistries {
    override val ruleConfig = DtoRegistry<MahjongRuleConfig, MahjongRuleConfigDto>()
    override val scoreConfig = DtoRegistry<ScoreConfig, ScoreConfigDto>()
    override val gameLength = DtoRegistry<GameLength, GameLengthDto>()
    override val dynamicRuleState = DtoRegistry<DynamicRuleState, DynamicRuleStateDto>()
    override val playerRuleState = DtoRegistry<PlayerRuleState, PlayerRuleStateDto>()
    override val discardPile = DtoRegistry<DiscardPile<*>, DiscardPileDto>()
    override val exhaustiveDrawReason = DtoRegistry<ExhaustiveDrawReason, ExhaustiveDrawReasonDto>()
    override val extensionGameAction = DtoRegistry<ExtensionGameAction, ExtensionGameActionDto>()
    override val extensionGameCommand = DtoRegistry<ExtensionGameCommand, ExtensionGameCommandDto>()

    override fun freeze() {
        ruleConfig.freeze()
        scoreConfig.freeze()
        gameLength.freeze()
        dynamicRuleState.freeze()
        playerRuleState.freeze()
        discardPile.freeze()
        exhaustiveDrawReason.freeze()
        extensionGameAction.freeze()
        extensionGameCommand.freeze()
    }
}

/**
 * 建立以 [registries] 為動態來源的 `SerializersModule`。模組本身可以早於 extension bootstrap
 * 建立；每次 polymorphic 編解碼都會查詢同一個 registry，因此後續完成的內建與第三方 DTO
 * 註冊會立即可見，不必重建 `Json`。
 */
fun buildMahjongDtoSerializersModule(registries: NetworkDtoRegistries): SerializersModule = SerializersModule {
    dynamicPolymorphic(MahjongRuleConfigDto::class, registries.ruleConfig)
    dynamicPolymorphic(ScoreConfigDto::class, registries.scoreConfig)
    dynamicPolymorphic(GameLengthDto::class, registries.gameLength)
    dynamicPolymorphic(DynamicRuleStateDto::class, registries.dynamicRuleState)
    dynamicPolymorphic(PlayerRuleStateDto::class, registries.playerRuleState)
    dynamicPolymorphic(DiscardPileDto::class, registries.discardPile)
    dynamicPolymorphic(ExhaustiveDrawReasonDto::class, registries.exhaustiveDrawReason)
    dynamicPolymorphic(ExtensionGameActionDto::class, registries.extensionGameAction)
    dynamicPolymorphic(ExtensionGameCommandDto::class, registries.extensionGameCommand)
}

/**
 * 建立會在每次編解碼時查詢 [registry] 的 polymorphic provider，而非在 [SerializersModule]
 * 建立當下複製靜態 subclass 清單。
 */
private fun <Dto : Any> SerializersModuleBuilder.dynamicPolymorphic(
    baseClass: KClass<Dto>,
    registry: DtoRegistry<*, Dto>,
) {
    polymorphicDefaultSerializer(baseClass) { dto -> registry.serializerFor(dto) }
    polymorphicDefaultDeserializer(baseClass) { serialName -> serialName?.let(registry::serializerFor) }
}

fun MahjongRuleConfig.toDto(registries: NetworkDtoRegistries): MahjongRuleConfigDto = registries.ruleConfig.toDto(this)
fun MahjongRuleConfigDto.toDomain(registries: NetworkDtoRegistries): MahjongRuleConfig = registries.ruleConfig.toDomain(this)

fun ScoreConfig.toDto(registries: NetworkDtoRegistries): ScoreConfigDto = registries.scoreConfig.toDto(this)
fun ScoreConfigDto.toDomain(registries: NetworkDtoRegistries): ScoreConfig = registries.scoreConfig.toDomain(this)

fun GameLength.toDto(registries: NetworkDtoRegistries): GameLengthDto = registries.gameLength.toDto(this)
fun GameLengthDto.toDomain(registries: NetworkDtoRegistries): GameLength = registries.gameLength.toDomain(this)

fun DynamicRuleState.toDto(registries: NetworkDtoRegistries): DynamicRuleStateDto = registries.dynamicRuleState.toDto(this)

fun DynamicRuleStateDto.toDomain(registries: NetworkDtoRegistries): DynamicRuleState = registries.dynamicRuleState.toDomain(this)

fun PlayerRuleState.toDto(registries: NetworkDtoRegistries): PlayerRuleStateDto = registries.playerRuleState.toDto(this)
fun PlayerRuleStateDto.toDomain(registries: NetworkDtoRegistries): PlayerRuleState = registries.playerRuleState.toDomain(this)

fun DiscardPile<*>.toDto(registries: NetworkDtoRegistries): DiscardPileDto = registries.discardPile.toDto(this)
fun DiscardPileDto.toDomain(registries: NetworkDtoRegistries): DiscardPile<*> = registries.discardPile.toDomain(this)

fun ExhaustiveDrawReason.toDto(registries: NetworkDtoRegistries): ExhaustiveDrawReasonDto = registries.exhaustiveDrawReason.toDto(this)

fun ExhaustiveDrawReasonDto.toDomain(registries: NetworkDtoRegistries): ExhaustiveDrawReason = registries.exhaustiveDrawReason.toDomain(this)

/** 將擴充遊戲動作轉換成已註冊的網路 DTO。 */
fun ExtensionGameAction.toDto(registries: NetworkDtoRegistries): ExtensionGameActionDto = registries.extensionGameAction.toDto(this)

/** 將已註冊的擴充遊戲動作 DTO 還原成領域物件。 */
fun ExtensionGameActionDto.toDomain(registries: NetworkDtoRegistries): ExtensionGameAction = registries.extensionGameAction.toDomain(this)

/** 將擴充遊戲命令轉換成已註冊的網路 DTO。 */
fun ExtensionGameCommand.toDto(registries: NetworkDtoRegistries): ExtensionGameCommandDto = registries.extensionGameCommand.toDto(this)

/** 將已註冊的擴充遊戲命令 DTO 還原成領域物件。 */
fun ExtensionGameCommandDto.toDomain(registries: NetworkDtoRegistries): ExtensionGameCommand = registries.extensionGameCommand.toDomain(this)
