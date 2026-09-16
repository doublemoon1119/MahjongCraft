package com.doublemoon1119.mahjongcraft.logic.module

import com.doublemoon1119.mahjongcraft.logic.table.TableState
import kotlin.uuid.Uuid

/**
 * [MahjongRuleModule.declareTsumo]／[MahjongRuleModule.declareRon] 計算胡牌點數結算後的結果。
 *
 * 胡牌結算本身不是任何單一地區規則專屬的概念——只是恰好由自摸與榮和共用同一種形狀（贏家獲得
 * 總點數 + 各付款玩家應付金額），因此定義在 `:mahjong-logic` 各地區規則共用的 `module` package，
 * 而非某個地區規則自己的 package 底下。
 *
 * 胡牌結算發生在胡牌當下，呼叫端本來就持有完整的 [TableState]，因此直接以真實玩家 Uuid 作為 key，
 * 而非相對方位。
 *
 * @property totalGained 贏家本次胡牌實際獲得的點數總和，恆等於 [paymentsByPlayerId] 所有金額的加總。
 * @property paymentsByPlayerId 本次須支付點數的玩家 Uuid 對應其應付金額。只包含實際須付款的玩家
 *   （金額必為正整數）；未列於此 map 中的玩家視為本次無需支付（例如規則只讓特定責任者付款時，其餘
 *   玩家不出現，而非以 0 的形式出現）。
 * @property paymentReasonIdsByPlayerId 付款方式有別於一般結算的玩家，對應其付款原因的完整 namespaced ID，
 *   供呈現層向所有玩家說明「為什麼是他付」。一般付款的玩家不出現；key 必須是 [paymentsByPlayerId] 中的
 *   玩家。原因 ID 的意義由提供它的規則定義。
 */
data class WinSettlementResult(
    val totalGained: Int,
    val paymentsByPlayerId: Map<Uuid, Int>,
    val paymentReasonIdsByPlayerId: Map<Uuid, String> = emptyMap(),
) {
    init {
        require(paymentReasonIdsByPlayerId.keys.all { it in paymentsByPlayerId }) {
            "Payment reasons must belong to paying players"
        }
        require(paymentReasonIdsByPlayerId.values.all { ':' in it }) {
            "Payment reason IDs must be namespaced"
        }
    }
}
