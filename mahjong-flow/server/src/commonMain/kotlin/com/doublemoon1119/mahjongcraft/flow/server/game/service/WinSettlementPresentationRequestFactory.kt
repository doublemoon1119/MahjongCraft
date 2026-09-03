package com.doublemoon1119.mahjongcraft.flow.server.game.service

import com.doublemoon1119.mahjongcraft.flow.common.game.model.BuiltInRoundOutcomeIds
import com.doublemoon1119.mahjongcraft.flow.common.game.model.ResolvedRoundOutcome
import com.doublemoon1119.mahjongcraft.flow.common.game.model.ScoreRankingPlayer
import com.doublemoon1119.mahjongcraft.flow.common.game.model.ScoreRankingPresentation
import com.doublemoon1119.mahjongcraft.flow.common.game.model.WinSettlementDetailField
import com.doublemoon1119.mahjongcraft.flow.common.game.model.WinSettlementDetailValue
import com.doublemoon1119.mahjongcraft.flow.common.game.model.WinSettlementPresentationRequest
import com.doublemoon1119.mahjongcraft.flow.common.game.model.WinSettlementTranslationKeys
import com.doublemoon1119.mahjongcraft.flow.common.game.model.WinSettlementWinnerPresentation
import com.doublemoon1119.mahjongcraft.flow.common.game.model.WinSettlementYakuTranslationKeys
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
                                    listOf(WinSettlementDetailValue.Entries.Entry(WinSettlementYakuTranslationKeys.NAGASHI_MANGAN)),
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
                            if (result.isYakuman) {
                                WinSettlementDetailValue.Entries.Entry(
                                    translationKey = yakuTranslationKey(yaku.yaku),
                                    trailingTranslationKey = yakumanMultiplierTranslationKey(-yaku.han),
                                    trailingTranslationArgument = (-yaku.han).takeIf { it !in 1..6 }?.toString(),
                                )
                            } else {
                                WinSettlementDetailValue.Entries.Entry(yakuTranslationKey(yaku.yaku), yaku.han.toString())
                            }
                        },
                    ),
                ),
            )
            if (!result.isYakuman) {
                add(WinSettlementDetailField(RIICHI_HAN_FU_FIELD, riichiHanFuValue(result.totalHan, result.totalFu)))
            } else {
                val multiplier = -result.totalHan
                add(
                    WinSettlementDetailField(
                        RIICHI_YAKUMAN_TOTAL_FIELD,
                        WinSettlementDetailValue.Text(
                            yakumanMultiplierTranslationKey(multiplier),
                            multiplier.takeIf { it !in 1..6 }?.let { listOf(it.toString()) }.orEmpty(),
                        ),
                    ),
                )
            }
            add(WinSettlementDetailField(RIICHI_DORA_FIELD, WinSettlementDetailValue.Tiles(indicators?.first.orEmpty().map { it.id })))
            add(WinSettlementDetailField(RIICHI_URA_DORA_FIELD, WinSettlementDetailValue.Tiles(indicators?.second.orEmpty().map { it.id })))
        }
    }

    private fun ranks(state: TableState, module: MahjongRuleModule<*>): Map<Uuid, Int> = state.players.sortedWith(module.compareForRoundRanking()).mapIndexed { index, player -> player.id to index + 1 }.toMap()

    /** 建立一般胡牌的翻符顯示；滿貫以上沒有權威符數時只顯示翻數。 */
    internal fun riichiHanFuValue(totalHan: Int, totalFu: Int): WinSettlementDetailValue.Text = if (totalFu > 0) {
        WinSettlementDetailValue.Text(
            WinSettlementTranslationKeys.HAN_FU,
            listOf(totalHan.toString(), totalFu.toString()),
        )
    } else {
        WinSettlementDetailValue.Text(
            WinSettlementTranslationKeys.HAN,
            listOf(totalHan.toString()),
        )
    }

    /** 對應既有玩家可見役種翻譯鍵；名稱差異單一來源見 [WinSettlementYakuTranslationKeys]。 */
    private fun yakuTranslationKey(type: YakuType): String = WinSettlementYakuTranslationKeys.keyFor(type)

    const val RIICHI_TEMPLATE_KEY = "mahjongcraft:riichi"
    const val GENERIC_TEMPLATE_KEY = "mahjongcraft:generic"
    const val RIICHI_YAKU_FIELD = "mahjongcraft:riichi_yaku"
    const val RIICHI_HAN_FU_FIELD = "mahjongcraft:riichi_han_fu"
    const val RIICHI_YAKUMAN_TOTAL_FIELD = "mahjongcraft:riichi_yakuman_total"
    const val RIICHI_DORA_FIELD = "mahjongcraft:riichi_dora"
    const val RIICHI_URA_DORA_FIELD = "mahjongcraft:riichi_ura_dora"

    private fun yakumanMultiplierTranslationKey(multiplier: Int): String = if (multiplier in 1..6) {
        "mahjongcraft.game.score.yakuman_${multiplier}x"
    } else {
        "mahjongcraft.game.score.yakuman_nx"
    }
}
