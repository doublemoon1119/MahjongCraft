package com.doublemoon1119.mahjongcraft.flow.server.game.service

import com.doublemoon1119.mahjongcraft.flow.common.game.model.BuiltInRoundOutcomeIds
import com.doublemoon1119.mahjongcraft.flow.common.game.model.ResolvedRoundOutcome
import com.doublemoon1119.mahjongcraft.flow.common.game.model.ScoreRankingPlayer
import com.doublemoon1119.mahjongcraft.flow.common.game.model.ScoreRankingPresentation
import com.doublemoon1119.mahjongcraft.flow.common.game.model.WinSettlementDetailField
import com.doublemoon1119.mahjongcraft.flow.common.game.model.WinSettlementDetailValue
import com.doublemoon1119.mahjongcraft.flow.common.game.model.WinSettlementPresentationRequest
import com.doublemoon1119.mahjongcraft.flow.common.game.model.WinSettlementWinnerPresentation
import com.doublemoon1119.mahjongcraft.flow.common.game.service.toPresentation
import com.doublemoon1119.mahjongcraft.logic.module.MahjongRuleModule
import com.doublemoon1119.mahjongcraft.logic.module.WinResolutionResult
import com.doublemoon1119.mahjongcraft.logic.rules.riichi.RiichiDynamicState
import com.doublemoon1119.mahjongcraft.logic.rules.riichi.RiichiHandValueResult
import com.doublemoon1119.mahjongcraft.logic.rules.riichi.yaku.YakuType
import com.doublemoon1119.mahjongcraft.logic.table.TableState
import kotlin.uuid.Uuid

/** 建立胡牌詳情與共用分數排行的權威快照。 */
object WinSettlementPresentationRequestFactory {
    /** 建立不偽造翻符或胡牌張的 win-equivalent 特殊 outcome request。 */
    fun createSpecialOutcome(
        previousState: TableState,
        outcome: ResolvedRoundOutcome,
        module: MahjongRuleModule<*>,
    ): WinSettlementPresentationRequest {
        val currentState = outcome.settledTableState
        val previousRanks = ranks(previousState, module)
        val currentRanks = ranks(currentState, module)
        return WinSettlementPresentationRequest(
            outcomeId = outcome.id,
            templateKey = if (outcome.id == BuiltInRoundOutcomeIds.NAGASHI_MANGAN) {
                RIICHI_TEMPLATE_KEY
            } else {
                GENERIC_TEMPLATE_KEY
            },
            isTsumo = outcome.responsiblePlayerIds.isEmpty(),
            winners = outcome.beneficiaryPlayerIds.map { winnerId ->
                val player = currentState.players.first { it.id == winnerId }
                WinSettlementWinnerPresentation(
                    playerId = winnerId,
                    seatIndex = currentState.players.indexOf(player),
                    responsiblePlayerId = outcome.responsiblePlayerIds.singleOrNull(),
                    totalScore = outcome.scoreDeltas.getValue(winnerId),
                    handTileIds = player.hand.allTiles.map { it.id },
                    melds = player.hand.melds.map { it.toPresentation(currentState.config.revealsClosedKanTiles) },
                    winningTileId = null,
                    detailFields = if (outcome.id == BuiltInRoundOutcomeIds.NAGASHI_MANGAN) {
                        listOf(
                            WinSettlementDetailField(
                                RIICHI_YAKU_FIELD,
                                WinSettlementDetailValue.Entries(
                                    listOf(WinSettlementDetailValue.Entries.Entry("mahjongcraft.game.yaku.nagashi_mangan")),
                                ),
                            ),
                        )
                    } else {
                        emptyList()
                    },
                )
            },
            ranking = ScoreRankingPresentation(
                currentState.players.mapIndexed { seatIndex, player ->
                    val previous = previousState.players.first { it.id == player.id }
                    ScoreRankingPlayer(
                        player.id,
                        seatIndex,
                        player.isAi,
                        previous.score,
                        player.score,
                        previousRanks.getValue(player.id),
                        currentRanks.getValue(player.id),
                    )
                },
            ),
        )
    }

    /** 建立一般自摸／榮和 request；規則專屬欄位在此轉成穩定、可序列化的 detail values。 */
    fun create(
        previousState: TableState,
        currentState: TableState,
        module: MahjongRuleModule<*>,
        outcomeId: String,
        isTsumo: Boolean,
        winningTileId: Uuid,
        responsiblePlayerId: Uuid?,
        resolutions: Map<Uuid, WinResolutionResult>,
        detailResolverRegistry: WinSettlementDetailResolverRegistry = createBuiltInWinSettlementDetailResolverRegistry(),
    ): WinSettlementPresentationRequest {
        val previousRanks = ranks(previousState, module)
        val currentRanks = ranks(currentState, module)
        val resolvedDetails = resolutions.mapValues { (_, resolution) ->
            detailResolverRegistry.resolve(module.id, currentState, resolution.handValueResult)
        }
        return WinSettlementPresentationRequest(
            outcomeId = outcomeId,
            templateKey = resolvedDetails.values.map(WinSettlementResolvedDetails::templateKey).distinct().singleOrNull()
                ?: GENERIC_TEMPLATE_KEY,
            isTsumo = isTsumo,
            winners = resolutions.map { (winnerId, resolution) ->
                val player = currentState.players.first { it.id == winnerId }
                WinSettlementWinnerPresentation(
                    playerId = winnerId,
                    seatIndex = currentState.players.indexOf(player),
                    responsiblePlayerId = responsiblePlayerId,
                    totalScore = resolution.totalGained,
                    handTileIds = player.hand.allTiles.map { it.id }.filterNot { it == winningTileId },
                    melds = player.hand.melds.map { it.toPresentation(currentState.config.revealsClosedKanTiles) },
                    winningTileId = winningTileId,
                    detailFields = resolvedDetails.getValue(winnerId).fields,
                )
            },
            ranking = ScoreRankingPresentation(
                currentState.players.mapIndexed { seatIndex, player ->
                    val previous = previousState.players.first { it.id == player.id }
                    ScoreRankingPlayer(
                        player.id,
                        seatIndex,
                        player.isAi,
                        previous.score,
                        player.score,
                        previousRanks.getValue(player.id),
                        currentRanks.getValue(player.id),
                    )
                },
            ),
        )
    }

