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
 * 的規則模組這件事。序列化用的多型清單改用 [MahjongRuleDtoRegistries] 動態組成，不是編譯期窮舉；
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
object MahjongRuleDtoRegistries {
    val ruleConfig = DtoRegistry<MahjongRuleConfig, MahjongRuleConfigDto>()
    val scoreConfig = DtoRegistry<ScoreConfig, ScoreConfigDto>()
    val gameLength = DtoRegistry<GameLength, GameLengthDto>()
    val dynamicRuleState = DtoRegistry<DynamicRuleState, DynamicRuleStateDto>()
    val playerRuleState = DtoRegistry<PlayerRuleState, PlayerRuleStateDto>()
    val discardPile = DtoRegistry<DiscardPile<*>, DiscardPileDto>()
    val exhaustiveDrawReason = DtoRegistry<ExhaustiveDrawReason, ExhaustiveDrawReasonDto>()
}

/**
 * 依 [MahjongRuleDtoRegistries] 目前已註冊的內容動態組成的 `SerializersModule`——每次
 * `register(...)` 之後都要重新取得（呼叫端通常在 [registerBuiltInRuleConfigDtos] 執行完、
 * 所有第三方規則模組也註冊完畢後，才建構真正要用的 `Json` 實例）。
 */
fun buildMahjongDtoSerializersModule(): SerializersModule = SerializersModule {
    polymorphic(MahjongRuleConfigDto::class) { MahjongRuleDtoRegistries.ruleConfig.registerSubclasses(this) }
    polymorphic(ScoreConfigDto::class) { MahjongRuleDtoRegistries.scoreConfig.registerSubclasses(this) }
    polymorphic(GameLengthDto::class) { MahjongRuleDtoRegistries.gameLength.registerSubclasses(this) }
    polymorphic(DynamicRuleStateDto::class) { MahjongRuleDtoRegistries.dynamicRuleState.registerSubclasses(this) }
    polymorphic(PlayerRuleStateDto::class) { MahjongRuleDtoRegistries.playerRuleState.registerSubclasses(this) }
    polymorphic(DiscardPileDto::class) { MahjongRuleDtoRegistries.discardPile.registerSubclasses(this) }
    polymorphic(ExhaustiveDrawReasonDto::class) { MahjongRuleDtoRegistries.exhaustiveDrawReason.registerSubclasses(this) }
}

fun MahjongRuleConfig.toDto(): MahjongRuleConfigDto = MahjongRuleDtoRegistries.ruleConfig.toDto(this)
fun MahjongRuleConfigDto.toDomain(): MahjongRuleConfig = MahjongRuleDtoRegistries.ruleConfig.toDomain(this)

fun ScoreConfig.toDto(): ScoreConfigDto = MahjongRuleDtoRegistries.scoreConfig.toDto(this)
fun ScoreConfigDto.toDomain(): ScoreConfig = MahjongRuleDtoRegistries.scoreConfig.toDomain(this)

fun GameLength.toDto(): GameLengthDto = MahjongRuleDtoRegistries.gameLength.toDto(this)
fun GameLengthDto.toDomain(): GameLength = MahjongRuleDtoRegistries.gameLength.toDomain(this)

fun DynamicRuleState.toDto(): DynamicRuleStateDto = MahjongRuleDtoRegistries.dynamicRuleState.toDto(this)
fun DynamicRuleStateDto.toDomain(): DynamicRuleState = MahjongRuleDtoRegistries.dynamicRuleState.toDomain(this)

fun PlayerRuleState.toDto(): PlayerRuleStateDto = MahjongRuleDtoRegistries.playerRuleState.toDto(this)
fun PlayerRuleStateDto.toDomain(): PlayerRuleState = MahjongRuleDtoRegistries.playerRuleState.toDomain(this)

fun DiscardPile<*>.toDto(): DiscardPileDto = MahjongRuleDtoRegistries.discardPile.toDto(this)
fun DiscardPileDto.toDomain(): DiscardPile<*> = MahjongRuleDtoRegistries.discardPile.toDomain(this)

fun ExhaustiveDrawReason.toDto(): ExhaustiveDrawReasonDto = MahjongRuleDtoRegistries.exhaustiveDrawReason.toDto(this)
fun ExhaustiveDrawReasonDto.toDomain(): ExhaustiveDrawReason = MahjongRuleDtoRegistries.exhaustiveDrawReason.toDomain(this)
