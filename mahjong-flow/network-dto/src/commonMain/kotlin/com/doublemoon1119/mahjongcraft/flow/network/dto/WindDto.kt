package com.doublemoon1119.mahjongcraft.flow.network.dto

import com.doublemoon1119.mahjongcraft.logic.table.Wind
import kotlinx.serialization.Serializable

/** [Wind] 的網路 DTO。 */
@Serializable
enum class WindDto { EAST, SOUTH, WEST, NORTH }

fun Wind.toDto(): WindDto = when (this) {
    Wind.EAST -> WindDto.EAST
    Wind.SOUTH -> WindDto.SOUTH
    Wind.WEST -> WindDto.WEST
    Wind.NORTH -> WindDto.NORTH
}

fun WindDto.toDomain(): Wind = when (this) {
    WindDto.EAST -> Wind.EAST
    WindDto.SOUTH -> Wind.SOUTH
    WindDto.WEST -> Wind.WEST
    WindDto.NORTH -> Wind.NORTH
}
