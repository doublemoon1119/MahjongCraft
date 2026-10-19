package com.doublemoon1119.mahjongcraft.example

import com.doublemoon1119.mahjongcraft.example.pairing.ExamplePairingRuleConfig
import com.doublemoon1119.mahjongcraft.flow.common.game.model.WinSettlementDetailField
import com.doublemoon1119.mahjongcraft.flow.common.game.model.WinSettlementDetailValue
import com.doublemoon1119.mahjongcraft.flow.common.game.model.WinSettlementQuantity
import com.doublemoon1119.mahjongcraft.flow.server.game.service.WinSettlementDetailResolver
import com.doublemoon1119.mahjongcraft.logic.judgment.HandValueResult
import com.doublemoon1119.mahjongcraft.logic.table.TableState

/** 將簡易配對的實際付款設定放入結算與歷史詳情，不套用其他規則的翻符資料。 */
object ExamplePairingWinSettlementDetailResolver : WinSettlementDetailResolver {
    /** 每位付款者點數的詳情欄位。 */
    const val PAYMENT_PER_PLAYER = "mahjongcraft_example:pairing/payment_per_player"

    /** 點數的語意單位。 */
    const val POINTS = "mahjongcraft_example:pairing/points"

    override fun resolve(state: TableState, handValue: HandValueResult): List<WinSettlementDetailField> {
        val config = state.config as? ExamplePairingRuleConfig ?: return emptyList()
        return listOf(
            WinSettlementDetailField(
                id = PAYMENT_PER_PLAYER,
                value = WinSettlementDetailValue.Quantities(
                    quantities = listOf(WinSettlementQuantity(unitId = POINTS, amount = config.paymentPerPlayer)),
                ),
            ),
        )
    }
}
