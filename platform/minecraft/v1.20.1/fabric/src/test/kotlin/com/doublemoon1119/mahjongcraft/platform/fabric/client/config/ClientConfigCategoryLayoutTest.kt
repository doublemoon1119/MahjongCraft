package com.doublemoon1119.mahjongcraft.platform.fabric.client.config

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** 小畫面分類列數改變時，欄位起點仍位於最後一排按鈕下方。 */
class ClientConfigCategoryLayoutTest {
    @Test
    fun `five categories use three compact rows`() {
        assertEquals(3, ClientConfigCategoryLayout.compactRowCount(5))
        val top = ClientConfigCategoryLayout.compactFieldsTop(panelTop = 12, categoryCount = 5)
        val lastButtonTop = 12 + ClientConfigCategoryLayout.CATEGORY_TOP_OFFSET +
            2 * ClientConfigCategoryLayout.CATEGORY_ROW_HEIGHT
        assertTrue(top >= lastButtonTop + 20)
    }

    @Test
    fun `four categories use only two compact rows`() {
        assertEquals(2, ClientConfigCategoryLayout.compactRowCount(4))
        val fourTop = ClientConfigCategoryLayout.compactFieldsTop(panelTop = 0, categoryCount = 4)
        val fiveTop = ClientConfigCategoryLayout.compactFieldsTop(panelTop = 0, categoryCount = 5)
        assertEquals(ClientConfigCategoryLayout.CATEGORY_ROW_HEIGHT, fiveTop - fourTop)
    }
}
