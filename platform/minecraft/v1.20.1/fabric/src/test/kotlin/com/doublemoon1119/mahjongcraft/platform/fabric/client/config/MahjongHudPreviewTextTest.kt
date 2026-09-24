package com.doublemoon1119.mahjongcraft.platform.fabric.client.config

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** 驗證 HUD 預覽框文字的截斷規則；寬度以固定字寬模擬，不依賴實際字型。 */
class MahjongHudPreviewTextTest {
    /** 每個字元固定寬度的假量測，讓斷言可以直接以字數表示寬度。 */
    private val widthOf: (String) -> Int = { it.length * CHARACTER_WIDTH }

    /** 放得下的文字完全不動，不會無故補上省略號。 */
    @Test
    fun `text that fits is returned unchanged`() {
        val text = "Auto Controls"

        assertEquals(text, trimPreviewText(text, widthOf(text), widthOf))
    }

    /** 放不下時截斷並補省略號，且結果含省略號仍在可用寬度內。 */
    @Test
    fun `text that overflows is trimmed and marked with an ellipsis`() {
        val text = "Automatic control status"
        val maxWidth = widthOf("Automatic")

        val trimmed = trimPreviewText(text, maxWidth, widthOf)

        assertTrue(trimmed.endsWith(PREVIEW_ELLIPSIS), "$trimmed must end with the ellipsis")
        assertTrue(text.startsWith(trimmed.removeSuffix(PREVIEW_ELLIPSIS)), "$trimmed must be a prefix of the original text")
        assertTrue(widthOf(trimmed) <= maxWidth, "$trimmed must fit into $maxWidth")
    }

    /** 連省略號都放不下的極窄預覽框只畫省略號，不畫出任何超出寬度的內容。 */
    @Test
    fun `a width narrower than the ellipsis leaves only the ellipsis`() {
        assertEquals(PREVIEW_ELLIPSIS, trimPreviewText("Auto Controls", 0, widthOf))
        assertEquals(PREVIEW_ELLIPSIS, trimPreviewText("Auto Controls", widthOf(PREVIEW_ELLIPSIS), widthOf))
    }

    private companion object {
        /** 假量測使用的固定字寬。 */
        const val CHARACTER_WIDTH = 6
    }
}
