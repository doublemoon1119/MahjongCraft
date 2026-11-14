package com.doublemoon1119.mahjongcraft.flow.server.game.riichi

import com.doublemoon1119.mahjongcraft.flow.common.game.model.ResolvedRoundOutcome
import com.doublemoon1119.mahjongcraft.flow.common.game.model.WinSettlementDetailEntry
import com.doublemoon1119.mahjongcraft.flow.common.game.model.WinSettlementDetailField
import com.doublemoon1119.mahjongcraft.flow.common.game.model.WinSettlementDetailValue
import com.doublemoon1119.mahjongcraft.flow.common.game.model.WinSettlementQuantity
import com.doublemoon1119.mahjongcraft.flow.common.game.model.riichi.RiichiRoundOutcomeIds
import com.doublemoon1119.mahjongcraft.flow.common.game.model.riichi.RiichiWinSettlementIds
import com.doublemoon1119.mahjongcraft.flow.server.game.service.WinSettlementDetailResolver
import com.doublemoon1119.mahjongcraft.logic.base.TileFaceView
import com.doublemoon1119.mahjongcraft.logic.base.TileValue
import com.doublemoon1119.mahjongcraft.logic.base.toValue
import com.doublemoon1119.mahjongcraft.logic.judgment.HandValueResult
import com.doublemoon1119.mahjongcraft.logic.rules.riichi.RiichiHandValueResult
import com.doublemoon1119.mahjongcraft.logic.rules.riichi.riichiWallProgress
import com.doublemoon1119.mahjongcraft.logic.rules.riichi.yaku.RiichiYakuType
import com.doublemoon1119.mahjongcraft.logic.table.TableState
import kotlin.uuid.Uuid

/** 日麻專屬的胡牌詳情解析器：一般胡牌的役種／翻符／寶牌，以及流局滿貫的特殊結算內容。 */
object RiichiWinSettlementDetailResolver : WinSettlementDetailResolver {
    override fun resolve(
        state: TableState,
        winnerId: Uuid,
        responsiblePlayerId: Uuid?,
        handValue: HandValueResult,
        publicFace: (TileValue) -> TileFaceView,
    ): List<WinSettlementDetailField> = riichiDetails(state, handValue, publicFace)

    override fun resolveSpecialOutcome(
        state: TableState,
        outcome: ResolvedRoundOutcome,
        publicFace: (TileValue) -> TileFaceView,
    ): List<WinSettlementDetailField>? {
        if (outcome.id != RiichiRoundOutcomeIds.NAGASHI_MANGAN) return null
        return listOf(
            element = WinSettlementDetailField(
                id = RiichiWinSettlementIds.YAKU_FIELD,
                value = WinSettlementDetailValue.Entries(listOf(WinSettlementDetailEntry(RiichiWinSettlementIds.NAGASHI_MANGAN))),
            ),
        )
    }

    /**
     * 建立日麻一般胡牌的役種、翻符與寶牌詳情；輸入不是日麻結算結果時回傳空清單。
     *
     * @param state 胡牌成立時的權威桌況。
     * @param handValue 已完成計算的胡牌結果。
     * @param publicFace 將指示牌轉成公開牌面的投影。
     * @return 日麻詳情欄位。
     */
    internal fun riichiDetails(
        state: TableState,
        handValue: HandValueResult,
        publicFace: (TileValue) -> TileFaceView,
    ): List<WinSettlementDetailField> {
        val result = handValue as? RiichiHandValueResult ?: return emptyList()
        val indicators = state.riichiWallProgress().getDoraIndicators(state)
        return buildList {
            add(
                element = WinSettlementDetailField(
                    id = RiichiWinSettlementIds.YAKU_FIELD,
                    value = WinSettlementDetailValue.Entries(
                        entries = result.yakuResults.map { yaku ->
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
                    element = WinSettlementDetailField(
                        id = RiichiWinSettlementIds.YAKUMAN_TOTAL_FIELD,
                        value = WinSettlementDetailValue.Quantities(listOf(WinSettlementQuantity(RiichiWinSettlementIds.YAKUMAN, -result.totalHan))),
                    ),
                )
            }
            add(WinSettlementDetailField(RiichiWinSettlementIds.DORA_FIELD, WinSettlementDetailValue.TileFaces(indicators.first.map { publicFace(it.toValue()) })))
            // 只使用本次權威役種結果辨識立直資格，不從其他玩家的立直狀態推測。
            if (!result.isYakuman && result.yakuResults.any { it.yaku == RiichiYakuType.Riichi || it.yaku == RiichiYakuType.DoubleRiichi }) {
                add(WinSettlementDetailField(RiichiWinSettlementIds.URA_DORA_FIELD, WinSettlementDetailValue.TileFaces(indicators.second.map { publicFace(it.toValue()) })))
            }
        }
    }

    /**
     * 建立一般胡牌的翻符；滿貫以上沒有權威符數時只有翻數。
     *
     * @param totalHan 結算結果中的總翻數。
     * @param totalFu 結算結果中的總符數；不大於 0 時不輸出符數欄位。
     * @return 翻數與可用符數組成的詳情值。
     */
    internal fun riichiHanFuValue(totalHan: Int, totalFu: Int): WinSettlementDetailValue.Quantities = WinSettlementDetailValue.Quantities(
        quantities = listOfNotNull(
            WinSettlementQuantity(RiichiWinSettlementIds.HAN, totalHan),
            WinSettlementQuantity(RiichiWinSettlementIds.FU, totalFu).takeIf { totalFu > 0 },
        ),
    )
}
