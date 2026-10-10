package com.doublemoon1119.mahjongcraft.flow.network.dto.model
import com.doublemoon1119.mahjongcraft.logic.table.Wind

/**
 * 將領域資料轉換為網路 DTO。
 * @return 轉換後的資料。
 */
fun Wind.toDto(): WindDto = when (this) {
    Wind.EAST -> WindDto.EAST
    Wind.SOUTH -> WindDto.SOUTH
    Wind.WEST -> WindDto.WEST
    Wind.NORTH -> WindDto.NORTH
}

/**
 * 將網路 DTO 還原為領域資料。
 * @return 轉換後的資料。
 */
fun WindDto.toDomain(): Wind = when (this) {
    WindDto.EAST -> Wind.EAST
    WindDto.SOUTH -> Wind.SOUTH
    WindDto.WEST -> Wind.WEST
    WindDto.NORTH -> Wind.NORTH
}
