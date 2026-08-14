package com.doublemoon1119.mahjongcraft.logic.base

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

/** 驗證擴充牌種穩定 ID 的格式與解析行為。 */
class TileTypeIdTest {
    /** 驗證合法的 namespace 與分段 path 可往返穩定字串。 */
    @Test
    fun `valid tile type id round trips through string`() {
        val id = TileTypeId.parse("mahjongcraft:taiwan/spring")

        assertEquals("mahjongcraft", id.namespace)
        assertEquals("taiwan/spring", id.path)
        assertEquals("mahjongcraft:taiwan/spring", id.toString())
    }

    /** 驗證空白、大寫、空 path 片段及多餘冒號皆不會形成不穩定 ID。 */
    @Test
    fun `invalid tile type ids are rejected`() {
        listOf(
            "mahjongcraft",
            ":spring",
            "mahjongcraft:",
            "MahjongCraft:spring",
            "mahjongcraft:taiwan//spring",
            "mahjongcraft:taiwan/spring/",
            "mahjongcraft:taiwan:spring",
        ).forEach { value ->
            assertFailsWith<IllegalArgumentException>(value) { TileTypeId.parse(value) }
        }
    }
}
