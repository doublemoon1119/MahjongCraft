package com.doublemoon1119.mahjongcraft.flow.persistence.dto.rule

import com.doublemoon1119.mahjongcraft.flow.persistence.dto.core.PersistenceDtoRegistry
import com.doublemoon1119.mahjongcraft.flow.persistence.dto.game.IdentifiedTilePersistenceDto
import com.doublemoon1119.mahjongcraft.flow.persistence.dto.game.RelativeDirectionPersistenceDto
import com.doublemoon1119.mahjongcraft.flow.persistence.dto.game.toDomain
import com.doublemoon1119.mahjongcraft.flow.persistence.dto.game.toPersistenceDto
import com.doublemoon1119.mahjongcraft.logic.base.RelativeDirection
import com.doublemoon1119.mahjongcraft.logic.rules.riichi.PaoLiability
import com.doublemoon1119.mahjongcraft.logic.rules.riichi.PaoYaku
import com.doublemoon1119.mahjongcraft.logic.rules.riichi.RiichiPlayerState
import com.doublemoon1119.mahjongcraft.logic.table.PlayerRuleState
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

/** [RiichiPlayerState] 的完整 persistence DTO。 */
@Serializable
data class RiichiPlayerStatePersistenceDto(
    val riichiTile: IdentifiedTilePersistenceDto?,
    val doubleRiichiTile: IdentifiedTilePersistenceDto?,
    val isIppatsu: Boolean,
    val paoLiability: PaoLiabilityPersistenceDto?,
)

/** 建立已註冊內建日麻玩家規則狀態的 persistence registry。 */
fun buildPlayerRuleStatePersistenceRegistry(): PersistenceDtoRegistry<PlayerRuleState> = PersistenceDtoRegistry<PlayerRuleState>()
    .apply {
        register(
            typeKey = "builtin:riichi_player_state",
            domainClass = RiichiPlayerState::class,
            serializer = RiichiPlayerStatePersistenceDto.serializer(),
            toDto = RiichiPlayerState::toPersistenceDto,
            toDomain = RiichiPlayerStatePersistenceDto::toDomain,
        )
    }

/** 將 [RiichiPlayerState] 轉換成 persistence DTO。 */
private fun RiichiPlayerState.toPersistenceDto(): RiichiPlayerStatePersistenceDto = RiichiPlayerStatePersistenceDto(
    riichiTile = riichiTile?.toPersistenceDto(),
    doubleRiichiTile = doubleRiichiTile?.toPersistenceDto(),
    isIppatsu = isIppatsu,
    paoLiability = paoLiability?.toPersistenceDto(),
)

/** 將日麻玩家規則狀態 persistence DTO 還原成 [RiichiPlayerState]。 */
private fun RiichiPlayerStatePersistenceDto.toDomain(): RiichiPlayerState = RiichiPlayerState(
    riichiTile = riichiTile?.toDomain(),
    doubleRiichiTile = doubleRiichiTile?.toDomain(),
    isIppatsu = isIppatsu,
    paoLiability = paoLiability?.toDomain(),
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
