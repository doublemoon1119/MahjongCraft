package com.doublemoon1119.mahjongcraft.platform.fabric.entity

import kotlin.test.Test
import kotlin.test.assertEquals

/** 驗證胡牌結算排名欄位的字串編碼。 */
class WinSettlementRankingCodecTest {
    /** 含付款原因與不含付款原因的列都能原樣讀回。 */
    @Test
    fun `round trips rankings with and without a payment reason`() {
        val rankings = listOf(
            snapshot(playerId = "a", paymentReasonId = "mahjongcraft:pao"),
            snapshot(playerId = "b", paymentReasonId = null),
        )

        assertEquals(rankings, WinSettlementRankingCodec.decode(WinSettlementRankingCodec.encode(rankings)))
    }

    /** 欄位數不符或數字格式錯誤的列直接略過，其餘列照常讀回。 */
    @Test
    fun `skips malformed rows`() {
        val valid = WinSettlementRankingCodec.encode(listOf(snapshot(playerId = "a", paymentReasonId = null)))
        val encoded = listOf(
            valid,
            listOf("b", "1", "0").joinToString(WinSettlementRankingCodec.FIELD_SEPARATOR.toString()),
            listOf("c", "x", "0", "1", "2", "3", "4", "").joinToString(WinSettlementRankingCodec.FIELD_SEPARATOR.toString()),
        ).joinToString(WinSettlementRankingCodec.ROW_SEPARATOR.toString())

        assertEquals(listOf("a"), WinSettlementRankingCodec.decode(encoded).map { it.playerId })
    }

    /** 建立測試用的排名列。 */
    private fun snapshot(
        playerId: String,
        paymentReasonId: String?,
    ) = WinSettlementRankingSnapshot(
        playerId = playerId,
        seatIndex = 1,
        isAi = true,
        previousScore = 25000,
        currentScore = 33000,
        previousRank = 2,
        currentRank = 1,
        paymentReasonId = paymentReasonId,
    )
}
