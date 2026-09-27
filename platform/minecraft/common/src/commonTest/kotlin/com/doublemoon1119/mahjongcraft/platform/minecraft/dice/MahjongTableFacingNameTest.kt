package com.doublemoon1119.mahjongcraft.platform.minecraft.dice

import kotlin.test.Test
import kotlin.test.assertEquals

/** 驗證桌面朝向以穩定名稱解析，未知值不會因序號變更而誤判。 */
class MahjongTableFacingNameTest {
    @Test
    fun `known names round trip`() {
        MahjongTableFacing.entries.forEach { facing ->
            assertEquals(facing, MahjongTableFacing.fromNameOrDefault(facing.name))
        }
    }

    @Test
    fun `unknown names use north as safe default`() {
        assertEquals(MahjongTableFacing.NORTH, MahjongTableFacing.fromNameOrDefault("INVALID"))
        assertEquals(MahjongTableFacing.NORTH, MahjongTableFacing.fromNameOrDefault(null))
    }
}
