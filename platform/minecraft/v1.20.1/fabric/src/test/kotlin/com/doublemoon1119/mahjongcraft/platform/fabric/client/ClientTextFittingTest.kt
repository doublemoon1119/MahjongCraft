package com.doublemoon1119.mahjongcraft.platform.fabric.client

import net.minecraft.text.Text
import kotlin.test.Test
import kotlin.test.assertEquals

/** 驗證 client 文字裁切的寬度邊界與省略號行為。 */
class ClientTextFittingTest {
    /** 非正寬度會回傳空文字。 */
    @Test
    fun `non positive width returns empty text`() {
        assertEquals("", fitClientText(Text.literal("text"), 0) { it.string.length }.string)
    }

    /** 剛好容納的文字不會被改寫。 */
    @Test
    fun `text that fits is preserved`() {
        assertEquals("text", fitClientText(Text.literal("text"), 4) { it.string.length }.string)
    }

    /** 過寬文字會保留可容納的前綴並附加省略號。 */
    @Test
    fun `wide text receives an ellipsis`() {
        assertEquals("t...", fitClientText(Text.literal("text too wide"), 4) { it.string.length }.string)
    }
}
