package com.doublemoon1119.mahjongcraft.flow.network.dto.rule

import com.doublemoon1119.mahjongcraft.flow.network.dto.registry.DtoRegistry
import com.doublemoon1119.mahjongcraft.logic.base.ExhaustiveDrawReason
import com.doublemoon1119.mahjongcraft.logic.config.DynamicRuleState
import com.doublemoon1119.mahjongcraft.logic.config.GameLength
import com.doublemoon1119.mahjongcraft.logic.config.MahjongRuleConfig
import com.doublemoon1119.mahjongcraft.logic.config.ScoreConfig
import com.doublemoon1119.mahjongcraft.logic.table.DiscardPile
import com.doublemoon1119.mahjongcraft.logic.table.PlayerRuleState
import kotlinx.serialization.Polymorphic
import kotlinx.serialization.modules.SerializersModule
import kotlinx.serialization.modules.polymorphic

/**
 * [MahjongRuleConfig] 的網路 DTO——刻意維持開放介面（不是 sealed），對應領域層本身就是開放介面、
 * 讓第三方能透過 [com.doublemoon1119.mahjongcraft.logic.module.MahjongModuleRegistry] 註冊自己
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

/**
 * 領域層開放介面 ↔ DTO 的註冊表集合。建構時全部是空的，比照
 * [com.doublemoon1119.mahjongcraft.logic.module.MahjongModuleRegistryImpl] 的既有精神——
 * [registerBuiltInRuleConfigDtos] 把日麻/台麻已有的實作註冊進來，第三方規則模組要支援序列化，
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

    override fun freeze() {
        ruleConfig.freeze()
        scoreConfig.freeze()
        gameLength.freeze()
        dynamicRuleState.freeze()
        playerRuleState.freeze()
        discardPile.freeze()
        exhaustiveDrawReason.freeze()
    }
}

/**
 * 依 [registries] 目前已註冊的內容動態組成 `SerializersModule`——每次
 * `register(...)` 之後都要重新取得（呼叫端通常在 [registerBuiltInRuleConfigDtos] 執行完、
 * 所有第三方規則模組也註冊完畢後，才建構真正要用的 `Json` 實例）。
 */
fun buildMahjongDtoSerializersModule(registries: NetworkDtoRegistries): SerializersModule = SerializersModule {
    polymorphic(MahjongRuleConfigDto::class) { registries.ruleConfig.registerSubclasses(this) }
    polymorphic(ScoreConfigDto::class) { registries.scoreConfig.registerSubclasses(this) }
    polymorphic(GameLengthDto::class) { registries.gameLength.registerSubclasses(this) }
    polymorphic(DynamicRuleStateDto::class) { registries.dynamicRuleState.registerSubclasses(this) }
    polymorphic(PlayerRuleStateDto::class) { registries.playerRuleState.registerSubclasses(this) }
    polymorphic(DiscardPileDto::class) { registries.discardPile.registerSubclasses(this) }
    polymorphic(ExhaustiveDrawReasonDto::class) { registries.exhaustiveDrawReason.registerSubclasses(this) }
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
