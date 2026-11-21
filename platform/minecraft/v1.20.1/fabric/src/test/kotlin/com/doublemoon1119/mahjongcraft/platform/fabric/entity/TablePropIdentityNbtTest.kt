package com.doublemoon1119.mahjongcraft.platform.fabric.entity

import net.minecraft.nbt.CompoundTag
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull

/** 規則物件身分的固定保存格式與缺失資料處理。 */
class TablePropIdentityNbtTest {
    /** 兩個完整身分一起保存並還原，不影響所屬桌子欄位。 */
    @Test
    fun `identity round trip preserves the owning table`() {
        val nbt = CompoundTag().apply { putString("ManagedTableId", "table-marker") }
        TablePropIdentityNbt.write(nbt, "example:token/first", "example:rule")
        assertEquals(TablePropIdentity("example:token/first", "example:rule"), TablePropIdentityNbt.read(nbt))
        assertEquals("example:token/first", nbt.getString("TablePropPlacementId"))
        assertEquals("example:rule", nbt.getString("TablePropRuleModuleId"))
        assertEquals("table-marker", nbt.getString("ManagedTableId"))
    }

    /** 缺少一個欄位、型別錯誤或識別碼不完整時不建立半套身分。 */
    @Test
    fun `missing malformed and wrong type identities are rejected`() {
        assertNull(TablePropIdentityNbt.read(CompoundTag()))
        assertNull(TablePropIdentityNbt.read(CompoundTag().apply { putString("TablePropPlacementId", "example:token") }))
        val nbt = CompoundTag()
        TablePropIdentityNbt.write(nbt, "example:token", "example:rule")
        nbt.putInt("TablePropRuleModuleId", 1)
        assertNull(TablePropIdentityNbt.read(nbt))
        nbt.putString("TablePropRuleModuleId", "rule_without_namespace")
        assertNull(TablePropIdentityNbt.read(nbt))
    }

    /** 自由放置模式清除舊規則物件身分，其他實體資料仍保留。 */
    @Test
    fun `empty or invalid identity clears both stale fields only`() {
        for (id in listOf("", "missing_namespace")) {
            val nbt = CompoundTag().apply { putString("OtherField", "value") }
            TablePropIdentityNbt.write(nbt, "example:token", "example:rule")
            TablePropIdentityNbt.write(nbt, id, "example:rule")
            assertNull(TablePropIdentityNbt.read(nbt))
            assertFalse(nbt.contains("TablePropPlacementId"))
            assertFalse(nbt.contains("TablePropRuleModuleId"))
            assertEquals("value", nbt.getString("OtherField"))
        }
    }
}
