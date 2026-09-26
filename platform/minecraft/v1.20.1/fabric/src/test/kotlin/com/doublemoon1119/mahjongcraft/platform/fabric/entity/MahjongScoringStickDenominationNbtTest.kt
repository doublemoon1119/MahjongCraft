package com.doublemoon1119.mahjongcraft.platform.fabric.entity

import net.minecraft.nbt.NbtCompound
import kotlin.test.Test
import kotlin.test.assertEquals

/** 點棒面額持久化的名稱往返與無效資料測試。 */
class MahjongScoringStickDenominationNbtTest {
    private val key = "Denomination"

    /** 每個面額都以名稱寫入並讀回同一個面額。 */
    @Test
    fun `every denomination survives a name round trip`() {
        MahjongScoringStickDenomination.entries.forEach { denomination ->
            val nbt = NbtCompound()

            MahjongScoringStickDenominationNbt.write(nbt, key, denomination)

            assertEquals(denomination.name, nbt.getString(key), "denomination must be stored by name")
            assertEquals(denomination, MahjongScoringStickDenominationNbt.read(nbt, key))
        }
    }

    /** 缺少欄位、名稱無效或型別不符時使用百分棒。 */
    @Test
    fun `unknown values fall back to the smallest denomination`() {
        assertEquals(MahjongScoringStickDenomination.P100, MahjongScoringStickDenominationNbt.read(NbtCompound(), key))
        assertEquals(
            MahjongScoringStickDenomination.P100,
            MahjongScoringStickDenominationNbt.read(NbtCompound().apply { putString(key, "P999") }, key),
        )
        assertEquals(
            MahjongScoringStickDenomination.P100,
            MahjongScoringStickDenominationNbt.read(NbtCompound().apply { putInt(key, 99) }, key),
        )
    }
}