    internal fun riichiDetails(state: TableState, handValue: com.doublemoon1119.mahjongcraft.logic.judgment.HandValueResult): List<WinSettlementDetailField> {
        val result = handValue as? RiichiHandValueResult ?: return emptyList()
        val indicators = (state.dynamicRuleState as? RiichiDynamicState)?.getDoraIndicators(state)
        return buildList {
            add(
                WinSettlementDetailField(
                    RIICHI_YAKU_FIELD,
                    WinSettlementDetailValue.Entries(
                        result.yakuResults.map { yaku ->
                            WinSettlementDetailValue.Entries.Entry(yakuTranslationKey(yaku.yaku), if (result.isYakuman) "" else yaku.han.toString())
                        },
                    ),
                ),
            )
            if (!result.isYakuman) {
                add(WinSettlementDetailField(RIICHI_HAN_FU_FIELD, WinSettlementDetailValue.Text("settlement.mahjongcraft.han_fu", listOf(result.totalHan.toString(), result.totalFu.toString()))))
            }
            add(WinSettlementDetailField(RIICHI_DORA_FIELD, WinSettlementDetailValue.Tiles(indicators?.first.orEmpty().map { it.id })))
            add(WinSettlementDetailField(RIICHI_URA_DORA_FIELD, WinSettlementDetailValue.Tiles(indicators?.second.orEmpty().map { it.id })))
        }
    }

    private fun ranks(state: TableState, module: MahjongRuleModule<*>): Map<Uuid, Int> = state.players.sortedWith(module.compareForRoundRanking()).mapIndexed { index, player -> player.id to index + 1 }.toMap()

    /** 對應既有玩家可見役種翻譯鍵；名稱差異集中在這裡，不由 renderer 猜測 enum 名稱。 */
    private fun yakuTranslationKey(type: YakuType): String = "mahjongcraft.game.yaku.${YAKU_TRANSLATION_PATHS.getValue(type)}"

    const val RIICHI_TEMPLATE_KEY = "mahjongcraft:riichi"
    const val GENERIC_TEMPLATE_KEY = "mahjongcraft:generic"
    const val RIICHI_YAKU_FIELD = "mahjongcraft:riichi_yaku"
    const val RIICHI_HAN_FU_FIELD = "mahjongcraft:riichi_han_fu"
    const val RIICHI_DORA_FIELD = "mahjongcraft:riichi_dora"
    const val RIICHI_URA_DORA_FIELD = "mahjongcraft:riichi_ura_dora"

    private val YAKU_TRANSLATION_PATHS = mapOf(
        YakuType.Dora to "dora",
        YakuType.UraDora to "uradora",
        YakuType.AkaDora to "red_five",
        YakuType.Tanyao to "tanyao",
        YakuType.Pinfu to "pinfu",
        YakuType.Iipeikou to "ipeiko",
        YakuType.Riichi to "reach",
        YakuType.DoubleRiichi to "double_reach",
        YakuType.Ippatsu to "ippatsu",
        YakuType.RinshanKaihou to "rinshankaihoh",
        YakuType.Haitei to "haitei",
        YakuType.Houtei to "houtei",
        YakuType.Chankan to "chankan",
        YakuType.Menzentsumo to "tsumo",
        YakuType.Toitoi to "toitoiho",
        YakuType.Sanankou to "sananko",
        YakuType.Sankantsu to "sankantsu",
        YakuType.SanshokuDokoku to "sanshokudohko",
        YakuType.SanshokuDoujun to "sanshokudohjun",
        YakuType.Honchan to "chanta",
        YakuType.Junchan to "junchan",
        YakuType.Honitsu to "honitsu",
        YakuType.Ryanpeikou to "ryanpeiko",
        YakuType.Ittuitsu to "ikkitsukan",
        YakuType.Honroutou to "honrohtoh",
        YakuType.Chinitsu to "chinitsu",
        YakuType.Shousangen to "shosangen",
        YakuType.Chiitoitsu to "chitoitsu",
        YakuType.RoundWind to "bakaze",
        YakuType.SeatWind to "jikaze",
        YakuType.Dragon to "chun",
        YakuType.KokushiMusou to "kokushimuso",
        YakuType.ChurenPoto to "churenpohto",
        YakuType.Tsuuiisou to "tsuiso",
        YakuType.Ryuuuiisou to "ryuiso",
        YakuType.Suuankou to "suanko",
        YakuType.Sukantsu to "sukantsu",
        YakuType.Shousuushi to "shosushi",
        YakuType.Daisangen to "daisangen",
        YakuType.Chinroutou to "chinroto",
        YakuType.Tenhou to "tenho",
        YakuType.Chiihou to "chiho",
        YakuType.KokushiMusou13 to "kokushimuso_jusanmenmachi",
        YakuType.ChurenPoto9 to "junsei_churenpohto",
        YakuType.SuuankouTanki to "suanko_tanki",
        YakuType.Daisuushii to "daisushi",
    )
}
