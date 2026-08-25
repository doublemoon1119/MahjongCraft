package com.doublemoon1119.mahjongcraft.flow.network.dto.command

import com.doublemoon1119.mahjongcraft.flow.common.game.model.GameCommand
import com.doublemoon1119.mahjongcraft.flow.network.dto.rule.NetworkDtoRegistries
import com.doublemoon1119.mahjongcraft.flow.network.dto.rule.toDomain
import com.doublemoon1119.mahjongcraft.flow.network.dto.rule.toDto
import kotlinx.serialization.Polymorphic
import kotlinx.serialization.Serializable
import kotlin.uuid.Uuid

/**
 * [GameCommand] 的網路 DTO。跟 [GameActionDto] 一樣獨立鏡射成一份 DTO，而不是直接讓
 * `GameCommand` 本身標 `@Serializable`——這個型別樹裡凡是會上線的型別風格一律統一走顯式 DTO +
 * mapper，不要「有些欄位是 DTO、有些欄位是領域型別」的混搭（即使 `:mahjong-flow-common` 本身已經
 * 依賴 Koin、不是「純」模組，這裡仍刻意不直接標註）。
 */
@Serializable
sealed interface GameCommandDto {
    @Serializable data class Extension(@Polymorphic val value: com.doublemoon1119.mahjongcraft.flow.network.dto.rule.ExtensionGameCommandDto) : GameCommandDto

    @Serializable data object Draw : GameCommandDto

    @Serializable data class Discard(val tileId: String) : GameCommandDto

    @Serializable data object Tsumo : GameCommandDto

    @Serializable data class Kan(val kanType: KanTypeDto, val tileId: String) : GameCommandDto

    @Serializable data class RespondToDiscard(val action: GameActionDto) : GameCommandDto

    @Serializable data class RespondToKan(val action: GameActionDto) : GameCommandDto

    @Serializable data class DeclareExhaustiveDraw(val reason: com.doublemoon1119.mahjongcraft.flow.network.dto.rule.ExhaustiveDrawReasonDto) : GameCommandDto
}

fun GameCommand.toDto(registries: NetworkDtoRegistries): GameCommandDto = when (this) {
    is GameCommand.Extension -> GameCommandDto.Extension(value.toDto(registries))
    GameCommand.Draw -> GameCommandDto.Draw
    is GameCommand.Discard -> GameCommandDto.Discard(tileId.toString())
    GameCommand.Tsumo -> GameCommandDto.Tsumo
    is GameCommand.Kan -> GameCommandDto.Kan(type.toDto(), tileId.toString())
    is GameCommand.RespondToDiscard -> GameCommandDto.RespondToDiscard(action.toDto(registries))
    is GameCommand.RespondToKan -> GameCommandDto.RespondToKan(action.toDto(registries))
    is GameCommand.DeclareExhaustiveDraw -> GameCommandDto.DeclareExhaustiveDraw(reason.toDto(registries))
}

fun GameCommandDto.toDomain(registries: NetworkDtoRegistries): GameCommand = when (this) {
    is GameCommandDto.Extension -> GameCommand.Extension(value.toDomain(registries))
    GameCommandDto.Draw -> GameCommand.Draw
    is GameCommandDto.Discard -> GameCommand.Discard(Uuid.parse(tileId))
    GameCommandDto.Tsumo -> GameCommand.Tsumo
    is GameCommandDto.Kan -> GameCommand.Kan(kanType.toDomain(), Uuid.parse(tileId))
    is GameCommandDto.RespondToDiscard -> GameCommand.RespondToDiscard(action.toDomain(registries))
    is GameCommandDto.RespondToKan -> GameCommand.RespondToKan(action.toDomain(registries))
    is GameCommandDto.DeclareExhaustiveDraw -> GameCommand.DeclareExhaustiveDraw(reason.toDomain(registries))
}
