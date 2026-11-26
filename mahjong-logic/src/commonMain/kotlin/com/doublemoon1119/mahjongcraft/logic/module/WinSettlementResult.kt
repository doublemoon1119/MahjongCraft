package com.doublemoon1119.mahjongcraft.logic.module

import com.doublemoon1119.mahjongcraft.logic.base.NamespacedId
import com.doublemoon1119.mahjongcraft.logic.table.TableState
import kotlin.uuid.Uuid

/**
 * [MahjongRuleModule.declareTsumo]／[MahjongRuleModule.declareRon] 計算胡牌點數結算後的結果。
 *
 * - 自摸與榮和共用同一種結果形狀：贏家獲得的總點數，加上各付款玩家應付的金額。
 *   這不屬於單一地區規則，因此定義在各規則共用的 `module` package。
 * - 胡牌結算發生在胡牌當下，呼叫端持有完整的 [TableState]，因此直接以真實玩家 Uuid 作為 key，不用相對方位。
 *
 * @property totalGained 贏家本次胡牌實際獲得的點數總和，恆等於 [paymentsByPlayerId] 所有金額的加總。
 * @property paymentsByPlayerId 本次須支付點數的玩家 Uuid 對應其應付金額。
 *   - 只包含實際須付款的玩家，金額必為正整數。
 *   - 不在此 map 中的玩家本次不付款。例如規則只讓特定責任者付款時，其餘玩家不出現，而不是以 0 出現。
 * @property paymentReasonIdsByPlayerId 付款方式有別於一般結算的玩家，對應其付款原因的完整 namespaced ID。
 *   - 供呈現層向所有玩家說明「為什麼是他付」。一般付款的玩家不出現。
 *   - key 必須是 [paymentsByPlayerId] 中的玩家。
 *   - 原因 ID 的意義由提供它的規則定義。
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
        require(paymentReasonIdsByPlayerId.values.all(NamespacedId::isValid)) {
            "Payment reason IDs must be namespaced"
        }
    }
}
