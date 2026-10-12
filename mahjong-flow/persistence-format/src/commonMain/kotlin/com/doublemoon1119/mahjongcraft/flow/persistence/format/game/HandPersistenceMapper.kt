package com.doublemoon1119.mahjongcraft.flow.persistence.format.game

import com.doublemoon1119.mahjongcraft.logic.base.Hand
import com.doublemoon1119.mahjongcraft.logic.base.IdentifiedTile
import com.doublemoon1119.mahjongcraft.logic.base.Meld
import com.doublemoon1119.mahjongcraft.logic.base.MeldType
import com.doublemoon1119.mahjongcraft.logic.base.MeldTypeId
import com.doublemoon1119.mahjongcraft.logic.base.RelativeDirection
import kotlin.uuid.Uuid

/**
 * 將 [IdentifiedTile] 轉換成持久化 DTO。
 *
 * @return 包含原模型資料的持久化 DTO。
 */
fun IdentifiedTile.toPersistenceDto(): IdentifiedTilePersistenceDto = IdentifiedTilePersistenceDto(id.toString(), tile.toPersistenceDto())

/**
 * 將 [IdentifiedTilePersistenceDto] 還原成對應的領域模型。
 *
 * @return 還原的領域模型。
 */
fun IdentifiedTilePersistenceDto.toDomain(): IdentifiedTile = IdentifiedTile(Uuid.parse(id), tile.toDomain())

/**
 * 將 [Meld] 轉換成持久化 DTO。
 *
 * @return 包含原模型資料的持久化 DTO。
 */
fun Meld.toPersistenceDto(): MeldPersistenceDto = MeldPersistenceDto(
    type = type.toPersistenceDto(),
    tiles = tiles.map(IdentifiedTile::toPersistenceDto),
    sourceTile = sourceTile?.toPersistenceDto(),
    sourceDirection = sourceDirection.toPersistenceDto(),
)

/**
 * 將 [MeldPersistenceDto] 還原成對應的領域模型。
 *
 * @return 還原的領域模型。
 */
fun MeldPersistenceDto.toDomain(): Meld = Meld(
    type = type.toDomain(),
    tiles = tiles.map(IdentifiedTilePersistenceDto::toDomain),
    sourceTile = sourceTile?.toDomain(),
    sourceDirection = sourceDirection.toDomain(),
)

/** 將副露種類轉成存檔 DTO。 */
private fun MeldType.toPersistenceDto(): MeldTypePersistenceDto = when (this) {
    MeldType.CHI -> MeldTypePersistenceDto.Chi
    MeldType.PON -> MeldTypePersistenceDto.Pon
    MeldType.OPEN_KAN -> MeldTypePersistenceDto.OpenKan
    MeldType.CLOSED_KAN -> MeldTypePersistenceDto.ClosedKan
    MeldType.ADDED_KAN -> MeldTypePersistenceDto.AddedKan
    is MeldType.Extension -> MeldTypePersistenceDto.Extension(typeId.toString())
}

/** 將已保存的副露種類還原成領域值。 */
private fun MeldTypePersistenceDto.toDomain(): MeldType = when (this) {
    MeldTypePersistenceDto.Chi -> MeldType.CHI
    MeldTypePersistenceDto.Pon -> MeldType.PON
    MeldTypePersistenceDto.OpenKan -> MeldType.OPEN_KAN
    MeldTypePersistenceDto.ClosedKan -> MeldType.CLOSED_KAN
    MeldTypePersistenceDto.AddedKan -> MeldType.ADDED_KAN
    is MeldTypePersistenceDto.Extension -> MeldType.Extension(MeldTypeId.parse(typeId))
}

/**
 * 將 [Hand] 轉換成持久化 DTO。
 *
 * @return 包含原模型資料的持久化 DTO。
 */
fun Hand.toPersistenceDto(): HandPersistenceDto = HandPersistenceDto(
    tiles = tiles.map(IdentifiedTile::toPersistenceDto),
    melds = melds.map(Meld::toPersistenceDto),
    lastDrawn = lastDrawn?.toPersistenceDto(),
)

/**
 * 將 [HandPersistenceDto] 還原成對應的領域模型。
 *
 * @return 還原的領域模型。
 */
fun HandPersistenceDto.toDomain(): Hand = Hand(
    tiles = tiles.map(IdentifiedTilePersistenceDto::toDomain),
    melds = melds.map(MeldPersistenceDto::toDomain),
    lastDrawn = lastDrawn?.toDomain(),
)

/** 將副露來源的相對方向轉成存檔 DTO。 */
private fun RelativeDirection.toPersistenceDto(): RelativeDirectionPersistenceDto = when (this) {
    RelativeDirection.Left -> RelativeDirectionPersistenceDto.LEFT
    RelativeDirection.Across -> RelativeDirectionPersistenceDto.ACROSS
    RelativeDirection.Right -> RelativeDirectionPersistenceDto.RIGHT
    RelativeDirection.Self -> RelativeDirectionPersistenceDto.SELF
}

/** 將已保存的相對方向還原成領域值。 */
private fun RelativeDirectionPersistenceDto.toDomain(): RelativeDirection = when (this) {
    RelativeDirectionPersistenceDto.LEFT -> RelativeDirection.Left
    RelativeDirectionPersistenceDto.ACROSS -> RelativeDirection.Across
    RelativeDirectionPersistenceDto.RIGHT -> RelativeDirection.Right
    RelativeDirectionPersistenceDto.SELF -> RelativeDirection.Self
}
