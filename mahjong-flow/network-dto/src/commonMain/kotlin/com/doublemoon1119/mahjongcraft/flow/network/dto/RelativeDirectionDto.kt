package com.doublemoon1119.mahjongcraft.flow.network.dto

import com.doublemoon1119.mahjongcraft.logic.base.RelativeDirection
import kotlinx.serialization.Serializable

@Serializable
sealed interface RelativeDirectionDto {
    @Serializable data object Left : RelativeDirectionDto

    @Serializable data object Across : RelativeDirectionDto

    @Serializable data object Right : RelativeDirectionDto

    @Serializable data object Self : RelativeDirectionDto
}

fun RelativeDirection.toDto(): RelativeDirectionDto = when (this) {
    RelativeDirection.Left -> RelativeDirectionDto.Left
    RelativeDirection.Across -> RelativeDirectionDto.Across
    RelativeDirection.Right -> RelativeDirectionDto.Right
    RelativeDirection.Self -> RelativeDirectionDto.Self
}

fun RelativeDirectionDto.toDomain(): RelativeDirection = when (this) {
    RelativeDirectionDto.Left -> RelativeDirection.Left
    RelativeDirectionDto.Across -> RelativeDirection.Across
    RelativeDirectionDto.Right -> RelativeDirection.Right
    RelativeDirectionDto.Self -> RelativeDirection.Self
}
