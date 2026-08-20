package com.doublemoon1119.mahjongcraft.logic.module

import com.doublemoon1119.mahjongcraft.logic.rules.riichi.PaoLiability
import com.doublemoon1119.mahjongcraft.logic.table.TableState
import kotlin.uuid.Uuid

/**
 * [MahjongRuleModule.declareTsumo]／[MahjongRuleModule.declareRon] 計算胡牌點數結算後的結果。
 *
 * 胡牌結算本身不是任何單一地區規則專屬的概念——只是恰好由自摸與榮和共用同一種形狀（贏家獲得
 * 總點數 + 各付款玩家應付金額），因此定義在 `:mahjong-logic` 各地區規則共用的 `module` package，
 * 而非某個地區規則自己的 package 底下（與 [RiichiDeclarationResult] 同理）。
 *
 * 與 [RiichiDeclarationResult] 不同的是，這裡直接以真實玩家 Uuid 作為 key，而非相對方位——
 * 因為胡牌結算發生在胡牌當下，呼叫端本來就已經拿著完整的 [TableState]，
 * 不需要像 [PaoLiability] 那樣延遲到結算時才解析身分。
 *
 * @property totalGained 贏家本次胡牌實際獲得的點數總和，恆等於 [paymentsByPlayerId] 所有金額的加總。
 * @property paymentsByPlayerId 本次須支付點數的玩家 Uuid 對應其應付金額。只包含實際須付款的玩家
 *   （金額必為正整數）；未列於此 map 中的玩家視為本次無需支付（例如包牌自摸時，只有包牌責任者
 *   在此 map 中，其餘玩家不出現，而非以 0 的形式出現）。
 */
data class WinSettlementResult(
    val totalGained: Int,
    val paymentsByPlayerId: Map<Uuid, Int>,
)
