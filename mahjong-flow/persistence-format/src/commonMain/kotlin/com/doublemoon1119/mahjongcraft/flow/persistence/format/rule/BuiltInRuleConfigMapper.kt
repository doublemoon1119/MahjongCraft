package com.doublemoon1119.mahjongcraft.flow.persistence.format.rule

import com.doublemoon1119.mahjongcraft.logic.config.MultiRonPolicy
import com.doublemoon1119.mahjongcraft.logic.config.RonResolution
import com.doublemoon1119.mahjongcraft.logic.rules.riichi.RiichiGameLength
import com.doublemoon1119.mahjongcraft.logic.rules.riichi.RiichiRuleConfig
import com.doublemoon1119.mahjongcraft.logic.rules.riichi.RiichiScoreConfig
import com.doublemoon1119.mahjongcraft.logic.rules.riichi.threeplayer.ThreePlayerRiichiRuleConfig
import com.doublemoon1119.mahjongcraft.logic.rules.taiwan.TaiwanGameLength
import com.doublemoon1119.mahjongcraft.logic.rules.taiwan.TaiwanRuleConfig
import com.doublemoon1119.mahjongcraft.logic.rules.taiwan.TaiwanScoreConfig

/**
 * 將日麻規則設定與 persistence DTO 互相轉換。
 *
 * @return 轉換後的結果。
 */
