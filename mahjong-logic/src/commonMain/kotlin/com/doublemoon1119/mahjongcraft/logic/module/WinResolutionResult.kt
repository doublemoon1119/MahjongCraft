package com.doublemoon1119.mahjongcraft.logic.module

import com.doublemoon1119.mahjongcraft.logic.judgment.HandValueResult
import kotlin.uuid.Uuid

/**
 * 一次胡牌的完整規則結果，同時保留點數結算與產生該結算的手牌價值資料。
 *
 * [settlement] 供權威流程套用分數；[handValueResult] 供外層流程解析規則中立的呈現提示。呈現提示本身
 * 不放入 logic 層，避免規則計算依賴任何平台演出概念。
 */
data class WinResolutionResult(
    val settlement: WinSettlementResult,
    val handValueResult: HandValueResult,
) {
    /** 贏家由付款項目取得的總點數。 */
    val totalGained: Int get() = settlement.totalGained

    /** 各付款玩家與付款金額。 */
    val paymentsByPlayerId: Map<Uuid, Int> get() = settlement.paymentsByPlayerId
}
