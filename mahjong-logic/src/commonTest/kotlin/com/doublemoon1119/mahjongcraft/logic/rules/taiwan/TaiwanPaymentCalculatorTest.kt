package com.doublemoon1119.mahjongcraft.logic.rules.taiwan

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.uuid.Uuid

/** 逐筆付款、零台、花胡及數值上限的行為測試。 */
class TaiwanPaymentCalculatorTest {
    /** 四位互不相同的玩家。 */
    private val players = List(4) { Uuid.random() }

    /** 底30、每台10的既有預設。 */
    private val config = TaiwanScoreConfig()

    /** 四個莊家座位都必須按付款關係加台。 */
    @Test
    fun `self draw payments depend on the dealer relation for every seat`() {
        players.forEach { dealer ->
            players.forEach { winner ->
                val result = TaiwanPaymentCalculator.calculate(
                    config = config,
                    playerIds = players,
                    winnerId = winner,
                    dealerId = dealer,
                    handTai = 2,
                    dealerStreak = 1,
                    discarderId = null,
                )
                val expected = players.filter { it != winner }.associateWith { payer -> if (winner == dealer || payer == dealer) 80 else 50 }
                assertEquals(expected, result.paymentsByPlayerId)
                assertEquals(if (winner == dealer) 240 else 180, result.totalGained)
                assertEquals(result.paymentsByPlayerId.values.sum(), result.totalGained)
            }
        }
    }

    /** 放槍只有一位付款，旁觀的莊家不會讓兩位閒家多付。 */
    @Test
    fun `discard wins charge only the discarder and only the applicable dealer tai`() {
        players.forEach { dealer ->
            players.forEach { winner ->
                players.filter { it != winner }.forEach { payer ->
                    val result = TaiwanPaymentCalculator.calculate(
                        config = config,
                        playerIds = players,
                        winnerId = winner,
                        dealerId = dealer,
                        handTai = 2,
                        dealerStreak = 1,
                        discarderId = payer,
                    )
                    val expected = if (dealer == winner || dealer == payer) 80 else 50
                    assertEquals(mapOf(payer to expected), result.paymentsByPlayerId)
                    assertEquals(expected, result.totalGained)
                }
            }
        }
    }

    /** 零台與初莊仍有底，莊家加一台。 */
    @Test
    fun `zero tai and initial dealer use base plus one dealer tai`() {
        val result = TaiwanPaymentCalculator.calculate(
            config = config,
            playerIds = players,
            winnerId = players[1],
            dealerId = players[0],
            handTai = 0,
            dealerStreak = 0,
            discarderId = null,
        )
        assertEquals(mapOf(players[0] to 40, players[2] to 30, players[3] to 30), result.paymentsByPlayerId)
        assertEquals(100, result.totalGained)
    }

    /** 固定8台的花胡不額外混入手牌台，付款方由既有結算參數指定。 */
    @Test
    fun `eight flower win and seven flower claim retain distinct payment structures`() {
        val eight = TaiwanPaymentCalculator.calculate(
            config = config,
            playerIds = players,
            winnerId = players[1],
            dealerId = players[0],
            handTai = TaiwanTaiType.EIGHT_FLOWERS.tai,
            dealerStreak = 0,
            discarderId = null,
        )
        assertEquals(mapOf(players[0] to 120, players[2] to 110, players[3] to 110), eight.paymentsByPlayerId)
        val seven = TaiwanPaymentCalculator.calculate(
            config = config,
            playerIds = players,
            winnerId = players[1],
            dealerId = players[0],
            handTai = TaiwanTaiType.SEVEN_FLOWERS.tai,
            dealerStreak = 0,
            discarderId = players[2],
        )
        assertEquals(mapOf(players[2] to 110), seven.paymentsByPlayerId)
    }

    /** 錯誤身分與負值不會變成一筆有效付款。 */
    @Test
    fun `invalid seats and negative values are rejected`() {
        assertFailsWith<IllegalArgumentException> {
            TaiwanPaymentCalculator.calculate(
                config = config,
                playerIds = players,
                winnerId = players[1],
                dealerId = players[0],
                handTai = -1,
                dealerStreak = 0,
                discarderId = null,
            )
        }
        assertFailsWith<IllegalArgumentException> {
            TaiwanPaymentCalculator.calculate(
                config = config,
                playerIds = players,
                winnerId = players[1],
                dealerId = players[0],
                handTai = 1,
                dealerStreak = -1,
                discarderId = null,
            )
        }
        assertFailsWith<IllegalArgumentException> {
            TaiwanPaymentCalculator.calculate(
                config = config,
                playerIds = players,
                winnerId = players[1],
                dealerId = players[0],
                handTai = 1,
                dealerStreak = 0,
                discarderId = players[1],
            )
        }
        assertFailsWith<IllegalArgumentException> {
            TaiwanPaymentCalculator.calculate(
                config = config,
                playerIds = players,
                winnerId = Uuid.random(),
                dealerId = players[0],
                handTai = 1,
                dealerStreak = 0,
                discarderId = null,
            )
        }
        assertFailsWith<IllegalArgumentException> {
            TaiwanPaymentCalculator.calculate(
                config = config,
                playerIds = List(4) { players[0] },
                winnerId = players[0],
                dealerId = players[0],
                handTai = 1,
                dealerStreak = 0,
                discarderId = null,
            )
        }
    }

    /** 單筆乘加或三家加總溢位都必須安全拒絕。 */
    @Test
    fun `overflow in individual payments and total receipts is rejected`() {
        assertFailsWith<IllegalArgumentException> {
            TaiwanPaymentCalculator.calculate(
                config = config,
                playerIds = players,
                winnerId = players[0],
                dealerId = players[0],
                handTai = Int.MAX_VALUE,
                dealerStreak = Int.MAX_VALUE,
                discarderId = null,
            )
        }
        val largeBase = config.copy(baseScore = 1_000_000_000, pointPerTai = 0)
        assertFailsWith<IllegalArgumentException> {
            TaiwanPaymentCalculator.calculate(
                config = largeBase,
                playerIds = players,
                winnerId = players[0],
                dealerId = players[0],
                handTai = 0,
                dealerStreak = 0,
                discarderId = null,
            )
        }
        val free = config.copy(baseScore = 0, pointPerTai = 0)
        val result = TaiwanPaymentCalculator.calculate(
            config = free,
            playerIds = players,
            winnerId = players[0],
            dealerId = players[0],
            handTai = 0,
            dealerStreak = 0,
            discarderId = null,
        )
        assertEquals(0, result.totalGained)
        assertTrueEmpty(result.paymentsByPlayerId)
    }

    /** 零付款不留下假的付款玩家。 */
    private fun assertTrueEmpty(payments: Map<Uuid, Int>) = assertEquals(emptyMap(), payments)
}
