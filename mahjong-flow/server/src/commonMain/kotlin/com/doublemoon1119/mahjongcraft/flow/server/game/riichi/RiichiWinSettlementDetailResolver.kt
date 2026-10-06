package com.doublemoon1119.mahjongcraft.flow.server.game.riichi

import com.doublemoon1119.mahjongcraft.flow.common.game.model.ResolvedRoundOutcome
import com.doublemoon1119.mahjongcraft.flow.common.game.model.WinSettlementDetailEntry
import com.doublemoon1119.mahjongcraft.flow.common.game.model.WinSettlementDetailField
import com.doublemoon1119.mahjongcraft.flow.common.game.model.WinSettlementDetailValue
import com.doublemoon1119.mahjongcraft.flow.common.game.model.WinSettlementQuantity
import com.doublemoon1119.mahjongcraft.flow.common.game.model.riichi.RiichiRoundOutcomeIds
import com.doublemoon1119.mahjongcraft.flow.common.game.model.riichi.RiichiWinSettlementIds
import com.doublemoon1119.mahjongcraft.flow.server.game.service.WinSettlementDetailResolver
import com.doublemoon1119.mahjongcraft.logic.judgment.HandValueResult
import com.doublemoon1119.mahjongcraft.logic.rules.riichi.RiichiDynamicState
import com.doublemoon1119.mahjongcraft.logic.rules.riichi.RiichiHandValueResult
import com.doublemoon1119.mahjongcraft.logic.rules.riichi.yaku.YakuType
import com.doublemoon1119.mahjongcraft.logic.table.TableState

/** 日麻專屬的胡牌詳情解析器：一般胡牌的役種／翻符／寶牌，以及流局滿貫的特殊結算內容。 */
object RiichiWinSettlementDetailResolver : WinSettlementDetailResolver {
    override fun resolve(state: TableState, handValue: HandValueResult): List<WinSettlementDetailField> = riichiDetails(state, handValue)

    override fun resolveSpecialOutcome(state: TableState, outcome: ResolvedRoundOutcome): List<WinSettlementDetailField>? {
        if (outcome.id != RiichiRoundOutcomeIds.NAGASHI_MANGAN) return null
        return listOf(
            WinSettlementDetailField(
                RiichiWinSettlementIds.YAKU_FIELD,
                WinSettlementDetailValue.Entries(listOf(WinSettlementDetailEntry(RiichiWinSettlementIds.NAGASHI_MANGAN))),
            ),
        )
    }

    internal fun riichiDetails(state: TableState, handValue: HandValueResult): List<WinSettlementDetailField> {
        val result = handValue as? RiichiHandValueResult ?: return emptyList()
        val indicators = (state.dynamicRuleState as? RiichiDynamicState)?.getDoraIndicators(state)
        return buildList {
            add(
                WinSettlementDetailField(
                    RiichiWinSettlementIds.YAKU_FIELD,
                    WinSettlementDetailValue.Entries(
                        result.yakuResults.map { yaku ->
                            if (result.isYakuman) {
                                RiichiWinSettlementIds.yakumanEntry(yaku.yaku, -yaku.han)
                            } else {
                                RiichiWinSettlementIds.yakuEntry(yaku.yaku, yaku.han)
                            }
                        },
                    ),
                ),
            )
            if (!result.isYakuman) {
                add(WinSettlementDetailField(RiichiWinSettlementIds.HAN_FU_FIELD, riichiHanFuValue(result.totalHan, result.totalFu)))
            } else {
                add(
                    WinSettlementDetailField(
                        RiichiWinSettlementIds.YAKUMAN_TOTAL_FIELD,
                        WinSettlementDetailValue.Quantities(listOf(WinSettlementQuantity(RiichiWinSettlementIds.YAKUMAN, -result.totalHan))),
                    ),
                )
            }
            add(WinSettlementDetailField(RiichiWinSettlementIds.DORA_FIELD, WinSettlementDetailValue.Tiles(indicators?.first.orEmpty().map { it.id })))
            // 只使用本次權威役種結果辨識立直資格，不從其他玩家的立直狀態推測。
            if (!result.isYakuman && result.yakuResults.any { it.yaku == YakuType.Riichi || it.yaku == YakuType.DoubleRiichi }) {
                add(WinSettlementDetailField(RiichiWinSettlementIds.URA_DORA_FIELD, WinSettlementDetailValue.Tiles(indicators?.second.orEmpty().map { it.id })))
            }
        }
    }

    /** 建立一般胡牌的翻符；滿貫以上沒有權威符數時只有翻數。 */
    internal fun riichiHanFuValue(totalHan: Int, totalFu: Int): WinSettlementDetailValue.Quantities = WinSettlementDetailValue.Quantities(
        listOfNotNull(
            WinSettlementQuantity(RiichiWinSettlementIds.HAN, totalHan),
            WinSettlementQuantity(RiichiWinSettlementIds.FU, totalFu).takeIf { totalFu > 0 },
        ),
    )
}
