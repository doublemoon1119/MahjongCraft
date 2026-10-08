package com.doublemoon1119.mahjongcraft.flow.persistence.format.rule

import com.doublemoon1119.mahjongcraft.flow.persistence.format.game.IdentifiedTilePersistenceDto
import com.doublemoon1119.mahjongcraft.flow.persistence.format.game.RelativeDirectionPersistenceDto
import com.doublemoon1119.mahjongcraft.flow.persistence.format.game.toDomain
import com.doublemoon1119.mahjongcraft.flow.persistence.format.game.toPersistenceDto
import com.doublemoon1119.mahjongcraft.logic.base.RelativeDirection
import com.doublemoon1119.mahjongcraft.logic.rules.riichi.PaoLiability
import com.doublemoon1119.mahjongcraft.logic.rules.riichi.PaoYaku
import com.doublemoon1119.mahjongcraft.logic.rules.riichi.RiichiPlayerState
import kotlinx.serialization.Serializable

/** [PaoYaku] 的 persistence DTO。 */
@Serializable
enum class PaoYakuPersistenceDto { DAISANGEN, DAISUUSHII }

/** [PaoLiability] 的完整 persistence DTO。 */
@Serializable
data class PaoLiabilityPersistenceDto(
    val yaku: PaoYakuPersistenceDto,
    val direction: RelativeDirectionPersistenceDto,
)

/**
 * [RiichiPlayerState] 的完整 persistence DTO。
 *
 * 預設值是最常見的狀態（沒有永久振聽、沒有拔北）；編碼時省略等於預設值的欄位，讓每個存檔點都帶有玩家狀態的
 * 精簡牌譜不必重複寫出這些值。
 */
@Serializable
data class RiichiPlayerStatePersistenceDto(
    val riichiTile: IdentifiedTilePersistenceDto?,
    val doubleRiichiTile: IdentifiedTilePersistenceDto?,
    val isIppatsu: Boolean,
    val paoLiability: PaoLiabilityPersistenceDto?,
    val isPermanentlyFuriten: Boolean = false,
    val nukiDoraTiles: List<IdentifiedTilePersistenceDto> = emptyList(),
)

/** 將 [RiichiPlayerState] 轉換成 persistence DTO。 */
fun RiichiPlayerState.toPersistenceDto(): RiichiPlayerStatePersistenceDto = RiichiPlayerStatePersistenceDto(
    riichiTile = riichiTile?.toPersistenceDto(),
    doubleRiichiTile = doubleRiichiTile?.toPersistenceDto(),
    isIppatsu = isIppatsu,
    paoLiability = paoLiability?.toPersistenceDto(),
    isPermanentlyFuriten = isPermanentlyFuriten,
    nukiDoraTiles = nukiDoraTiles.map { it.toPersistenceDto() },
)

/** 將日麻玩家規則狀態 persistence DTO 還原成 [RiichiPlayerState]。 */
fun RiichiPlayerStatePersistenceDto.toDomain(): RiichiPlayerState = RiichiPlayerState(
    riichiTile = riichiTile?.toDomain(),
    doubleRiichiTile = doubleRiichiTile?.toDomain(),
    isIppatsu = isIppatsu,
    paoLiability = paoLiability?.toDomain(),
    isPermanentlyFuriten = isPermanentlyFuriten,
    nukiDoraTiles = nukiDoraTiles.map { it.toDomain() },
)

/** 將 [PaoLiability] 轉換成 persistence DTO。 */
private fun PaoLiability.toPersistenceDto(): PaoLiabilityPersistenceDto = PaoLiabilityPersistenceDto(
    yaku = when (yaku) {
        PaoYaku.Daisangen -> PaoYakuPersistenceDto.DAISANGEN
        PaoYaku.Daisuushii -> PaoYakuPersistenceDto.DAISUUSHII
    },
    direction = direction.toPersistenceDto(),
)

/** 將包牌責任 persistence DTO 還原成 [PaoLiability]。 */
private fun PaoLiabilityPersistenceDto.toDomain(): PaoLiability = PaoLiability(
    yaku = when (yaku) {
        PaoYakuPersistenceDto.DAISANGEN -> PaoYaku.Daisangen
        PaoYakuPersistenceDto.DAISUUSHII -> PaoYaku.Daisuushii
    },
    direction = direction.toDomain(),
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
