package com.doublemoon1119.mahjongcraft.flow.persistence.format.game

import com.doublemoon1119.mahjongcraft.flow.persistence.format.component.ComponentPersistenceRegistry
import com.doublemoon1119.mahjongcraft.logic.base.Hand
import com.doublemoon1119.mahjongcraft.logic.base.IdentifiedTile
import com.doublemoon1119.mahjongcraft.logic.base.Meld
import com.doublemoon1119.mahjongcraft.logic.base.MeldType
import com.doublemoon1119.mahjongcraft.logic.base.MeldTypeId
import kotlin.uuid.Uuid

/**
 * 將 [IdentifiedTile] 轉換成持久化 DTO。
 *
 * @param componentRegistry 牌張組件的保存轉換。
 * @return 包含原模型資料的持久化 DTO。
 */
fun IdentifiedTile.toPersistenceDto(componentRegistry: ComponentPersistenceRegistry): IdentifiedTilePersistenceDto = IdentifiedTilePersistenceDto(id.toString(), tile.toPersistenceDto(), componentRegistry.encode(components))

/**
 * 將 [IdentifiedTilePersistenceDto] 還原成對應的領域模型。
 *
 * @param componentRegistry 牌張組件的保存轉換。
 * @return 還原的領域模型。
 */
fun IdentifiedTilePersistenceDto.toDomain(componentRegistry: ComponentPersistenceRegistry): IdentifiedTile = IdentifiedTile(Uuid.parse(id), tile.toDomain(), componentRegistry.decode(components))

/**
 * 將 [Meld] 轉換成持久化 DTO。
 *
 * @param componentRegistry 牌張組件的保存轉換。
 * @return 包含原模型資料的持久化 DTO。
 */
fun Meld.toPersistenceDto(componentRegistry: ComponentPersistenceRegistry): MeldPersistenceDto = MeldPersistenceDto(
    id = id.toString(),
    components = componentRegistry.encode(components),
    type = type.toPersistenceDto(),
    tiles = tiles.map { it.toPersistenceDto(componentRegistry) },
    sourceTile = sourceTile?.toPersistenceDto(componentRegistry),
    sourceDirection = sourceDirection.toPersistenceDto(),
    publiclyRevealedTileIds = publiclyRevealedTileIds.map(Uuid::toString).toSet(),
    faceDownTileIds = faceDownTileIds.map(Uuid::toString).toSet(),
)

/**
 * 將 [MeldPersistenceDto] 還原成對應的領域模型。
 *
 * @param componentRegistry 牌張組件的保存轉換。
 * @return 還原的領域模型。
 */
fun MeldPersistenceDto.toDomain(componentRegistry: ComponentPersistenceRegistry): Meld = Meld(
    id = Uuid.parse(id),
    components = componentRegistry.decode(components),
    type = type.toDomain(),
    tiles = tiles.map { it.toDomain(componentRegistry) },
    sourceTile = sourceTile?.toDomain(componentRegistry),
    sourceDirection = sourceDirection.toDomain(),
    publiclyRevealedTileIds = publiclyRevealedTileIds.map(Uuid::parse).toSet(),
    faceDownTileIds = faceDownTileIds.map(Uuid::parse).toSet(),
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
 * @param componentRegistry 牌張組件的保存轉換。
 * @return 包含原模型資料的持久化 DTO。
 */
fun Hand.toPersistenceDto(componentRegistry: ComponentPersistenceRegistry): HandPersistenceDto = HandPersistenceDto(
    tiles = tiles.map { it.toPersistenceDto(componentRegistry) },
    melds = melds.map { it.toPersistenceDto(componentRegistry) },
    lastDrawn = lastDrawn?.toPersistenceDto(componentRegistry),
)

/**
 * 將 [HandPersistenceDto] 還原成對應的領域模型。
 *
 * @param componentRegistry 牌張組件的保存轉換。
 * @return 還原的領域模型。
 */
fun HandPersistenceDto.toDomain(componentRegistry: ComponentPersistenceRegistry): Hand = Hand(
    tiles = tiles.map { it.toDomain(componentRegistry) },
    melds = melds.map { it.toDomain(componentRegistry) },
    lastDrawn = lastDrawn?.toDomain(componentRegistry),
)
