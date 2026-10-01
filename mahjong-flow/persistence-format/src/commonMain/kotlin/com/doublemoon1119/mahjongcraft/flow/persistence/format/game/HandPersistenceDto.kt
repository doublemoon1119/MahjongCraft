package com.doublemoon1119.mahjongcraft.flow.persistence.format.game

import com.doublemoon1119.mahjongcraft.logic.base.Hand
import com.doublemoon1119.mahjongcraft.logic.base.IdentifiedTile
import com.doublemoon1119.mahjongcraft.logic.base.Meld
import com.doublemoon1119.mahjongcraft.logic.base.MeldType
import com.doublemoon1119.mahjongcraft.logic.base.MeldTypeId
import com.doublemoon1119.mahjongcraft.logic.base.RelativeDirection
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlin.uuid.Uuid

/**
 * [IdentifiedTile] 的完整 persistence DTO，保留跨重啟穩定的牌 UUID。
 *
 * @property id 跨重啟穩定的牌 UUID 字串。
 * @property tile 牌張的 persistence DTO。
 */
@Serializable
data class IdentifiedTilePersistenceDto(
    val id: String,
    val tile: TilePersistenceDto,
)

/** [MeldType] 的 persistence DTO。 */
@Serializable
sealed interface MeldTypePersistenceDto {
    /** 吃牌形成的副露種類。 */
    @Serializable
    @SerialName("chi")
    data object Chi : MeldTypePersistenceDto

    /** 碰牌形成的副露種類。 */
    @Serializable
    @SerialName("pon")
    data object Pon : MeldTypePersistenceDto

    /** 明槓形成的副露種類。 */
    @Serializable
    @SerialName("open_kan")
    data object OpenKan : MeldTypePersistenceDto

    /** 暗槓形成的副露種類。 */
    @Serializable
    @SerialName("closed_kan")
    data object ClosedKan : MeldTypePersistenceDto

    /** 加槓形成的副露種類。 */
    @Serializable
    @SerialName("added_kan")
    data object AddedKan : MeldTypePersistenceDto

    /**
     * 擴充規則提供的副露種類。
     *
     * @property typeId 擴充副露種類的穩定識別字串。
     */
    @Serializable
    @SerialName("extension")
    data class Extension(val typeId: String) : MeldTypePersistenceDto
}

/** [RelativeDirection] 的 persistence DTO。 */
@Serializable
enum class RelativeDirectionPersistenceDto { LEFT, ACROSS, RIGHT, SELF }

/**
 * [Meld] 的完整 persistence DTO。
 *
 * @property type 副露種類。
 * @property tiles 副露包含的牌張。
 * @property sourceTile 副露來源牌張；沒有來源牌張時為 null。
 * @property sourceDirection 副露來源相對於目前玩家的方向。
 */
@Serializable
data class MeldPersistenceDto(
    val type: MeldTypePersistenceDto,
    val tiles: List<IdentifiedTilePersistenceDto>,
    val sourceTile: IdentifiedTilePersistenceDto?,
    val sourceDirection: RelativeDirectionPersistenceDto,
)

/**
 * [Hand] 的完整 persistence DTO，保留立牌、副露與最後摸牌的區分。
 *
 * @property tiles 手牌中的立牌。
 * @property melds 手牌中的副露。
 * @property lastDrawn 最近摸入且尚未整理的牌張；沒有時為 null。
 */
@Serializable
data class HandPersistenceDto(
    val tiles: List<IdentifiedTilePersistenceDto>,
    val melds: List<MeldPersistenceDto>,
    val lastDrawn: IdentifiedTilePersistenceDto?,
)

/**
 * 將 [IdentifiedTile] 轉換成 persistence DTO。
 *
 * @return 對應的識別牌 persistence DTO。
 */
fun IdentifiedTile.toPersistenceDto(): IdentifiedTilePersistenceDto = IdentifiedTilePersistenceDto(id.toString(), tile.toPersistenceDto())

/**
 * 將 [IdentifiedTilePersistenceDto] 還原成 [IdentifiedTile]。
 *
 * @return 對應的領域識別牌。
 */
fun IdentifiedTilePersistenceDto.toDomain(): IdentifiedTile = IdentifiedTile(Uuid.parse(id), tile.toDomain())

/**
 * 將 [Meld] 轉換成 persistence DTO。
 *
 * @return 對應的副露 persistence DTO。
 */
