package com.doublemoon1119.mahjongcraft.platform.forge.loader

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

/** 驗證 `modproperties` 宣告的類別名稱讀取。 */
class ForgeModPropertyClassesTest {
    /** 沒有宣告、單一類別與類別陣列都能讀出。 */
    @Test
    fun `reads a class name or a list of class names`() {
        assertEquals(emptyList(), ForgeModPropertyClasses.classNames("example", PROPERTY, null))
        assertEquals(listOf("com.example.One"), ForgeModPropertyClasses.classNames("example", PROPERTY, " com.example.One "))
        assertEquals(
            expected = listOf("com.example.One", "com.example.Two"),
            actual = ForgeModPropertyClasses.classNames("example", PROPERTY, listOf("com.example.One", "com.example.Two")),
        )
    }

    /** 不是字串或字串陣列的值會被拒絕。 */
    @Test
    fun `rejects values that are not class names`() {
        assertFailsWith<IllegalStateException> { ForgeModPropertyClasses.classNames("example", PROPERTY, 1) }
        assertFailsWith<IllegalStateException> { ForgeModPropertyClasses.classNames("example", PROPERTY, listOf("com.example.One", 2)) }
    }

    private companion object {
        /** 測試使用的屬性名稱。 */
        const val PROPERTY = "mahjongcraft:extension"
    }
}
