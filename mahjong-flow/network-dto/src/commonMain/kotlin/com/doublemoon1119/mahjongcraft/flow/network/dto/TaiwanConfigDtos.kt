package com.doublemoon1119.mahjongcraft.flow.network.dto

import com.doublemoon1119.mahjongcraft.logic.rules.taiwan.TaiwanDiscardPile
import com.doublemoon1119.mahjongcraft.logic.rules.taiwan.TaiwanGameLength
import com.doublemoon1119.mahjongcraft.logic.rules.taiwan.TaiwanRuleConfig
import com.doublemoon1119.mahjongcraft.logic.rules.taiwan.TaiwanScoreConfig
import com.doublemoon1119.mahjongcraft.logic.table.DiscardPile
import kotlinx.serialization.Serializable

// ── MahjongRuleConfigDto ──────────────────────────────────────────────────

/** [TaiwanRuleConfig] 的完整網路 DTO。 */
@Serializable
data class TaiwanRuleConfigDto(
    val initialHandSize: Int,
    val deadTileCount: Int,
    val scoreConfig: TaiwanScoreConfigDto,
    val gameLength: GameLengthDto,
    val minimumWinConstraint: Int,
    val isSpectateAllowed: Boolean,
    val minPlayers: Int,
    val maxPlayers: Int,
    val multiRonPolicy: MultiRonPolicyDto,
    val useFlowerTiles: Boolean,
) : MahjongRuleConfigDto

fun TaiwanRuleConfig.toTaiwanDto(): TaiwanRuleConfigDto = TaiwanRuleConfigDto(
    initialHandSize = initialHandSize,
    deadTileCount = deadTileCount,
    scoreConfig = scoreConfig.toTaiwanDto(),
    gameLength = gameLength.toDto(),
    minimumWinConstraint = minimumWinConstraint,
    isSpectateAllowed = isSpectateAllowed,
    minPlayers = minPlayers,
    maxPlayers = maxPlayers,
    multiRonPolicy = multiRonPolicy.toDto(),
    useFlowerTiles = useFlowerTiles,
)

fun TaiwanRuleConfigDto.toDomain(): TaiwanRuleConfig = TaiwanRuleConfig(
    useFlowerTiles = useFlowerTiles,
    minimumWinConstraint = minimumWinConstraint,
    scoreConfig = scoreConfig.toDomain(),
    gameLength = gameLength.toDomain() as TaiwanGameLength,
    isSpectateAllowed = isSpectateAllowed,
    multiRonPolicy = multiRonPolicy.toDomain(),
)

// ── ScoreConfigDto ─────────────────────────────────────────────────────────

/** [TaiwanScoreConfig] 的網路 DTO。 */
@Serializable
data class TaiwanScoreConfigDto(
    val baseScore: Int,
    val pointPerTai: Int,
    val initialScore: Int,
    val bustThreshold: Int?,
) : ScoreConfigDto

fun TaiwanScoreConfig.toTaiwanDto(): TaiwanScoreConfigDto = TaiwanScoreConfigDto(
    baseScore = baseScore,
    pointPerTai = pointPerTai,
    initialScore = initialScore,
    bustThreshold = bustThreshold,
)

fun TaiwanScoreConfigDto.toDomain(): TaiwanScoreConfig = TaiwanScoreConfig(
    baseScore = baseScore,
    pointPerTai = pointPerTai,
    initialScore = initialScore,
    bustThreshold = bustThreshold,
)

// ── GameLengthDto ──────────────────────────────────────────────────────────

/** [TaiwanGameLength] 的網路 DTO。 */
@Serializable
sealed interface TaiwanGameLengthDto : GameLengthDto {
    @Serializable data object OneGame : TaiwanGameLengthDto

    @Serializable data object East : TaiwanGameLengthDto

    @Serializable data object TwoWinds : TaiwanGameLengthDto

    @Serializable data object FourWinds : TaiwanGameLengthDto
}

// ── DiscardPileDto ─────────────────────────────────────────────────────────

/** 台灣麻將沒有立直等額外狀態，直接鏡射 [DiscardPile.DiscardEntry] 基礎欄位即可。 */
@Serializable
data class DiscardEntryDto(val tile: IdentifiedTileDto, val isTaken: Boolean)

/** [TaiwanDiscardPile] 的網路 DTO。 */
@Serializable
data class TaiwanDiscardPileDto(val entries: List<DiscardEntryDto>) : DiscardPileDto

fun TaiwanDiscardPile.toTaiwanDto(): TaiwanDiscardPileDto = TaiwanDiscardPileDto(
    entries = entries.map { DiscardEntryDto(it.tile.toDto(), it.isTaken) },
)

fun TaiwanDiscardPileDto.toDomain(): TaiwanDiscardPile = TaiwanDiscardPile(
    entries.map { DiscardPile.DiscardEntry(it.tile.toDomain(), it.isTaken) },
)
