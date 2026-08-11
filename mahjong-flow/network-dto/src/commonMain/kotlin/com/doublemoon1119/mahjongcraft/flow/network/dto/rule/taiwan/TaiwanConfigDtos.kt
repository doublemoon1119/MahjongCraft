package com.doublemoon1119.mahjongcraft.flow.network.dto.rule.taiwan

import com.doublemoon1119.mahjongcraft.flow.network.dto.model.IdentifiedTileDto
import com.doublemoon1119.mahjongcraft.flow.network.dto.model.toDomain
import com.doublemoon1119.mahjongcraft.flow.network.dto.model.toDto
import com.doublemoon1119.mahjongcraft.flow.network.dto.rule.DiscardPileDto
import com.doublemoon1119.mahjongcraft.flow.network.dto.rule.GameLengthDto
import com.doublemoon1119.mahjongcraft.flow.network.dto.rule.MahjongRuleConfigDto
import com.doublemoon1119.mahjongcraft.flow.network.dto.rule.MultiRonPolicyDto
import com.doublemoon1119.mahjongcraft.flow.network.dto.rule.NetworkDtoRegistries
import com.doublemoon1119.mahjongcraft.flow.network.dto.rule.ScoreConfigDto
import com.doublemoon1119.mahjongcraft.logic.rules.taiwan.TaiwanDiscardPile
import com.doublemoon1119.mahjongcraft.logic.rules.taiwan.TaiwanGameLength
import com.doublemoon1119.mahjongcraft.logic.rules.taiwan.TaiwanRuleConfig
import com.doublemoon1119.mahjongcraft.logic.rules.taiwan.TaiwanScoreConfig
import com.doublemoon1119.mahjongcraft.logic.table.DiscardPile
import kotlinx.serialization.Serializable
import com.doublemoon1119.mahjongcraft.flow.network.dto.rule.toDomain as toRuleDomain
import com.doublemoon1119.mahjongcraft.flow.network.dto.rule.toDto as toRuleDto

// ── MahjongRuleConfigDto ──────────────────────────────────────────────────

/** [TaiwanRuleConfig] 的完整網路 DTO。 */
@Serializable
data class TaiwanRuleConfigDto(
    val initialHandSize: Int,
    val deadTileCount: Int,
    val scoreConfig: TaiwanScoreConfigDto,
    val gameLength: GameLengthDto,
    val minimumWinConstraint: Int,
    val minPlayers: Int,
    val maxPlayers: Int,
    val multiRonPolicy: MultiRonPolicyDto,
    val useFlowerTiles: Boolean,
) : MahjongRuleConfigDto

fun TaiwanRuleConfig.toTaiwanDto(registries: NetworkDtoRegistries): TaiwanRuleConfigDto = TaiwanRuleConfigDto(
    initialHandSize = initialHandSize,
    deadTileCount = deadTileCount,
    scoreConfig = scoreConfig.toTaiwanDto(),
    gameLength = gameLength.toRuleDto(registries),
    minimumWinConstraint = minimumWinConstraint,
    minPlayers = minPlayers,
    maxPlayers = maxPlayers,
    multiRonPolicy = multiRonPolicy.toRuleDto(),
    useFlowerTiles = useFlowerTiles,
)

fun TaiwanRuleConfigDto.toDomain(registries: NetworkDtoRegistries): TaiwanRuleConfig = TaiwanRuleConfig(
    useFlowerTiles = useFlowerTiles,
    minimumWinConstraint = minimumWinConstraint,
    scoreConfig = scoreConfig.toDomain(),
    gameLength = gameLength.toRuleDomain(registries) as TaiwanGameLength,
    multiRonPolicy = multiRonPolicy.toRuleDomain(),
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
