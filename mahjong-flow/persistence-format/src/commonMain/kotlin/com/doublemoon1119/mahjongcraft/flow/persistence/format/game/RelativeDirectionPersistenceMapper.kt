package com.doublemoon1119.mahjongcraft.flow.persistence.format.game

import com.doublemoon1119.mahjongcraft.logic.base.RelativeDirection

/** 將相對方向轉成存檔 DTO。 */
fun RelativeDirection.toPersistenceDto(): RelativeDirectionPersistenceDto = when (this) {
    RelativeDirection.Left -> RelativeDirectionPersistenceDto.LEFT
    RelativeDirection.Across -> RelativeDirectionPersistenceDto.ACROSS
    RelativeDirection.Right -> RelativeDirectionPersistenceDto.RIGHT
    RelativeDirection.Self -> RelativeDirectionPersistenceDto.SELF
}

/** 將已保存的相對方向還原成領域值。 */
fun RelativeDirectionPersistenceDto.toDomain(): RelativeDirection = when (this) {
    RelativeDirectionPersistenceDto.LEFT -> RelativeDirection.Left
    RelativeDirectionPersistenceDto.ACROSS -> RelativeDirection.Across
    RelativeDirectionPersistenceDto.RIGHT -> RelativeDirection.Right
    RelativeDirectionPersistenceDto.SELF -> RelativeDirection.Self
}
