package com.doublemoon1119.mahjongcraft.flow.network.dto.rule.taiwan
import com.doublemoon1119.mahjongcraft.flow.network.dto.model.toDomain
import com.doublemoon1119.mahjongcraft.flow.network.dto.model.toDto
import com.doublemoon1119.mahjongcraft.flow.network.dto.rule.NetworkDtoRegistries
import com.doublemoon1119.mahjongcraft.logic.rules.taiwan.TaiwanDiscardPile
import com.doublemoon1119.mahjongcraft.logic.rules.taiwan.TaiwanGameLength
import com.doublemoon1119.mahjongcraft.logic.rules.taiwan.TaiwanRuleConfig
import com.doublemoon1119.mahjongcraft.logic.rules.taiwan.TaiwanScoreConfig
import com.doublemoon1119.mahjongcraft.logic.table.DiscardPile
import com.doublemoon1119.mahjongcraft.flow.network.dto.rule.toDomain as toRuleDomain
import com.doublemoon1119.mahjongcraft.flow.network.dto.rule.toDto as toRuleDto

/**
 * 將台灣麻將規則設定轉為網路 DTO。
 *
 * @param registries 網路 DTO 的動態註冊表集合。
 * @return 轉換後的資料。
 */
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
    revealsClosedKanTiles = revealsClosedKanTiles,
)

/**
 * 將台灣麻將規則設定 DTO 還原為領域資料；固定的起始手牌、王牌與玩家人數使用規則值。
 *
 * @param registries 網路 DTO 的動態註冊表集合。
 * @return 轉換後的資料。
 */
fun TaiwanRuleConfigDto.toDomain(registries: NetworkDtoRegistries): TaiwanRuleConfig = TaiwanRuleConfig(
    useFlowerTiles = useFlowerTiles,
    minimumWinConstraint = minimumWinConstraint,
    scoreConfig = scoreConfig.toDomain(),
    gameLength = gameLength.toRuleDomain(registries) as TaiwanGameLength,
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

/**
 * 將台灣麻將牌河轉為網路 DTO。
 *
 * @return 轉換後的資料。
 */
fun TaiwanDiscardPile.toTaiwanDto(): TaiwanDiscardPileDto = TaiwanDiscardPileDto(
    entries = entries.map { DiscardEntryDto(it.tile.toDto(), it.isTaken) },
)

/**
 * 將台灣麻將牌河 DTO 還原為領域資料。
 *
 * @return 轉換後的資料。
 */
fun TaiwanDiscardPileDto.toDomain(): TaiwanDiscardPile = TaiwanDiscardPile(
    entries.map { DiscardPile.DiscardEntry(it.tile.toDomain(), it.isTaken) },
)
