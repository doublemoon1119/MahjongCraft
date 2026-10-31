package com.doublemoon1119.mahjongcraft.platform.minecraft.table

import com.doublemoon1119.mahjongcraft.platform.minecraft.api.table.MahjongTableFacing
import kotlin.test.Test
import kotlin.test.assertEquals

/** 驗證桌面朝向以穩定名稱解析，未知值不會因序號變更而誤判。 */
class MahjongTableFacingNameTest {
    /** 已知名稱應可解析回原本朝向。 */
    @Test
    fun `known names round trip`() {
        MahjongTableFacing.entries.forEach { facing ->
            assertEquals(facing, mahjongTableFacingFromNameOrDefault(facing.name))
        }
    }

    /** 未知或缺失名稱應安全退回北向。 */
    @Test
    fun `unknown names use north as safe default`() {
        assertEquals(MahjongTableFacing.NORTH, mahjongTableFacingFromNameOrDefault("INVALID"))
        assertEquals(MahjongTableFacing.NORTH, mahjongTableFacingFromNameOrDefault(null))
    }
}
