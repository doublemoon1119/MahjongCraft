package com.doublemoon1119.mahjongcraft.logic.rules.taiwan

import com.doublemoon1119.mahjongcraft.logic.base.IdentifiedTile
import com.doublemoon1119.mahjongcraft.logic.module.WinSettlementResult
import com.doublemoon1119.mahjongcraft.logic.table.TableState
import kotlin.uuid.Uuid

/** 台灣麻將獨立花胡的資格與付款計算。 */
object TaiwanSpecialFlowerWin {
    /**
     * 一次已成立、尚待玩家接受的花胡資格。
     *
     * @property type 八仙過海或七搶一。
     * @property winnerId 可以選擇花胡的玩家。
     * @property payerId 七搶一的被搶花玩家；八仙過海由三家付款，因此為 null。
     * @property claimedFlower 七搶一要轉給贏家的花牌；八仙過海為 null。
     * @property replacementPlayerId 接受花胡後取得補牌的玩家。
     */
    data class Opportunity(
        val type: TaiwanTaiType,
        val winnerId: Uuid,
        val payerId: Uuid?,
        val claimedFlower: IdentifiedTile?,
        val replacementPlayerId: Uuid,
    )

    /**
     * 在一張花牌公開後判斷是否出現花胡資格。
     *
     * 八仙過海優先於七搶一。同時存在多位七花玩家時，依桌上座位順序選擇第一位，讓相同桌況得到穩定結果。
     *
     * @param tableState 已將新花牌放進公開花區的桌況。
     * @param revealingPlayerId 本次公開花牌的玩家。
     * @param revealedFlower 本次剛公開的花牌。
     * @return 成立的花胡資格；沒有資格時為 null。
     */
    fun findOpportunity(
        tableState: TableState,
        revealingPlayerId: Uuid,
        revealedFlower: IdentifiedTile,
    ): Opportunity? {
        val revealer = tableState.players.firstOrNull { it.id == revealingPlayerId } ?: return null
        val revealerFlowers = flowersOf(revealer.id, tableState)
        if (revealedFlower.id !in revealerFlowers.map { it.id }) return null
        if (revealerFlowers.size == 8) {
            return Opportunity(
                type = TaiwanTaiType.EIGHT_FLOWERS,
                winnerId = revealer.id,
                payerId = null,
                claimedFlower = null,
                replacementPlayerId = revealer.id,
            )
        }
        tableState.players.firstOrNull { player ->
            player.id != revealer.id && flowersOf(player.id, tableState).size == 7
        }?.let { winner ->
            return Opportunity(
                type = TaiwanTaiType.SEVEN_FLOWERS,
                winnerId = winner.id,
                payerId = revealer.id,
                claimedFlower = revealedFlower,
                replacementPlayerId = winner.id,
            )
        }
        if (revealerFlowers.size != 7) return null
        val payer = tableState.players.firstOrNull { player ->
            player.id != revealer.id && flowersOf(player.id, tableState).size == 1
        } ?: return null
        return Opportunity(
            type = TaiwanTaiType.SEVEN_FLOWERS,
            winnerId = revealer.id,
            payerId = payer.id,
            claimedFlower = flowersOf(payer.id, tableState).single(),
            replacementPlayerId = revealer.id,
        )
    }

    /**
     * 計算已接受花胡的付款。
     *
     * @param tableState 花胡成立時的桌況。
     * @param opportunity 已確認的花胡資格。
     * @return 固定八台加上莊家與連莊關係的付款結果。
     */
    fun settle(tableState: TableState, opportunity: Opportunity): WinSettlementResult {
        val config = tableState.config as? TaiwanRuleConfig
            ?: error("Taiwan special flower wins require Taiwan rule config")
        return TaiwanPaymentCalculator.calculate(
            config = config.scoreConfig,
            playerIds = tableState.players.map { it.id },
            winnerId = opportunity.winnerId,
            dealerId = tableState.dealerPlayerId,
            handTai = opportunity.type.tai,
            dealerStreak = tableState.comboCount,
            discarderId = opportunity.payerId,
        )
    }

    /** 取得玩家已公開的花牌。 */
    private fun flowersOf(playerId: Uuid, tableState: TableState): List<IdentifiedTile> = tableState.players
        .first { it.id == playerId }
        .tileAreas[TaiwanPlayerTileAreaIds.FLOWERS]
        ?.tiles
        .orEmpty()
}
