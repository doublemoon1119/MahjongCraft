package com.doublemoon1119.mahjongcraft.flow.network.dto.command

import com.doublemoon1119.mahjongcraft.flow.common.game.model.GameCommand
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
    @Serializable data object Draw : GameCommandDto

    @Serializable data class Discard(val tileId: String) : GameCommandDto

    @Serializable data class Riichi(val tileId: String) : GameCommandDto

    @Serializable data object Tsumo : GameCommandDto

    @Serializable data class Kan(val kanType: KanTypeDto, val tileId: String) : GameCommandDto

    @Serializable data class RespondToDiscard(val action: GameActionDto) : GameCommandDto

    @Serializable data class RespondToChankan(val action: GameActionDto) : GameCommandDto

    @Serializable data object KyuushuKyuuhai : GameCommandDto
}

fun GameCommand.toDto(): GameCommandDto = when (this) {
    GameCommand.Draw -> GameCommandDto.Draw
    is GameCommand.Discard -> GameCommandDto.Discard(tileId.toString())
    is GameCommand.Riichi -> GameCommandDto.Riichi(tileId.toString())
    GameCommand.Tsumo -> GameCommandDto.Tsumo
    is GameCommand.Kan -> GameCommandDto.Kan(type.toDto(), tileId.toString())
    is GameCommand.RespondToDiscard -> GameCommandDto.RespondToDiscard(action.toDto())
    is GameCommand.RespondToChankan -> GameCommandDto.RespondToChankan(action.toDto())
    GameCommand.KyuushuKyuuhai -> GameCommandDto.KyuushuKyuuhai
}

fun GameCommandDto.toDomain(): GameCommand = when (this) {
    GameCommandDto.Draw -> GameCommand.Draw
    is GameCommandDto.Discard -> GameCommand.Discard(Uuid.parse(tileId))
    is GameCommandDto.Riichi -> GameCommand.Riichi(Uuid.parse(tileId))
    GameCommandDto.Tsumo -> GameCommand.Tsumo
    is GameCommandDto.Kan -> GameCommand.Kan(kanType.toDomain(), Uuid.parse(tileId))
    is GameCommandDto.RespondToDiscard -> GameCommand.RespondToDiscard(action.toDomain())
    is GameCommandDto.RespondToChankan -> GameCommand.RespondToChankan(action.toDomain())
    GameCommandDto.KyuushuKyuuhai -> GameCommand.KyuushuKyuuhai
}
