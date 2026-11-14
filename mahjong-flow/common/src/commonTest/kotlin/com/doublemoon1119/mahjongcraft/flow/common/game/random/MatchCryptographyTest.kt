package com.doublemoon1119.mahjongcraft.flow.common.game.random

import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFalse

/** 驗證平台提供的 HMAC-SHA256 與安全亂數符合標準。 */
class MatchCryptographyTest {
    /** 驗證 RFC 4231 測試案例 1 與 2 的 HMAC-SHA256 結果。 */
    @Test
    fun `hmac sha256 matches RFC 4231 vectors`() {
        assertContentEquals(
            expected = "b0344c61d8db38535ca8afceaf0bf12b881dc200c9833da726e9376c2e32cff7".hexToByteArray(),
            actual = hmacSha256(key = ByteArray(20) { 0x0b }, message = "Hi There".encodeToByteArray()),
        )
        assertContentEquals(
            expected = "5bdcc146bf60754e6a042426089575c75a003f089d2739839dec58b964ec3843".hexToByteArray(),
            actual = hmacSha256(key = "Jefe".encodeToByteArray(), message = "what do ya want for nothing?".encodeToByteArray()),
        )
    }

    /** 驗證安全亂數回傳要求的長度，且兩次結果不同。 */
    @Test
    fun `secure random bytes have the requested size and differ`() {
        val first = secureRandomBytes(MatchSeed.SIZE_BYTES)
        val second = secureRandomBytes(MatchSeed.SIZE_BYTES)
        assertEquals(MatchSeed.SIZE_BYTES, first.size)
        assertFalse(first.contentEquals(second))
    }
}
