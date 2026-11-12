package com.doublemoon1119.mahjongcraft.flow.network.dto.rule.riichi
import com.doublemoon1119.mahjongcraft.flow.network.dto.model.toDomain
import com.doublemoon1119.mahjongcraft.flow.network.dto.model.toDto
import com.doublemoon1119.mahjongcraft.flow.network.dto.registry.NetworkDtoConverter
import com.doublemoon1119.mahjongcraft.logic.rules.riichi.RiichiGameLength
import com.doublemoon1119.mahjongcraft.logic.rules.riichi.RiichiPaoLiability
import com.doublemoon1119.mahjongcraft.logic.rules.riichi.RiichiPaoYaku
import com.doublemoon1119.mahjongcraft.logic.rules.riichi.RiichiRuleConfig
import com.doublemoon1119.mahjongcraft.logic.rules.riichi.RiichiScoreConfig
import com.doublemoon1119.mahjongcraft.logic.rules.riichi.threeplayer.ThreePlayerRiichiRuleConfig
import com.doublemoon1119.mahjongcraft.flow.network.dto.rule.toDomain as toRuleDomain
import com.doublemoon1119.mahjongcraft.flow.network.dto.rule.toDto as toRuleDto
/**
 * 將日麻規則設定轉為網路 DTO。
 *
 * @param registries 網路 DTO 的動態註冊表集合。
 * @return 轉換後的資料。
 */
fun RiichiRuleConfig.toRiichiDto(registries: NetworkDtoConverter): RiichiRuleConfigDto = RiichiRuleConfigDto(
    initialHandSize = initialHandSize,
    reservedTileCount = reservedTileCount,
    scoreConfig = scoreConfig.toRiichiDto(),
    gameLength = gameLength.toRuleDto(registries),
    minimumWinConstraint = minimumWinConstraint,
    minPlayers = minPlayers,
    maxPlayers = maxPlayers,
    multiRonPolicy = multiRonPolicy.toRuleDto(),
    redDoraCount = redDoraCount,
    allowOpenTanyao = allowOpenTanyao,
    useLocalYaku = useLocalYaku,
    revealsClosedKanTiles = revealsClosedKanTiles,
)

/**
 * 將日麻規則設定 DTO 還原為領域資料；固定的起始手牌、王牌與玩家人數使用規則值。
 *
 * @param registries 網路 DTO 的動態註冊表集合。
 * @return 轉換後的資料。
 */
fun RiichiRuleConfigDto.toDomain(registries: NetworkDtoConverter): RiichiRuleConfig = RiichiRuleConfig(
    redDoraCount = redDoraCount,
    allowOpenTanyao = allowOpenTanyao,
    useLocalYaku = useLocalYaku,
    minimumWinConstraint = minimumWinConstraint,
    scoreConfig = scoreConfig.toDomain(),
    gameLength = requireNotNull(gameLength.toRuleDomain(registries) as? RiichiGameLength) { "Riichi config requires a riichi game length" },
    multiRonPolicy = multiRonPolicy.toRuleDomain(),
)

/**
 * 將三人日麻規則設定轉為網路 DTO。
 *
 * @param registries 網路 DTO 的動態註冊表集合。
 * @return 轉換後的資料。
 */
fun ThreePlayerRiichiRuleConfig.toThreePlayerRiichiDto(registries: NetworkDtoConverter): ThreePlayerRiichiRuleConfigDto = ThreePlayerRiichiRuleConfigDto(
    initialHandSize = initialHandSize,
    reservedTileCount = reservedTileCount,
    scoreConfig = scoreConfig.toRiichiDto(),
    gameLength = gameLength.toRuleDto(registries),
    minimumWinConstraint = minimumWinConstraint,
    minPlayers = minPlayers,
    maxPlayers = maxPlayers,
    multiRonPolicy = multiRonPolicy.toRuleDto(),
    redDoraCount = redDoraCount,
    allowOpenTanyao = allowOpenTanyao,
    useLocalYaku = useLocalYaku,
    revealsClosedKanTiles = revealsClosedKanTiles,
)

/**
 * 將三人日麻規則設定 DTO 還原為領域資料；固定的起始手牌、王牌與玩家人數使用規則值。
 *
 * @param registries 網路 DTO 的動態註冊表集合。
 * @return 轉換後的資料。
 */
fun ThreePlayerRiichiRuleConfigDto.toDomain(registries: NetworkDtoConverter): ThreePlayerRiichiRuleConfig = ThreePlayerRiichiRuleConfig(
    redDoraCount = redDoraCount,
    allowOpenTanyao = allowOpenTanyao,
    useLocalYaku = useLocalYaku,
    minimumWinConstraint = minimumWinConstraint,
    scoreConfig = scoreConfig.toDomain(),
    gameLength = requireNotNull(gameLength.toRuleDomain(registries) as? RiichiGameLength) { "Riichi config requires a riichi game length" },
    multiRonPolicy = multiRonPolicy.toRuleDomain(),
)

/**
 * 將日麻計分設定轉為網路 DTO。
 *
 * @return 轉換後的資料。
 */
fun RiichiScoreConfig.toRiichiDto(): RiichiScoreConfigDto = RiichiScoreConfigDto(
    initialScore = initialScore,
    bustThreshold = bustThreshold,
    minPointsToWin = minPointsToWin,
    notenPenaltyUnit = notenPenaltyUnit,
)

/**
 * 將日麻計分設定 DTO 還原為領域資料。
 *
 * @return 轉換後的資料。
 */
fun RiichiScoreConfigDto.toDomain(): RiichiScoreConfig = RiichiScoreConfig(
    initialScore = initialScore,
    bustThreshold = bustThreshold,
    minPointsToWin = minPointsToWin,
    notenPenaltyUnit = notenPenaltyUnit,
)

/**
 * 將包責任資料轉為日麻網路 DTO。
 *
 * @return 轉換後的資料。
 */
fun RiichiPaoLiability.toDto(): RiichiPaoLiabilityDto = RiichiPaoLiabilityDto(
    yaku = when (yaku) {
        RiichiPaoYaku.Daisangen -> RiichiPaoYakuDto.Daisangen
        RiichiPaoYaku.Daisuushii -> RiichiPaoYakuDto.Daisuushii
    },
    direction = direction.toDto(),
)

/**
 * 將日麻包責任 DTO 還原為領域資料。
 *
 * @return 轉換後的資料。
 */
fun RiichiPaoLiabilityDto.toDomain(): RiichiPaoLiability = RiichiPaoLiability(
    yaku = when (yaku) {
        RiichiPaoYakuDto.Daisangen -> RiichiPaoYaku.Daisangen
        RiichiPaoYakuDto.Daisuushii -> RiichiPaoYaku.Daisuushii
    },
    direction = direction.toDomain(),
)
