package com.doublemoon1119.mahjongcraft.logic.module

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.uuid.Uuid

/** 驗證胡牌結算結果的付款原因約束。 */
class WinSettlementResultTest {
    /** 沒有指定付款原因時為空。 */
    @Test
    fun `has no payment reasons by default`() {
        val payer = Uuid.random()

        assertEquals(emptyMap(), WinSettlementResult(totalGained = 1000, paymentsByPlayerId = mapOf(payer to 1000)).paymentReasonIdsByPlayerId)
    }

    /** 付款原因只能標在實際付款的玩家身上。 */
    @Test
    fun `rejects a payment reason for a player who does not pay`() {
        assertFailsWith<IllegalArgumentException> {
            WinSettlementResult(
                totalGained = 1000,
                paymentsByPlayerId = mapOf(Uuid.random() to 1000),
                paymentReasonIdsByPlayerId = mapOf(Uuid.random() to "test:reason"),
            )
        }
    }

    /** 付款原因 ID 必須帶命名空間。 */
    @Test
    fun `rejects a payment reason without a namespace`() {
        val payer = Uuid.random()

        assertFailsWith<IllegalArgumentException> {
            WinSettlementResult(
                totalGained = 1000,
                paymentsByPlayerId = mapOf(payer to 1000),
                paymentReasonIdsByPlayerId = mapOf(payer to "reason"),
            )
        }
    }
}
