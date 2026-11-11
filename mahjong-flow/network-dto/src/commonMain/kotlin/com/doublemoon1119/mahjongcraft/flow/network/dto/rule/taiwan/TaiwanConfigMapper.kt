package com.doublemoon1119.mahjongcraft.flow.network.dto.rule.taiwan
import com.doublemoon1119.mahjongcraft.flow.network.dto.model.toDomain
import com.doublemoon1119.mahjongcraft.flow.network.dto.registry.NetworkDtoConverter
import com.doublemoon1119.mahjongcraft.logic.rules.taiwan.TaiwanGameLength
import com.doublemoon1119.mahjongcraft.logic.rules.taiwan.TaiwanRuleConfig
import com.doublemoon1119.mahjongcraft.logic.rules.taiwan.TaiwanScoreConfig
import com.doublemoon1119.mahjongcraft.flow.network.dto.rule.toDomain as toRuleDomain
import com.doublemoon1119.mahjongcraft.flow.network.dto.rule.toDto as toRuleDto

/**
 * 將台灣麻將規則設定轉為網路 DTO。
 *
 * @param registries 網路 DTO 的動態註冊表集合。
 * @return 轉換後的資料。
 */
fun TaiwanRuleConfig.toTaiwanDto(registries: NetworkDtoConverter): TaiwanRuleConfigDto = TaiwanRuleConfigDto(
    initialHandSize = initialHandSize,
    deadTileCount = deadTileCount,
    scoreConfig = scoreConfig.toTaiwanDto(),
    gameLength = gameLength.toRuleDto(registries),
    minimumWinConstraint = minimumWinConstraint,
    minPlayers = minPlayers,
    maxPlayers = maxPlayers,
    multiRonPolicy = multiRonPolicy.toRuleDto(),
    useFlowerTiles = useFlowerTiles,
    fourWindsAbortiveDraw = fourWindsAbortiveDraw,
    kanClearsPassedWin = kanClearsPassedWin,
    revealsClosedKanTiles = revealsClosedKanTiles,
)

/**
 * 將台灣麻將規則設定 DTO 還原為領域資料；固定的起始手牌、王牌與玩家人數使用規則值。
 *
 * @param registries 網路 DTO 的動態註冊表集合。
 * @return 轉換後的資料。
 */
fun TaiwanRuleConfigDto.toDomain(registries: NetworkDtoConverter): TaiwanRuleConfig = TaiwanRuleConfig(
    useFlowerTiles = useFlowerTiles,
    fourWindsAbortiveDraw = fourWindsAbortiveDraw,
    kanClearsPassedWin = kanClearsPassedWin,
    minimumWinConstraint = minimumWinConstraint,
    scoreConfig = scoreConfig.toDomain(),
    gameLength = requireNotNull(gameLength.toRuleDomain(registries) as? TaiwanGameLength) { "Taiwan config requires a taiwan game length" },
    multiRonPolicy = multiRonPolicy.toRuleDomain(),
)

/**
 * 將台灣麻將計分設定轉為網路 DTO。
 *
 * @return 轉換後的資料。
 */
fun TaiwanScoreConfig.toTaiwanDto(): TaiwanScoreConfigDto = TaiwanScoreConfigDto(
    baseScore = baseScore,
    pointPerTai = pointPerTai,
    initialScore = initialScore,
    bustThreshold = bustThreshold,
)

/**
 * 將台灣麻將計分設定 DTO 還原為領域資料。
 *
 * @return 轉換後的資料。
 */
fun TaiwanScoreConfigDto.toDomain(): TaiwanScoreConfig = TaiwanScoreConfig(
    baseScore = baseScore,
    pointPerTai = pointPerTai,
    initialScore = initialScore,
    bustThreshold = bustThreshold,
)
