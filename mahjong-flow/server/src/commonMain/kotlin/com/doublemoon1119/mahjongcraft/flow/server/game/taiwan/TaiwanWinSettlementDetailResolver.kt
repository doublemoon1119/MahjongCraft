package com.doublemoon1119.mahjongcraft.flow.server.game.taiwan

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
import com.doublemoon1119.mahjongcraft.logic.rules.taiwan.TaiwanHandValueResult
import com.doublemoon1119.mahjongcraft.logic.rules.taiwan.TaiwanPlayerTileAreaIds
import com.doublemoon1119.mahjongcraft.logic.table.TableState
import kotlin.uuid.Uuid

/**
 * 台灣麻將的胡牌詳情解析器：贏家公開的花牌、成立的台種、莊家與連莊台，以及合計台數。
 *
 * 莊家台依付款規則只在贏家或付款者是莊家時計入：莊家胡牌或閒家胡莊家的牌時每一份付款都計入；閒家自摸時只有莊家那一份
 * 計入，條目標為只適用部分付款者，合計另外列出莊家那一份；閒家胡閒家的牌時不列。
 */
object TaiwanWinSettlementDetailResolver : WinSettlementDetailResolver {
    override fun resolve(
        state: TableState,
        winnerId: Uuid,
        responsiblePlayerId: Uuid?,
        handValue: HandValueResult,
        publicFace: (TileValue) -> TileFaceView,
    ): List<WinSettlementDetailField> {
        val taiwanValue = handValue as? TaiwanHandValueResult
        val handEntries = taiwanValue?.taiResults.orEmpty().map { result -> TaiwanWinSettlementIds.taiEntry(result.type, result.tai) }
        return listOfNotNull(flowersField(flowersOf(state, winnerId), publicFace)) +
            taiFields(state, winnerId, responsiblePlayerId, handEntries, taiwanValue?.totalTai ?: 0)
    }

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
        ) + taiFields(state, winnerId, outcome.responsiblePlayerIds.singleOrNull(), listOf(taiEntry(detail.taiTypeId, detail.tai)), detail.tai)
    }

    /**
     * 台種清單與合計台數欄位，依付款規則加上莊家與連莊那一條。
     *
     * @param state 胡牌成立時的桌況。
     * @param winnerId 贏家。
     * @param responsiblePlayerId 單獨付款者；其他三家一起付款時為 null。
     * @param handEntries 手牌或花胡成立的台種。
     * @param handTai 不含莊家台的台數。
     * @return 台種清單、合計台數，以及閒家自摸時莊家那一份的合計。
     */
    private fun taiFields(
        state: TableState,
        winnerId: Uuid,
        responsiblePlayerId: Uuid?,
        handEntries: List<WinSettlementDetailEntry>,
        handTai: Int,
    ): List<WinSettlementDetailField> {
        val dealerId = state.dealerPlayerId
        val streak = state.comboCount
        val dealerTai = 1 + 2 * streak
        val dealerPaysAlone = winnerId != dealerId && responsiblePlayerId == null
        val dealerApplies = winnerId == dealerId || responsiblePlayerId == dealerId || dealerPaysAlone
        val dealerEntry = if (dealerApplies) {
            WinSettlementDetailEntry(
                id = if (streak == 0) TaiwanWinSettlementIds.DEALER_ENTRY else TaiwanWinSettlementIds.DEALER_STREAK_ENTRY,
                quantity = WinSettlementQuantity(TaiwanWinSettlementIds.TAI_UNIT, dealerTai),
                nameQuantity = WinSettlementQuantity(TaiwanWinSettlementIds.DEALER_STREAK_UNIT, streak).takeIf { streak > 0 },
                appliesToSomePayersOnly = dealerPaysAlone,
            )
        } else {
            null
        }
        val entries = handEntries + listOfNotNull(dealerEntry)
        val total = if (dealerApplies && !dealerPaysAlone) handTai + dealerTai else handTai
        return listOfNotNull(
            entries.takeIf { it.isNotEmpty() }?.let { WinSettlementDetailField(TaiwanWinSettlementIds.TAI_FIELD, WinSettlementDetailValue.Entries(it)) },
            WinSettlementDetailField(TaiwanWinSettlementIds.TAI_TOTAL_FIELD, taiQuantities(total)),
            if (dealerPaysAlone) WinSettlementDetailField(TaiwanWinSettlementIds.DEALER_TAI_TOTAL_FIELD, taiQuantities(handTai + dealerTai)) else null,
        )
    }

    /**
     * 一條台種。
     *
     * @param id 台種識別碼。
     * @param tai 台數。
     * @return 清單條目。
     */
    private fun taiEntry(id: String, tai: Int) = WinSettlementDetailEntry(id, WinSettlementQuantity(TaiwanWinSettlementIds.TAI_UNIT, tai))

    /**
     * 只有台數的數值欄位值。
     *
     * @param tai 台數。
     * @return 數值欄位值。
     */
    private fun taiQuantities(tai: Int) = WinSettlementDetailValue.Quantities(listOf(WinSettlementQuantity(TaiwanWinSettlementIds.TAI_UNIT, tai)))

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
