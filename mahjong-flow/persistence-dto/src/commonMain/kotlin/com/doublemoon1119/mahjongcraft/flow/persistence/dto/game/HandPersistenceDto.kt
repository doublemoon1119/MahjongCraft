package com.doublemoon1119.mahjongcraft.flow.persistence.dto.game

import com.doublemoon1119.mahjongcraft.logic.base.Hand
import com.doublemoon1119.mahjongcraft.logic.base.IdentifiedTile
import com.doublemoon1119.mahjongcraft.logic.base.Meld
import com.doublemoon1119.mahjongcraft.logic.base.MeldType
import com.doublemoon1119.mahjongcraft.logic.base.RelativeDirection
import kotlinx.serialization.Serializable
import kotlin.uuid.Uuid

/** [IdentifiedTile] 的完整 persistence DTO，保留跨重啟穩定的牌 UUID。 */
@Serializable
data class IdentifiedTilePersistenceDto(val id: String, val tile: TilePersistenceDto)

/** [MeldType] 的 persistence DTO。 */
@Serializable
enum class MeldTypePersistenceDto { CHI, PON, OPEN_KAN, CLOSED_KAN, ADDED_KAN }

/** [RelativeDirection] 的 persistence DTO。 */
@Serializable
enum class RelativeDirectionPersistenceDto { LEFT, ACROSS, RIGHT, SELF }

/** [Meld] 的完整 persistence DTO。 */
@Serializable
data class MeldPersistenceDto(
    val type: MeldTypePersistenceDto,
    val tiles: List<IdentifiedTilePersistenceDto>,
    val sourceTile: IdentifiedTilePersistenceDto?,
    val sourceDirection: RelativeDirectionPersistenceDto,
)

/** [Hand] 的完整 persistence DTO，保留立牌、副露與最後摸牌的區分。 */
@Serializable
data class HandPersistenceDto(
    val tiles: List<IdentifiedTilePersistenceDto>,
    val melds: List<MeldPersistenceDto>,
    val lastDrawn: IdentifiedTilePersistenceDto?,
)

/** 將 [IdentifiedTile] 轉換成 persistence DTO。 */
fun IdentifiedTile.toPersistenceDto(): IdentifiedTilePersistenceDto = IdentifiedTilePersistenceDto(id.toString(), tile.toPersistenceDto())

/** 將 [IdentifiedTilePersistenceDto] 還原成 [IdentifiedTile]。 */
fun IdentifiedTilePersistenceDto.toDomain(): IdentifiedTile = IdentifiedTile(Uuid.parse(id), tile.toDomain())

/** 將 [Meld] 轉換成 persistence DTO。 */
fun Meld.toPersistenceDto(): MeldPersistenceDto = MeldPersistenceDto(
    type = MeldTypePersistenceDto.valueOf(type.name),
    tiles = tiles.map(IdentifiedTile::toPersistenceDto),
    sourceTile = sourceTile?.toPersistenceDto(),
    sourceDirection = sourceDirection.toPersistenceDto(),
)

/** 將 [MeldPersistenceDto] 還原成 [Meld]。 */
fun MeldPersistenceDto.toDomain(): Meld = Meld(
    type = MeldType.valueOf(type.name),
    tiles = tiles.map(IdentifiedTilePersistenceDto::toDomain),
    sourceTile = sourceTile?.toDomain(),
    sourceDirection = sourceDirection.toDomain(),
)

/** 將 [Hand] 轉換成 persistence DTO。 */
fun Hand.toPersistenceDto(): HandPersistenceDto = HandPersistenceDto(
    tiles = tiles.map(IdentifiedTile::toPersistenceDto),
    melds = melds.map(Meld::toPersistenceDto),
    lastDrawn = lastDrawn?.toPersistenceDto(),
)

/** 將 [HandPersistenceDto] 還原成 [Hand]。 */
fun HandPersistenceDto.toDomain(): Hand = Hand(
    tiles = tiles.map(IdentifiedTilePersistenceDto::toDomain),
    melds = melds.map(MeldPersistenceDto::toDomain),
    lastDrawn = lastDrawn?.toDomain(),
)

/** 將 [RelativeDirection] 轉換成 persistence DTO。 */
private fun RelativeDirection.toPersistenceDto(): RelativeDirectionPersistenceDto = when (this) {
    RelativeDirection.Left -> RelativeDirectionPersistenceDto.LEFT
    RelativeDirection.Across -> RelativeDirectionPersistenceDto.ACROSS
    RelativeDirection.Right -> RelativeDirectionPersistenceDto.RIGHT
    RelativeDirection.Self -> RelativeDirectionPersistenceDto.SELF
}

/** 將 [RelativeDirectionPersistenceDto] 還原成 [RelativeDirection]。 */
private fun RelativeDirectionPersistenceDto.toDomain(): RelativeDirection = when (this) {
    RelativeDirectionPersistenceDto.LEFT -> RelativeDirection.Left
    RelativeDirectionPersistenceDto.ACROSS -> RelativeDirection.Across
    RelativeDirectionPersistenceDto.RIGHT -> RelativeDirection.Right
    RelativeDirectionPersistenceDto.SELF -> RelativeDirection.Self
}
