package com.doublemoon1119.mahjongcraft.flow.network.dto.rule.riichi
import com.doublemoon1119.mahjongcraft.flow.network.dto.model.toDomain
import com.doublemoon1119.mahjongcraft.flow.network.dto.model.toDto
import com.doublemoon1119.mahjongcraft.flow.network.dto.rule.NetworkDtoRegistries
import com.doublemoon1119.mahjongcraft.logic.base.GameAction
import com.doublemoon1119.mahjongcraft.logic.rules.riichi.PaoLiability
import com.doublemoon1119.mahjongcraft.logic.rules.riichi.PaoYaku
import com.doublemoon1119.mahjongcraft.logic.rules.riichi.RiichiDiscardEntry
import com.doublemoon1119.mahjongcraft.logic.rules.riichi.RiichiDiscardPile
import com.doublemoon1119.mahjongcraft.logic.rules.riichi.RiichiDynamicState
import com.doublemoon1119.mahjongcraft.logic.rules.riichi.RiichiGameLength
import com.doublemoon1119.mahjongcraft.logic.rules.riichi.RiichiPendingKanDoraReveal
import com.doublemoon1119.mahjongcraft.logic.rules.riichi.RiichiPlayerState
import com.doublemoon1119.mahjongcraft.logic.rules.riichi.RiichiRuleConfig
import com.doublemoon1119.mahjongcraft.logic.rules.riichi.RiichiScoreConfig
import com.doublemoon1119.mahjongcraft.logic.rules.riichi.threeplayer.ThreePlayerRiichiRuleConfig
import kotlin.uuid.Uuid
import com.doublemoon1119.mahjongcraft.flow.network.dto.rule.toDomain as toRuleDomain
import com.doublemoon1119.mahjongcraft.flow.network.dto.rule.toDto as toRuleDto

/**
 * 將日麻規則設定轉為網路 DTO。
 *
 * @param registries 網路 DTO 的動態註冊表集合。
 * @return 轉換後的資料。
 */
