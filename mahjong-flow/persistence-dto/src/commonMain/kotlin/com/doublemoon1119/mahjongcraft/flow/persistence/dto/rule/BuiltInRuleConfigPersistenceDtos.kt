package com.doublemoon1119.mahjongcraft.flow.persistence.dto.rule

import com.doublemoon1119.mahjongcraft.flow.persistence.dto.core.PersistenceDtoRegistry
import com.doublemoon1119.mahjongcraft.logic.config.MahjongRuleConfig
import com.doublemoon1119.mahjongcraft.logic.config.MultiRonPolicy
import com.doublemoon1119.mahjongcraft.logic.config.RonResolution
import com.doublemoon1119.mahjongcraft.logic.rules.riichi.RiichiGameLength
import com.doublemoon1119.mahjongcraft.logic.rules.riichi.RiichiRuleConfig
import com.doublemoon1119.mahjongcraft.logic.rules.riichi.RiichiScoreConfig
import com.doublemoon1119.mahjongcraft.logic.rules.taiwan.TaiwanGameLength
import com.doublemoon1119.mahjongcraft.logic.rules.taiwan.TaiwanRuleConfig
import com.doublemoon1119.mahjongcraft.logic.rules.taiwan.TaiwanScoreConfig
import kotlinx.serialization.Serializable

/** [RonResolution] 的 persistence DTO。 */
@Serializable
enum class RonResolutionPersistenceDto {
    /** 僅最近的玩家榮和。 */
    NEAREST_WINNER,

    /** 所有符合資格的玩家榮和。 */
    ALL_WINNERS,

    /** 判定途中流局。 */
    ABORTIVE_DRAW,
}

/** [MultiRonPolicy] 的 persistence DTO。 */
@Serializable
data class MultiRonPolicyPersistenceDto(
    val doubleRonResolution: RonResolutionPersistenceDto,
    val tripleRonResolution: RonResolutionPersistenceDto,
)

/** [RiichiGameLength] 的 persistence DTO。 */
@Serializable
enum class RiichiGameLengthPersistenceDto {
    /** 一局。 */
    ONE_GAME,

    /** 東風戰。 */
    EAST,

    /** 東南戰。 */
    TWO_WINDS,
}

/** [RiichiRuleConfig] 的完整 persistence DTO。 */
@Serializable
data class RiichiRuleConfigPersistenceDto(
    val redDoraCount: Int,
    val allowOpenTanyao: Boolean,
    val useLocalYaku: Boolean,
    val minimumWinConstraint: Int,
    val initialScore: Int,
    val bustThreshold: Int?,
    val minPointsToWin: Int,
    val notenPenaltyUnit: Int,
    val gameLength: RiichiGameLengthPersistenceDto,
    val multiRonPolicy: MultiRonPolicyPersistenceDto,
)

/** [TaiwanGameLength] 的 persistence DTO。 */
@Serializable
enum class TaiwanGameLengthPersistenceDto {
    /** 一局。 */
    ONE_GAME,

    /** 東風戰。 */
    EAST,

    /** 東南戰。 */
    TWO_WINDS,

    /** 東南西北戰。 */
    FOUR_WINDS,
}

/** [TaiwanRuleConfig] 的完整 persistence DTO。 */
@Serializable
data class TaiwanRuleConfigPersistenceDto(
    val useFlowerTiles: Boolean,
    val minimumWinConstraint: Int,
    val baseScore: Int,
    val pointPerTai: Int,
    val initialScore: Int,
    val bustThreshold: Int?,
    val gameLength: TaiwanGameLengthPersistenceDto,
    val multiRonPolicy: MultiRonPolicyPersistenceDto,
)

/** 建立已註冊內建日麻與台麻規則配置的 persistence registry。 */
fun buildRuleConfigPersistenceRegistry(): PersistenceDtoRegistry<MahjongRuleConfig> = PersistenceDtoRegistry<MahjongRuleConfig>()
    .apply {
        register(
            typeKey = "builtin:riichi_rule_config",
            domainClass = RiichiRuleConfig::class,
            serializer = RiichiRuleConfigPersistenceDto.serializer(),
            toDto = RiichiRuleConfig::toPersistenceDto,
            toDomain = RiichiRuleConfigPersistenceDto::toDomain,
        )
        register(
            typeKey = "builtin:taiwan_rule_config",
            domainClass = TaiwanRuleConfig::class,
            serializer = TaiwanRuleConfigPersistenceDto.serializer(),
            toDto = TaiwanRuleConfig::toPersistenceDto,
            toDomain = TaiwanRuleConfigPersistenceDto::toDomain,
        )
    }

/** 將日麻規則配置轉換成 persistence DTO。 */
private fun RiichiRuleConfig.toPersistenceDto(): RiichiRuleConfigPersistenceDto = RiichiRuleConfigPersistenceDto(
    redDoraCount = redDoraCount,
    allowOpenTanyao = allowOpenTanyao,
    useLocalYaku = useLocalYaku,
    minimumWinConstraint = minimumWinConstraint,
    initialScore = scoreConfig.initialScore,
    bustThreshold = scoreConfig.bustThreshold,
    minPointsToWin = scoreConfig.minPointsToWin,
    notenPenaltyUnit = scoreConfig.notenPenaltyUnit,
    gameLength = gameLength.toPersistenceDto(),
    multiRonPolicy = multiRonPolicy.toPersistenceDto(),
)

