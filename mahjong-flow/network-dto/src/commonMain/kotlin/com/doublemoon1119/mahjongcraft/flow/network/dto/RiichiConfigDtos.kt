package com.doublemoon1119.mahjongcraft.flow.network.dto

import com.doublemoon1119.mahjongcraft.logic.rules.riichi.PaoLiability
import com.doublemoon1119.mahjongcraft.logic.rules.riichi.PaoYaku
import com.doublemoon1119.mahjongcraft.logic.rules.riichi.RiichiDiscardEntry
import com.doublemoon1119.mahjongcraft.logic.rules.riichi.RiichiDiscardPile
import com.doublemoon1119.mahjongcraft.logic.rules.riichi.RiichiDynamicState
import com.doublemoon1119.mahjongcraft.logic.rules.riichi.RiichiGameLength
import com.doublemoon1119.mahjongcraft.logic.rules.riichi.RiichiPlayerState
import com.doublemoon1119.mahjongcraft.logic.rules.riichi.RiichiRuleConfig
import com.doublemoon1119.mahjongcraft.logic.rules.riichi.RiichiScoreConfig
import kotlinx.serialization.Serializable

// ── MahjongRuleConfigDto ──────────────────────────────────────────────────

/** [RiichiRuleConfig] 的完整網路 DTO。 */
@Serializable
data class RiichiRuleConfigDto(
    val initialHandSize: Int,
    val deadTileCount: Int,
    val scoreConfig: RiichiScoreConfigDto,
    val gameLength: GameLengthDto,
    val minimumWinConstraint: Int,
    val isSpectateAllowed: Boolean,
    val minPlayers: Int,
    val maxPlayers: Int,
    val multiRonPolicy: MultiRonPolicyDto,
    val redDoraCount: Int,
    val allowOpenTanyao: Boolean,
    val useLocalYaku: Boolean,
) : MahjongRuleConfigDto

fun RiichiRuleConfig.toRiichiDto(): RiichiRuleConfigDto = RiichiRuleConfigDto(
    initialHandSize = initialHandSize,
    deadTileCount = deadTileCount,
    scoreConfig = scoreConfig.toRiichiDto(),
    gameLength = gameLength.toDto(),
    minimumWinConstraint = minimumWinConstraint,
    isSpectateAllowed = isSpectateAllowed,
    minPlayers = minPlayers,
    maxPlayers = maxPlayers,
    multiRonPolicy = multiRonPolicy.toDto(),
    redDoraCount = redDoraCount,
    allowOpenTanyao = allowOpenTanyao,
    useLocalYaku = useLocalYaku,
)

fun RiichiRuleConfigDto.toDomain(): RiichiRuleConfig = RiichiRuleConfig(
    redDoraCount = redDoraCount,
    allowOpenTanyao = allowOpenTanyao,
    useLocalYaku = useLocalYaku,
    minimumWinConstraint = minimumWinConstraint,
    scoreConfig = scoreConfig.toDomain(),
    gameLength = gameLength.toDomain() as RiichiGameLength,
    isSpectateAllowed = isSpectateAllowed,
    multiRonPolicy = multiRonPolicy.toDomain(),
)

// ── ScoreConfigDto ─────────────────────────────────────────────────────────

/** [RiichiScoreConfig] 的網路 DTO。 */
@Serializable
data class RiichiScoreConfigDto(
    val initialScore: Int,
    val bustThreshold: Int?,
    val minPointsToWin: Int,
    val notenPenaltyUnit: Int,
) : ScoreConfigDto

fun RiichiScoreConfig.toRiichiDto(): RiichiScoreConfigDto = RiichiScoreConfigDto(
    initialScore = initialScore,
    bustThreshold = bustThreshold,
    minPointsToWin = minPointsToWin,
    notenPenaltyUnit = notenPenaltyUnit,
)

fun RiichiScoreConfigDto.toDomain(): RiichiScoreConfig = RiichiScoreConfig(
    initialScore = initialScore,
    bustThreshold = bustThreshold,
    minPointsToWin = minPointsToWin,
    notenPenaltyUnit = notenPenaltyUnit,
)

// ── GameLengthDto ──────────────────────────────────────────────────────────

/** [RiichiGameLength] 的網路 DTO。 */
@Serializable
sealed interface RiichiGameLengthDto : GameLengthDto {
    @Serializable data object OneGame : RiichiGameLengthDto

    @Serializable data object East : RiichiGameLengthDto

