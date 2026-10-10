package com.doublemoon1119.mahjongcraft.flow.network.dto.model
import com.doublemoon1119.mahjongcraft.logic.base.RelativeDirection

/**
 * 將領域資料轉換為網路 DTO。
 * @return 轉換後的資料。
 */
fun RelativeDirection.toDto(): RelativeDirectionDto = when (this) {
    RelativeDirection.Left -> RelativeDirectionDto.Left
    RelativeDirection.Across -> RelativeDirectionDto.Across
    RelativeDirection.Right -> RelativeDirectionDto.Right
    RelativeDirection.Self -> RelativeDirectionDto.Self
}

/**
 * 將網路 DTO 還原為領域資料。
 * @return 轉換後的資料。
 */
fun RelativeDirectionDto.toDomain(): RelativeDirection = when (this) {
    RelativeDirectionDto.Left -> RelativeDirection.Left
    RelativeDirectionDto.Across -> RelativeDirection.Across
    RelativeDirectionDto.Right -> RelativeDirection.Right
    RelativeDirectionDto.Self -> RelativeDirection.Self
}
