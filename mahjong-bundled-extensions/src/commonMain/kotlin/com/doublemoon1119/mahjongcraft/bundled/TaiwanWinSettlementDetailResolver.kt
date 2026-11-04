package com.doublemoon1119.mahjongcraft.bundled

import com.doublemoon1119.mahjongcraft.flow.common.game.model.ResolvedRoundOutcome
import com.doublemoon1119.mahjongcraft.flow.common.game.model.WinSettlementDetailEntry
import com.doublemoon1119.mahjongcraft.flow.common.game.model.WinSettlementDetailField
import com.doublemoon1119.mahjongcraft.flow.common.game.model.WinSettlementDetailValue
import com.doublemoon1119.mahjongcraft.flow.common.game.model.WinSettlementQuantity
import com.doublemoon1119.mahjongcraft.flow.common.game.model.taiwan.TaiwanSpecialFlowerWinDetail
import com.doublemoon1119.mahjongcraft.flow.common.game.model.taiwan.TaiwanWinSettlementIds
import com.doublemoon1119.mahjongcraft.flow.server.game.service.WinSettlementDetailResolver
import com.doublemoon1119.mahjongcraft.logic.base.TileFaceView
import com.doublemoon1119.mahjongcraft.logic.base.TileValue
import com.doublemoon1119.mahjongcraft.logic.judgment.HandValueResult
import com.doublemoon1119.mahjongcraft.logic.table.TableState

/** 台灣麻將的胡牌詳情解析器；目前提供特殊花牌和牌的台種與台數。 */
object TaiwanWinSettlementDetailResolver : WinSettlementDetailResolver {
    override fun resolve(
        state: TableState,
        handValue: HandValueResult,
        publicFace: (TileValue) -> TileFaceView,
    ): List<WinSettlementDetailField> = emptyList()

    override fun resolveSpecialOutcome(
        state: TableState,
        outcome: ResolvedRoundOutcome,
        publicFace: (TileValue) -> TileFaceView,
    ): List<WinSettlementDetailField>? {
        val detail = outcome.extensionDetail as? TaiwanSpecialFlowerWinDetail ?: return null
        return listOf(
            WinSettlementDetailField(
                id = TaiwanWinSettlementIds.TAI_FIELD,
                value = WinSettlementDetailValue.Entries(
                    listOf(
                        WinSettlementDetailEntry(
                            id = detail.taiTypeId,
                            quantity = WinSettlementQuantity(TaiwanWinSettlementIds.TAI_UNIT, detail.tai),
                        ),
                    ),
                ),
            ),
        )
    }
}