fun Meld.toPersistenceDto(): MeldPersistenceDto = MeldPersistenceDto(
    type = type.toPersistenceDto(),
    tiles = tiles.map(IdentifiedTile::toPersistenceDto),
    sourceTile = sourceTile?.toPersistenceDto(),
    sourceDirection = sourceDirection.toPersistenceDto(),
)

/**
 * 將 [MeldPersistenceDto] 還原成 [Meld]。
 *
 * @return 對應的領域副露。
 */
fun MeldPersistenceDto.toDomain(): Meld = Meld(
    type = type.toDomain(),
    tiles = tiles.map(IdentifiedTilePersistenceDto::toDomain),
    sourceTile = sourceTile?.toDomain(),
    sourceDirection = sourceDirection.toDomain(),
)

/**
 * 將副露種類轉成 persistence DTO。
 *
 * @return 對應的副露種類 persistence DTO。
 */
private fun MeldType.toPersistenceDto(): MeldTypePersistenceDto = when (this) {
    MeldType.CHI -> MeldTypePersistenceDto.Chi
    MeldType.PON -> MeldTypePersistenceDto.Pon
    MeldType.OPEN_KAN -> MeldTypePersistenceDto.OpenKan
    MeldType.CLOSED_KAN -> MeldTypePersistenceDto.ClosedKan
    MeldType.ADDED_KAN -> MeldTypePersistenceDto.AddedKan
    is MeldType.Extension -> MeldTypePersistenceDto.Extension(typeId.toString())
}

/**
 * 將 persistence DTO 還原成副露種類。
 *
 * @return 對應的領域副露種類。
 */
private fun MeldTypePersistenceDto.toDomain(): MeldType = when (this) {
    MeldTypePersistenceDto.Chi -> MeldType.CHI
    MeldTypePersistenceDto.Pon -> MeldType.PON
    MeldTypePersistenceDto.OpenKan -> MeldType.OPEN_KAN
    MeldTypePersistenceDto.ClosedKan -> MeldType.CLOSED_KAN
    MeldTypePersistenceDto.AddedKan -> MeldType.ADDED_KAN
    is MeldTypePersistenceDto.Extension -> MeldType.Extension(
        MeldTypeId.parse(typeId),
    )
}

/**
 * 將 [Hand] 轉換成 persistence DTO。
 *
 * @return 對應的手牌 persistence DTO。
 */
fun Hand.toPersistenceDto(): HandPersistenceDto = HandPersistenceDto(
    tiles = tiles.map(IdentifiedTile::toPersistenceDto),
    melds = melds.map(Meld::toPersistenceDto),
    lastDrawn = lastDrawn?.toPersistenceDto(),
)

/**
 * 將 [HandPersistenceDto] 還原成 [Hand]。
 *
 * @return 對應的領域手牌。
 */
fun HandPersistenceDto.toDomain(): Hand = Hand(
    tiles = tiles.map(IdentifiedTilePersistenceDto::toDomain),
    melds = melds.map(MeldPersistenceDto::toDomain),
    lastDrawn = lastDrawn?.toDomain(),
)

/**
 * 將 [RelativeDirection] 轉換成 persistence DTO。
 *
 * @return 對應的相對方向 persistence DTO。
 */
private fun RelativeDirection.toPersistenceDto(): RelativeDirectionPersistenceDto = when (this) {
    RelativeDirection.Left -> RelativeDirectionPersistenceDto.LEFT
    RelativeDirection.Across -> RelativeDirectionPersistenceDto.ACROSS
    RelativeDirection.Right -> RelativeDirectionPersistenceDto.RIGHT
    RelativeDirection.Self -> RelativeDirectionPersistenceDto.SELF
}

/**
 * 將 [RelativeDirectionPersistenceDto] 還原成 [RelativeDirection]。
 *
 * @return 對應的領域相對方向。
 */
private fun RelativeDirectionPersistenceDto.toDomain(): RelativeDirection = when (this) {
    RelativeDirectionPersistenceDto.LEFT -> RelativeDirection.Left
    RelativeDirectionPersistenceDto.ACROSS -> RelativeDirection.Across
    RelativeDirectionPersistenceDto.RIGHT -> RelativeDirection.Right
    RelativeDirectionPersistenceDto.SELF -> RelativeDirection.Self
}