    @Serializable data object TwoWinds : RiichiGameLengthDto
}

// ── DynamicRuleStateDto ────────────────────────────────────────────────────

/** [RiichiDynamicState] 的網路 DTO。 */
@Serializable
data class RiichiDynamicStateDto(val riichiStickCount: Int) : DynamicRuleStateDto

fun RiichiDynamicState.toRiichiDto(): RiichiDynamicStateDto = RiichiDynamicStateDto(riichiStickCount)
fun RiichiDynamicStateDto.toDomain(): RiichiDynamicState = RiichiDynamicState(riichiStickCount)

// ── PlayerRuleStateDto ─────────────────────────────────────────────────────

/** [PaoYaku] 的網路 DTO。 */
@Serializable
enum class PaoYakuDto { Daisangen, Daisuushii }

/** [PaoLiability] 的網路 DTO。 */
@Serializable
data class PaoLiabilityDto(val yaku: PaoYakuDto, val direction: RelativeDirectionDto)

fun PaoLiability.toDto(): PaoLiabilityDto = PaoLiabilityDto(
    yaku = when (yaku) {
        PaoYaku.Daisangen -> PaoYakuDto.Daisangen
        PaoYaku.Daisuushii -> PaoYakuDto.Daisuushii
    },
    direction = direction.toDto(),
)

fun PaoLiabilityDto.toDomain(): PaoLiability = PaoLiability(
    yaku = when (yaku) {
        PaoYakuDto.Daisangen -> PaoYaku.Daisangen
        PaoYakuDto.Daisuushii -> PaoYaku.Daisuushii
    },
    direction = direction.toDomain(),
)

/** [RiichiPlayerState] 的網路 DTO。 */
@Serializable
data class RiichiPlayerStateDto(
    val riichiTile: IdentifiedTileDto?,
    val doubleRiichiTile: IdentifiedTileDto?,
    val isIppatsu: Boolean,
    val paoLiability: PaoLiabilityDto?,
) : PlayerRuleStateDto

fun RiichiPlayerState.toRiichiDto(): RiichiPlayerStateDto = RiichiPlayerStateDto(
    riichiTile = riichiTile?.toDto(),
    doubleRiichiTile = doubleRiichiTile?.toDto(),
    isIppatsu = isIppatsu,
    paoLiability = paoLiability?.toDto(),
)

fun RiichiPlayerStateDto.toDomain(): RiichiPlayerState = RiichiPlayerState(
    riichiTile = riichiTile?.toDomain(),
    doubleRiichiTile = doubleRiichiTile?.toDomain(),
    isIppatsu = isIppatsu,
    paoLiability = paoLiability?.toDomain(),
)

// ── DiscardPileDto ─────────────────────────────────────────────────────────

/** [RiichiDiscardEntry] 的網路 DTO。 */
@Serializable
data class RiichiDiscardEntryDto(val tile: IdentifiedTileDto, val isRiichi: Boolean, val isTaken: Boolean)

/** [RiichiDiscardPile] 的網路 DTO。 */
@Serializable
data class RiichiDiscardPileDto(val entries: List<RiichiDiscardEntryDto>) : DiscardPileDto

fun RiichiDiscardPile.toRiichiDto(): RiichiDiscardPileDto = RiichiDiscardPileDto(
    entries = entries.map { RiichiDiscardEntryDto(it.tile.toDto(), it.isRiichi, it.isTaken) },
)

fun RiichiDiscardPileDto.toDomain(): RiichiDiscardPile = RiichiDiscardPile(
    entries.map { RiichiDiscardEntry(it.tile.toDomain(), it.isRiichi, it.isTaken) },
)

// ── ExhaustiveDrawReasonDto ────────────────────────────────────────────────

/** [RiichiExhaustiveDrawReason] 的網路 DTO。 */
@Serializable
sealed interface RiichiExhaustiveDrawReasonDto : ExhaustiveDrawReasonDto {
    @Serializable data object Normal : RiichiExhaustiveDrawReasonDto

    @Serializable data object KyuushuKyuuhai : RiichiExhaustiveDrawReasonDto

    @Serializable data object SuufonRenda : RiichiExhaustiveDrawReasonDto

    @Serializable data object SuukanNagare : RiichiExhaustiveDrawReasonDto

    @Serializable data object SuuchaRiichi : RiichiExhaustiveDrawReasonDto

    @Serializable data object SanchaHou : RiichiExhaustiveDrawReasonDto
}
