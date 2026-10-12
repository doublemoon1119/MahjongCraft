package com.doublemoon1119.mahjongcraft.platform.minecraft.dice

import kotlin.test.Test
import kotlin.test.assertEquals

/** 驗證桌面朝向以穩定名稱解析，未知值不會因序號變更而誤判。 */
class MahjongTableFacingNameTest {
    /** 已知名稱應可解析回原本朝向。 */
    @Test
    fun `known names round trip`() {
        MahjongTableFacing.entries.forEach { facing ->
            assertEquals(facing, MahjongTableFacing.fromNameOrDefault(facing.name))
        }
    }

    /** 未知或缺失名稱應安全退回北向。 */
    @Test
    fun `unknown names use north as safe default`() {
        assertEquals(MahjongTableFacing.NORTH, MahjongTableFacing.fromNameOrDefault("INVALID"))
        assertEquals(MahjongTableFacing.NORTH, MahjongTableFacing.fromNameOrDefault(null))
    }
}
