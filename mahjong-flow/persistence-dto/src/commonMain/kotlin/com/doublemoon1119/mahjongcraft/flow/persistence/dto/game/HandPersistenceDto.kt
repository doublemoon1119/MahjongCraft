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
sealed interface MeldTypePersistenceDto {
    @Serializable data object Chi : MeldTypePersistenceDto

    @Serializable data object Pon : MeldTypePersistenceDto

    @Serializable data object OpenKan : MeldTypePersistenceDto

    @Serializable data object ClosedKan : MeldTypePersistenceDto

    @Serializable data object AddedKan : MeldTypePersistenceDto

    @Serializable data class Extension(val typeId: String) : MeldTypePersistenceDto
}

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
    type = type.toPersistenceDto(),
    tiles = tiles.map(IdentifiedTile::toPersistenceDto),
    sourceTile = sourceTile?.toPersistenceDto(),
    sourceDirection = sourceDirection.toPersistenceDto(),
)

/** 將 [MeldPersistenceDto] 還原成 [Meld]。 */
fun MeldPersistenceDto.toDomain(): Meld = Meld(
    type = type.toDomain(),
    tiles = tiles.map(IdentifiedTilePersistenceDto::toDomain),
    sourceTile = sourceTile?.toDomain(),
    sourceDirection = sourceDirection.toDomain(),
)

/** 將副露種類轉成 persistence DTO。 */
private fun MeldType.toPersistenceDto(): MeldTypePersistenceDto = when (this) {
    MeldType.CHI -> MeldTypePersistenceDto.Chi
    MeldType.PON -> MeldTypePersistenceDto.Pon
    MeldType.OPEN_KAN -> MeldTypePersistenceDto.OpenKan
    MeldType.CLOSED_KAN -> MeldTypePersistenceDto.ClosedKan
    MeldType.ADDED_KAN -> MeldTypePersistenceDto.AddedKan
    is MeldType.Extension -> MeldTypePersistenceDto.Extension(typeId.toString())
}

/** 將 persistence DTO 還原成副露種類。 */
private fun MeldTypePersistenceDto.toDomain(): MeldType = when (this) {
    MeldTypePersistenceDto.Chi -> MeldType.CHI
    MeldTypePersistenceDto.Pon -> MeldType.PON
    MeldTypePersistenceDto.OpenKan -> MeldType.OPEN_KAN
    MeldTypePersistenceDto.ClosedKan -> MeldType.CLOSED_KAN
    MeldTypePersistenceDto.AddedKan -> MeldType.ADDED_KAN
    is MeldTypePersistenceDto.Extension -> MeldType.Extension(
        com.doublemoon1119.mahjongcraft.logic.base.MeldTypeId.parse(typeId),
    )
}

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