fun RiichiRuleConfig.toPersistenceDto(): RiichiRuleConfigPersistenceDto = RiichiRuleConfigPersistenceDto(
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

/**
 * 將日麻規則 persistence DTO 還原成領域設定。
 *
 * @return 轉換後的結果。
 */
fun RiichiRuleConfigPersistenceDto.toDomain(): RiichiRuleConfig = RiichiRuleConfig(
    redDoraCount = redDoraCount,
    allowOpenTanyao = allowOpenTanyao,
    useLocalYaku = useLocalYaku,
    minimumWinConstraint = minimumWinConstraint,
    scoreConfig = RiichiScoreConfig(initialScore, bustThreshold, minPointsToWin, notenPenaltyUnit),
    gameLength = gameLength.toDomain(),
    multiRonPolicy = multiRonPolicy.toDomain(),
)

/**
 * 將三人日麻規則設定與 persistence DTO 互相轉換。
 *
 * @return 轉換後的結果。
 */
fun ThreePlayerRiichiRuleConfig.toPersistenceDto(): ThreePlayerRiichiRuleConfigPersistenceDto = ThreePlayerRiichiRuleConfigPersistenceDto(
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

/**
 * 將三人日麻規則 persistence DTO 還原成領域設定。
 *
 * @return 轉換後的結果。
 */
fun ThreePlayerRiichiRuleConfigPersistenceDto.toDomain(): ThreePlayerRiichiRuleConfig = ThreePlayerRiichiRuleConfig(
    redDoraCount = redDoraCount,
    allowOpenTanyao = allowOpenTanyao,
    useLocalYaku = useLocalYaku,
    minimumWinConstraint = minimumWinConstraint,
    scoreConfig = RiichiScoreConfig(initialScore, bustThreshold, minPointsToWin, notenPenaltyUnit),
    gameLength = gameLength.toDomain(),
    multiRonPolicy = multiRonPolicy.toDomain(),
)

/**
 * 將台麻規則設定與 persistence DTO 互相轉換。
 *
 * @return 轉換後的結果。
 */
fun TaiwanRuleConfig.toPersistenceDto(): TaiwanRuleConfigPersistenceDto = TaiwanRuleConfigPersistenceDto(
    useFlowerTiles = useFlowerTiles,
    minimumWinConstraint = minimumWinConstraint,
    baseScore = scoreConfig.baseScore,
    pointPerTai = scoreConfig.pointPerTai,
    initialScore = scoreConfig.initialScore,
    bustThreshold = scoreConfig.bustThreshold,
    gameLength = gameLength.toPersistenceDto(),
    multiRonPolicy = multiRonPolicy.toPersistenceDto(),
)

/**
 * 將台麻規則 persistence DTO 還原成領域設定。
 *
 * @return 轉換後的結果。
 */
fun TaiwanRuleConfigPersistenceDto.toDomain(): TaiwanRuleConfig = TaiwanRuleConfig(
    useFlowerTiles = useFlowerTiles,
    minimumWinConstraint = minimumWinConstraint,
    scoreConfig = TaiwanScoreConfig(baseScore, pointPerTai, initialScore, bustThreshold),
    gameLength = gameLength.toDomain(),
    multiRonPolicy = multiRonPolicy.toDomain(),
)

/** 將多家榮和政策轉為持久化 DTO。 */
private fun MultiRonPolicy.toPersistenceDto(): MultiRonPolicyPersistenceDto = MultiRonPolicyPersistenceDto(
    doubleRonResolution = doubleRonResolution.toPersistenceDto(),
    tripleRonResolution = tripleRonResolution.toPersistenceDto(),
)

/** 將持久化 DTO 還原為多家榮和政策。 */
private fun MultiRonPolicyPersistenceDto.toDomain(): MultiRonPolicy = MultiRonPolicy(
    doubleRonResolution = doubleRonResolution.toDomain(),
    tripleRonResolution = tripleRonResolution.toDomain(),
)

/** 將榮和處理方式轉為持久化 DTO。 */
private fun RonResolution.toPersistenceDto(): RonResolutionPersistenceDto = RonResolutionPersistenceDto.valueOf(name)

/** 將持久化 DTO 還原為榮和處理方式。 */
private fun RonResolutionPersistenceDto.toDomain(): RonResolution = RonResolution.valueOf(name)

/** 將日麻對局長度轉為持久化 DTO。 */
private fun RiichiGameLength.toPersistenceDto(): RiichiGameLengthPersistenceDto = when (this) {
    RiichiGameLength.OneGame -> RiichiGameLengthPersistenceDto.ONE_GAME
    RiichiGameLength.East -> RiichiGameLengthPersistenceDto.EAST
    RiichiGameLength.TwoWinds -> RiichiGameLengthPersistenceDto.TWO_WINDS
}

/** 將持久化 DTO 還原為日麻對局長度。 */
private fun RiichiGameLengthPersistenceDto.toDomain(): RiichiGameLength = when (this) {
    RiichiGameLengthPersistenceDto.ONE_GAME -> RiichiGameLength.OneGame
    RiichiGameLengthPersistenceDto.EAST -> RiichiGameLength.East
    RiichiGameLengthPersistenceDto.TWO_WINDS -> RiichiGameLength.TwoWinds
}

/** 將台麻對局長度轉為持久化 DTO。 */
private fun TaiwanGameLength.toPersistenceDto(): TaiwanGameLengthPersistenceDto = when (this) {
    TaiwanGameLength.OneGame -> TaiwanGameLengthPersistenceDto.ONE_GAME
    TaiwanGameLength.East -> TaiwanGameLengthPersistenceDto.EAST
    TaiwanGameLength.TwoWinds -> TaiwanGameLengthPersistenceDto.TWO_WINDS
    TaiwanGameLength.FourWinds -> TaiwanGameLengthPersistenceDto.FOUR_WINDS
}

/** 將持久化 DTO 還原為台麻對局長度。 */
private fun TaiwanGameLengthPersistenceDto.toDomain(): TaiwanGameLength = when (this) {
    TaiwanGameLengthPersistenceDto.ONE_GAME -> TaiwanGameLength.OneGame
    TaiwanGameLengthPersistenceDto.EAST -> TaiwanGameLength.East
    TaiwanGameLengthPersistenceDto.TWO_WINDS -> TaiwanGameLength.TwoWinds
    TaiwanGameLengthPersistenceDto.FOUR_WINDS -> TaiwanGameLength.FourWinds
}