/** 將日麻規則 persistence DTO 還原成領域配置。 */
private fun RiichiRuleConfigPersistenceDto.toDomain(): RiichiRuleConfig = RiichiRuleConfig(
    redDoraCount = redDoraCount,
    allowOpenTanyao = allowOpenTanyao,
    useLocalYaku = useLocalYaku,
    minimumWinConstraint = minimumWinConstraint,
    scoreConfig = RiichiScoreConfig(initialScore, bustThreshold, minPointsToWin, notenPenaltyUnit),
    gameLength = gameLength.toDomain(),
    multiRonPolicy = multiRonPolicy.toDomain(),
)

/** 將台麻規則配置轉換成 persistence DTO。 */
private fun TaiwanRuleConfig.toPersistenceDto(): TaiwanRuleConfigPersistenceDto = TaiwanRuleConfigPersistenceDto(
    useFlowerTiles = useFlowerTiles,
    minimumWinConstraint = minimumWinConstraint,
    baseScore = scoreConfig.baseScore,
    pointPerTai = scoreConfig.pointPerTai,
    initialScore = scoreConfig.initialScore,
    bustThreshold = scoreConfig.bustThreshold,
    gameLength = gameLength.toPersistenceDto(),
    multiRonPolicy = multiRonPolicy.toPersistenceDto(),
)

/** 將台麻規則 persistence DTO 還原成領域配置。 */
private fun TaiwanRuleConfigPersistenceDto.toDomain(): TaiwanRuleConfig = TaiwanRuleConfig(
    useFlowerTiles = useFlowerTiles,
    minimumWinConstraint = minimumWinConstraint,
    scoreConfig = TaiwanScoreConfig(baseScore, pointPerTai, initialScore, bustThreshold),
    gameLength = gameLength.toDomain(),
    multiRonPolicy = multiRonPolicy.toDomain(),
)

/** 將一炮多響設定轉換成 persistence DTO。 */
private fun MultiRonPolicy.toPersistenceDto(): MultiRonPolicyPersistenceDto = MultiRonPolicyPersistenceDto(
    doubleRonResolution = doubleRonResolution.toPersistenceDto(),
    tripleRonResolution = tripleRonResolution.toPersistenceDto(),
)

/** 將一炮多響 persistence DTO 還原成領域設定。 */
private fun MultiRonPolicyPersistenceDto.toDomain(): MultiRonPolicy = MultiRonPolicy(
    doubleRonResolution = doubleRonResolution.toDomain(),
    tripleRonResolution = tripleRonResolution.toDomain(),
)

/** 將榮和結算方式轉換成 persistence DTO。 */
private fun RonResolution.toPersistenceDto(): RonResolutionPersistenceDto = RonResolutionPersistenceDto.valueOf(name)

/** 將榮和結算 persistence DTO 還原成領域設定。 */
private fun RonResolutionPersistenceDto.toDomain(): RonResolution = RonResolution.valueOf(name)

/** 將日麻對局長度轉換成 persistence DTO。 */
private fun RiichiGameLength.toPersistenceDto(): RiichiGameLengthPersistenceDto = when (this) {
    RiichiGameLength.OneGame -> RiichiGameLengthPersistenceDto.ONE_GAME
    RiichiGameLength.East -> RiichiGameLengthPersistenceDto.EAST
    RiichiGameLength.TwoWinds -> RiichiGameLengthPersistenceDto.TWO_WINDS
}

/** 將日麻對局長度 persistence DTO 還原成領域設定。 */
private fun RiichiGameLengthPersistenceDto.toDomain(): RiichiGameLength = when (this) {
    RiichiGameLengthPersistenceDto.ONE_GAME -> RiichiGameLength.OneGame
    RiichiGameLengthPersistenceDto.EAST -> RiichiGameLength.East
    RiichiGameLengthPersistenceDto.TWO_WINDS -> RiichiGameLength.TwoWinds
}

/** 將台麻對局長度轉換成 persistence DTO。 */
private fun TaiwanGameLength.toPersistenceDto(): TaiwanGameLengthPersistenceDto = when (this) {
    TaiwanGameLength.OneGame -> TaiwanGameLengthPersistenceDto.ONE_GAME
    TaiwanGameLength.East -> TaiwanGameLengthPersistenceDto.EAST
    TaiwanGameLength.TwoWinds -> TaiwanGameLengthPersistenceDto.TWO_WINDS
    TaiwanGameLength.FourWinds -> TaiwanGameLengthPersistenceDto.FOUR_WINDS
}

/** 將台麻對局長度 persistence DTO 還原成領域設定。 */
private fun TaiwanGameLengthPersistenceDto.toDomain(): TaiwanGameLength = when (this) {
    TaiwanGameLengthPersistenceDto.ONE_GAME -> TaiwanGameLength.OneGame
    TaiwanGameLengthPersistenceDto.EAST -> TaiwanGameLength.East
    TaiwanGameLengthPersistenceDto.TWO_WINDS -> TaiwanGameLength.TwoWinds
    TaiwanGameLengthPersistenceDto.FOUR_WINDS -> TaiwanGameLength.FourWinds
}