fun RiichiRuleConfig.toRiichiDto(registries: NetworkDtoRegistries): RiichiRuleConfigDto = RiichiRuleConfigDto(
    initialHandSize = initialHandSize,
    deadTileCount = deadTileCount,
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
fun RiichiRuleConfigDto.toDomain(registries: NetworkDtoRegistries): RiichiRuleConfig = RiichiRuleConfig(
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
fun ThreePlayerRiichiRuleConfig.toThreePlayerRiichiDto(registries: NetworkDtoRegistries): ThreePlayerRiichiRuleConfigDto = ThreePlayerRiichiRuleConfigDto(
    initialHandSize = initialHandSize,
    deadTileCount = deadTileCount,
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
fun ThreePlayerRiichiRuleConfigDto.toDomain(registries: NetworkDtoRegistries): ThreePlayerRiichiRuleConfig = ThreePlayerRiichiRuleConfig(
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
 * 將日麻動態桌況狀態轉為網路 DTO。
 *
 * @return 轉換後的資料。
 */
fun RiichiDynamicState.toRiichiDto(): RiichiDynamicStateDto = RiichiDynamicStateDto(
    riichiStickCount,
    completedSupplementalDrawCount,
    revealedKanDoraCount,
    pendingKanDoraReveals.map {
        RiichiPendingKanDoraRevealDto(
            it.actorPlayerId.toString(),
            it.sourceKanType.toPendingDto(),
            it.supplementalDrawNumber,
        )
    },
    completedNorthDrawCount,
)

/**
 * 將日麻動態桌況狀態 DTO 還原為領域資料。
 *
 * @return 轉換後的資料。
 */
fun RiichiDynamicStateDto.toDomain(): RiichiDynamicState = RiichiDynamicState(
    riichiStickCount,
    completedSupplementalDrawCount,
    revealedKanDoraCount,
    pendingKanDoraReveals.map {
        RiichiPendingKanDoraReveal(
            Uuid.parse(it.actorPlayerId),
            it.sourceKanType.toDomain(),
            it.supplementalDrawNumber,
        )
    },
    completedNorthDrawCount,
)

/**
 * 將來源槓牌種類轉為等待公開項目的網路 DTO。
 *
 * @return 轉換後的資料。
 */
private fun GameAction.KanType.toPendingDto(): RiichiPendingKanTypeDto = when (this) {
    GameAction.KanType.OPEN_KAN -> RiichiPendingKanTypeDto.OPEN_KAN
    GameAction.KanType.CLOSED_KAN -> RiichiPendingKanTypeDto.CLOSED_KAN
    GameAction.KanType.ADDED_KAN -> RiichiPendingKanTypeDto.ADDED_KAN
}

/**
 * 將等待公開項目的網路 DTO 還原為來源槓牌種類。
 *
 * @return 轉換後的資料。
 */
private fun RiichiPendingKanTypeDto.toDomain(): GameAction.KanType = when (this) {
    RiichiPendingKanTypeDto.OPEN_KAN -> GameAction.KanType.OPEN_KAN
    RiichiPendingKanTypeDto.CLOSED_KAN -> GameAction.KanType.CLOSED_KAN
    RiichiPendingKanTypeDto.ADDED_KAN -> GameAction.KanType.ADDED_KAN
}

/**
 * 將包責任資料轉為日麻網路 DTO。
 *
 * @return 轉換後的資料。
 */
fun PaoLiability.toDto(): PaoLiabilityDto = PaoLiabilityDto(
    yaku = when (yaku) {
        PaoYaku.Daisangen -> PaoYakuDto.Daisangen
        PaoYaku.Daisuushii -> PaoYakuDto.Daisuushii
    },
    direction = direction.toDto(),
)

/**
 * 將日麻包責任 DTO 還原為領域資料。
 *
 * @return 轉換後的資料。
 */
fun PaoLiabilityDto.toDomain(): PaoLiability = PaoLiability(
    yaku = when (yaku) {
        PaoYakuDto.Daisangen -> PaoYaku.Daisangen
        PaoYakuDto.Daisuushii -> PaoYaku.Daisuushii
    },
    direction = direction.toDomain(),
)

/**
 * 將日麻玩家規則狀態轉為網路 DTO。
 *
 * @return 轉換後的資料。
 */
fun RiichiPlayerState.toRiichiDto(): RiichiPlayerStateDto = RiichiPlayerStateDto(
    riichiTile = riichiTile?.toDto(),
    doubleRiichiTile = doubleRiichiTile?.toDto(),
    isIppatsu = isIppatsu,
    paoLiability = paoLiability?.toDto(),
    nukiDoraTiles = nukiDoraTiles.map { it.toDto() },
)

/**
 * 將日麻玩家規則狀態 DTO 還原為領域資料。
 *
 * @return 轉換後的資料。
 */
fun RiichiPlayerStateDto.toDomain(): RiichiPlayerState = RiichiPlayerState(
    riichiTile = riichiTile?.toDomain(),
    doubleRiichiTile = doubleRiichiTile?.toDomain(),
    isIppatsu = isIppatsu,
    paoLiability = paoLiability?.toDomain(),
    nukiDoraTiles = nukiDoraTiles.map { it.toDomain() },
)

/**
 * 將日麻牌河轉為網路 DTO。
 *
 * @return 轉換後的資料。
 */
fun RiichiDiscardPile.toRiichiDto(): RiichiDiscardPileDto = RiichiDiscardPileDto(
    entries = entries.map { RiichiDiscardEntryDto(it.tile.toDto(), it.isRiichi, it.isTaken) },
)

/**
 * 將日麻牌河 DTO 還原為領域資料。
 *
 * @return 轉換後的資料。
 */
fun RiichiDiscardPileDto.toDomain(): RiichiDiscardPile = RiichiDiscardPile(
    entries.map { RiichiDiscardEntry(it.tile.toDomain(), it.isRiichi, it.isTaken) },
)
