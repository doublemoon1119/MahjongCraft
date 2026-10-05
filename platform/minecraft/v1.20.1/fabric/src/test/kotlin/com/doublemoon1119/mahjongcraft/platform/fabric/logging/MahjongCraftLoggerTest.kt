package com.doublemoon1119.mahjongcraft.platform.fabric.logging

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse

/** 驗證本 mod logger 的命名格式。 */
class MahjongCraftLoggerTest {
    /** logger 名稱為 `MahjongCraft/<類別名>`，不含 `.`，只印最後一段的 log 格式也會顯示完整名稱。 */
    @Test
    fun `logger name combines the mod name and the owner class name`() {
        val name = mahjongCraftLogger(MahjongCraftLoggerTest::class).name

        assertEquals("MahjongCraft/MahjongCraftLoggerTest", name)
        assertFalse('.' in name)
    }
}
