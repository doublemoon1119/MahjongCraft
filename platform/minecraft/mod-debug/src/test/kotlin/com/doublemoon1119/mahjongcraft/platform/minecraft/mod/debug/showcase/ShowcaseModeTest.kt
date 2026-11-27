package com.doublemoon1119.mahjongcraft.platform.minecraft.mod.debug.showcase

import kotlin.io.path.Path
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/** 驗證宣傳截圖模式的系統屬性解析。 */
class ShowcaseModeTest {
    /** 只有 true（不分大小寫）開啟宣傳截圖模式。 */
    @Test
    fun `only true turns the showcase on`() {
        listOf(null, "", "false", "all").forEach { value -> assertNull(ShowcaseMode.parse(value, title = null), "value '$value'") }
        assertEquals(ShowcaseMode(titleImage = null), ShowcaseMode.parse(" TRUE ", title = null))
    }

    /** 有指定標題圖片時記下它的位置，空白時視為沒有指定。 */
    @Test
    fun `the title image is read when given`() {
        assertEquals(ShowcaseMode(titleImage = Path("docs/assets/branding/title.png")), ShowcaseMode.parse("true", title = "docs/assets/branding/title.png"))
        assertEquals(ShowcaseMode(titleImage = null), ShowcaseMode.parse("true", title = "  "))
    }
}
