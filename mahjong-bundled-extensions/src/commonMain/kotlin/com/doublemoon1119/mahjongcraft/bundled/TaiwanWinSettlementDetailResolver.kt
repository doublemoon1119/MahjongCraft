package com.doublemoon1119.mahjongcraft.bundled

import com.doublemoon1119.mahjongcraft.flow.common.game.model.ResolvedRoundOutcome
import com.doublemoon1119.mahjongcraft.flow.common.game.model.WinSettlementDetailEntry
import com.doublemoon1119.mahjongcraft.flow.common.game.model.WinSettlementDetailField
import com.doublemoon1119.mahjongcraft.flow.common.game.model.WinSettlementDetailValue
import com.doublemoon1119.mahjongcraft.flow.common.game.model.WinSettlementQuantity
import com.doublemoon1119.mahjongcraft.flow.common.game.model.taiwan.TaiwanSpecialFlowerWinDetail
import com.doublemoon1119.mahjongcraft.flow.common.game.model.taiwan.TaiwanWinSettlementIds
import com.doublemoon1119.mahjongcraft.flow.server.game.service.WinSettlementDetailResolver
import com.doublemoon1119.mahjongcraft.logic.base.IdentifiedTile
import com.doublemoon1119.mahjongcraft.logic.base.TileFaceView
import com.doublemoon1119.mahjongcraft.logic.base.TileValue
import com.doublemoon1119.mahjongcraft.logic.base.toValue
import com.doublemoon1119.mahjongcraft.logic.judgment.HandValueResult
import com.doublemoon1119.mahjongcraft.logic.rules.taiwan.TaiwanPlayerTileAreaIds
import com.doublemoon1119.mahjongcraft.logic.table.TableState
import kotlin.uuid.Uuid

/** 台灣麻將的胡牌詳情解析器：贏家公開的花牌，以及特殊花牌和牌的台種、台數與合計台數。 */
object TaiwanWinSettlementDetailResolver : WinSettlementDetailResolver {
    override fun resolve(
        state: TableState,
        winnerId: Uuid,
        handValue: HandValueResult,
        publicFace: (TileValue) -> TileFaceView,
    ): List<WinSettlementDetailField> = listOfNotNull(flowersField(flowersOf(state, winnerId), publicFace))

    override fun resolveSpecialOutcome(
        state: TableState,
        outcome: ResolvedRoundOutcome,
        publicFace: (TileValue) -> TileFaceView,
    ): List<WinSettlementDetailField>? {
        val detail = outcome.extensionDetail as? TaiwanSpecialFlowerWinDetail ?: return null
        val winnerId = outcome.beneficiaryPlayerIds.single()
        // 七搶一被搶的花仍在被搶玩家的花牌區，從整桌的花牌中找出。
        val winningFlower = state.players.flatMap { player -> flowersOf(state, player.id) }.single { it.id == detail.winningFlowerId }
        return listOfNotNull(
            flowersField(flowersOf(state, winnerId).filterNot { it.id == detail.winningFlowerId }, publicFace),
            WinSettlementDetailField(
                id = TaiwanWinSettlementIds.WINNING_FLOWER_FIELD,
                value = WinSettlementDetailValue.TileFaces(listOf(publicFace(winningFlower.toValue()))),
            ),
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
            WinSettlementDetailField(
                id = TaiwanWinSettlementIds.TAI_TOTAL_FIELD,
                value = WinSettlementDetailValue.Quantities(listOf(WinSettlementQuantity(TaiwanWinSettlementIds.TAI_UNIT, detail.tai))),
            ),
        )
    }

    /**
     * 花牌欄位；沒有花牌時為 null，不顯示這一行。
     *
     * @param flowers 依公開順序排列的花牌。
     * @param publicFace 將牌值轉成公開牌面的投影。
     * @return 花牌欄位。
     */
    private fun flowersField(flowers: List<IdentifiedTile>, publicFace: (TileValue) -> TileFaceView): WinSettlementDetailField? = flowers.takeIf { it.isNotEmpty() }?.let {
        WinSettlementDetailField(
            id = TaiwanWinSettlementIds.FLOWERS_FIELD,
            value = WinSettlementDetailValue.TileFaces(it.map { flower -> publicFace(flower.toValue()) }),
        )
    }

    /**
     * 玩家公開的花牌。
     *
     * @param state 權威桌況。
     * @param playerId 玩家。
     * @return 依公開順序排列的花牌。
     */
    private fun flowersOf(state: TableState, playerId: Uuid): List<IdentifiedTile> = state.players
        .first { it.id == playerId }
        .tileAreas[TaiwanPlayerTileAreaIds.FLOWERS]
        ?.tiles
        .orEmpty()
}
