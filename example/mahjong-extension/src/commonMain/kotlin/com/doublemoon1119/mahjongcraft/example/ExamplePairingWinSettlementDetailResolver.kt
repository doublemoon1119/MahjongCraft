package com.doublemoon1119.mahjongcraft.example

import com.doublemoon1119.mahjongcraft.example.pairing.ExamplePairingComponents
import com.doublemoon1119.mahjongcraft.example.pairing.ExamplePairingHandValueResult
import com.doublemoon1119.mahjongcraft.example.pairing.ExamplePairingRuleConfig
import com.doublemoon1119.mahjongcraft.example.pairing.ExamplePairingTileType
import com.doublemoon1119.mahjongcraft.flow.common.game.model.WinSettlementDetailField
import com.doublemoon1119.mahjongcraft.flow.common.game.model.WinSettlementDetailValue
import com.doublemoon1119.mahjongcraft.flow.common.game.model.WinSettlementQuantity
import com.doublemoon1119.mahjongcraft.flow.server.game.service.WinSettlementDetailResolver
import com.doublemoon1119.mahjongcraft.logic.base.TileFaceView
import com.doublemoon1119.mahjongcraft.logic.base.TileValue
import com.doublemoon1119.mahjongcraft.logic.judgment.HandValueResult
import com.doublemoon1119.mahjongcraft.logic.table.TableState

/** 將簡易配對的實際付款設定放入結算與歷史詳情，不套用其他規則的翻符資料。 */
object ExamplePairingWinSettlementDetailResolver : WinSettlementDetailResolver {
    /** 每位付款者點數的詳情欄位。 */
    const val PAYMENT_PER_PLAYER = "mahjongcraft_example:pairing/payment_per_player"

    /** 本局公開的加分牌面清單，不引用局內牌張。 */
    const val BONUS_TILES = "mahjongcraft_example:pairing/bonus_tiles"

    /** 點數的語意單位。 */
    const val POINTS = "mahjongcraft_example:pairing/points"

    /** 自摸命中的不同加分牌種類數量欄位。 */
    const val BONUS_TILE_COUNT = "mahjongcraft_example:pairing/bonus_tile_count"

    override fun resolve(
        state: TableState,
        handValue: HandValueResult,
        publicFace: (TileValue) -> TileFaceView,
    ): List<WinSettlementDetailField> {
        val config = state.config as? ExamplePairingRuleConfig ?: return emptyList()
        val bonusTileCount = (handValue as? ExamplePairingHandValueResult)?.bonusTileCount ?: 0
        val bonusFaces = requireNotNull(state.components[ExamplePairingComponents.BONUS_TILES]) { "Example pairing bonus tiles are required for settlement details" }.values.sorted().map { publicFace(TileValue(ExamplePairingTileType.bonusFace(it))) }
        return listOf(
            WinSettlementDetailField(BONUS_TILES, WinSettlementDetailValue.TileFaces(bonusFaces)),
            WinSettlementDetailField(
                id = PAYMENT_PER_PLAYER,
                value = WinSettlementDetailValue.Quantities(
                    quantities = listOf(WinSettlementQuantity(unitId = POINTS, amount = config.paymentPerPlayer + bonusTileCount)),
                ),
            ),
            WinSettlementDetailField(
                id = BONUS_TILE_COUNT,
                value = WinSettlementDetailValue.Quantities(
                    quantities = listOf(WinSettlementQuantity(unitId = POINTS, amount = bonusTileCount)),
                ),
            ),
        )
    }
}
