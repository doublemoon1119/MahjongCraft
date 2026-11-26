package com.doublemoon1119.mahjongcraft.platform.minecraft.mod.loader

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

/** 驗證 mod 中繼資料宣告的類別名稱讀取。 */
class ModPropertyClassesTest {
    /** 沒有宣告、單一類別與類別陣列都能讀出。 */
    @Test
    fun `reads a class name or a list of class names`() {
        assertEquals(emptyList(), ModPropertyClasses.classNames("example", PROPERTY, null))
        assertEquals(listOf("com.example.One"), ModPropertyClasses.classNames("example", PROPERTY, " com.example.One "))
        assertEquals(
            expected = listOf("com.example.One", "com.example.Two"),
            actual = ModPropertyClasses.classNames("example", PROPERTY, listOf("com.example.One", "com.example.Two")),
        )
    }

    /** 不是字串或字串陣列的值會被拒絕。 */
    @Test
    fun `rejects values that are not class names`() {
        assertFailsWith<IllegalStateException> { ModPropertyClasses.classNames("example", PROPERTY, 1) }
        assertFailsWith<IllegalStateException> { ModPropertyClasses.classNames("example", PROPERTY, listOf("com.example.One", 2)) }
    }

    private companion object {
        /** 測試使用的屬性名稱。 */
        const val PROPERTY = "mahjongcraft:extension"
    }
}
