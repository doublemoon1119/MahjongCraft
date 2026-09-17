package com.doublemoon1119.mahjongcraft.logic.base

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** 驗證共用 namespaced ID 格式的接受範圍。 */
class NamespacedIdTest {
    /** 合法 namespace 與含 `/`、`.`、`-`、`_` 的 path 皆被接受。 */
    @Test
    fun `well formed ids are valid`() {
        listOf(
            "mahjongcraft:riichi",
            "example_mod:flower/spring",
            "a.b-c_d:e/f.g-h_i",
            "0:9",
            "mahjongcraft:path/",
        ).forEach { value ->
            assertTrue(NamespacedId.isValid(value), "Expected valid namespaced id: $value")
        }
    }

    /** 缺少冒號、空片段、大寫、空白、非法符號、多個冒號與 namespace 含 `/` 皆被拒絕。 */
    @Test
    fun `malformed ids are invalid`() {
        listOf(
            "",
            "mahjongcraft",
            ":riichi",
            "mahjongcraft:",
            ":",
            "MahjongCraft:riichi",
            "mahjongcraft:Riichi",
            "mahjongcraft:riichi rule",
            " mahjongcraft:riichi",
            "mahjongcraft:riichi!",
            "mahjongcraft:riichi:extra",
            "mahjong/craft:riichi",
        ).forEach { value ->
            assertFalse(NamespacedId.isValid(value), "Expected invalid namespaced id: '$value'")
        }
    }

    /** 不合法時以呼叫端提供的訊息失敗。 */
    @Test
    fun `requireValid fails with the caller message`() {
        NamespacedId.requireValid("mahjongcraft:riichi") { "unused" }

        val error = assertFailsWith<IllegalArgumentException> {
            NamespacedId.requireValid("Riichi") { "Rule module ID must be namespaced: Riichi" }
        }

        assertEquals("Rule module ID must be namespaced: Riichi", error.message)
    }
}
