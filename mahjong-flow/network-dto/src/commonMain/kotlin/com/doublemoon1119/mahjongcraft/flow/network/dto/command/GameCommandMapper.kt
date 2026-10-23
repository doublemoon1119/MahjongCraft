package com.doublemoon1119.mahjongcraft.flow.network.dto.command
import com.doublemoon1119.mahjongcraft.flow.common.game.model.GameCommand
import com.doublemoon1119.mahjongcraft.flow.network.dto.model.toDomain
import com.doublemoon1119.mahjongcraft.flow.network.dto.model.toDto
import com.doublemoon1119.mahjongcraft.flow.network.dto.rule.NetworkDtoRegistries
import com.doublemoon1119.mahjongcraft.flow.network.dto.rule.toDomain
import com.doublemoon1119.mahjongcraft.flow.network.dto.rule.toDto
import kotlin.uuid.Uuid

/**
 * 將領域資料轉換為網路 DTO。
 *
 * @param registries 網路 DTO 的動態註冊表集合。
 * @return 轉換後的資料。
 */
fun GameCommand.toDto(registries: NetworkDtoRegistries): GameCommandDto = when (this) {
    is GameCommand.SubmitRuleDecision -> GameCommandDto.SubmitRuleDecision(submission.toDto())
    is GameCommand.Extension -> GameCommandDto.Extension(value.toDto(registries))
    GameCommand.Draw -> GameCommandDto.Draw
    is GameCommand.Discard -> GameCommandDto.Discard(tileId.toString())
    GameCommand.Tsumo -> GameCommandDto.Tsumo
    is GameCommand.Kan -> GameCommandDto.Kan(type.toDto(), tileId.toString())
    is GameCommand.RespondToDiscard -> GameCommandDto.RespondToDiscard(
        action = action.toDto(registries),
        reactionId = reactionId.toString(),
        selectedTileIds = selectedTileIds.map(Uuid::toString),
    )
    is GameCommand.RespondToRobbing -> GameCommandDto.RespondToRobbing(action.toDto(registries))
    is GameCommand.DeclareExhaustiveDraw -> GameCommandDto.DeclareExhaustiveDraw(reason.toDto(registries))
}

/**
 * 將網路 DTO 還原為領域資料。
 *
 * @param registries 網路 DTO 的動態註冊表集合。
 * @return 轉換後的資料。
 */
fun GameCommandDto.toDomain(registries: NetworkDtoRegistries): GameCommand = when (this) {
    is GameCommandDto.SubmitRuleDecision -> GameCommand.SubmitRuleDecision(submission.toDomain())
    is GameCommandDto.Extension -> GameCommand.Extension(value.toDomain(registries))
    GameCommandDto.Draw -> GameCommand.Draw
    is GameCommandDto.Discard -> GameCommand.Discard(Uuid.parse(tileId))
    GameCommandDto.Tsumo -> GameCommand.Tsumo
    is GameCommandDto.Kan -> GameCommand.Kan(kanType.toDomain(), Uuid.parse(tileId))
    is GameCommandDto.RespondToDiscard -> GameCommand.RespondToDiscard(
        action = action.toDomain(registries),
        reactionId = Uuid.parse(reactionId),
        selectedTileIds = selectedTileIds.map(Uuid::parse),
    )
    is GameCommandDto.RespondToRobbing -> GameCommand.RespondToRobbing(action.toDomain(registries))
    is GameCommandDto.DeclareExhaustiveDraw -> GameCommand.DeclareExhaustiveDraw(reason.toDomain(registries))
}
