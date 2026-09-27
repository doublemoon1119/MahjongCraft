package com.doublemoon1119.mahjongcraft.platform.fabric.item

import com.doublemoon1119.mahjongcraft.platform.minecraft.stick.MahjongScoringStickDenomination
import net.minecraft.nbt.NbtCompound
import kotlin.test.Test
import kotlin.test.assertEquals

/** 點棒物品資料與面額 model predicate 的測試。 */
class MahjongScoringStickItemStackDataTest {
    /** 每種面額皆能以名稱寫入物品並讀回。 */
    @Test
    fun `every denomination survives item stack round trip`() {
        MahjongScoringStickDenomination.entries.forEach { denomination ->
            val nbt = NbtCompound()
            MahjongScoringStickItemStackData.writeNbt(nbt, denomination)
            assertEquals(denomination.name, nbt.getString(MahjongScoringStickItemStackData.NBT_KEY_DENOMINATION))
            assertEquals(denomination, MahjongScoringStickItemStackData.readNbt(nbt))
        }
    }

    /** 缺失、未知名稱與錯誤型別均使用百分棒。 */
    @Test
    fun `invalid item denomination falls back to smallest denomination`() {
        val nbt = NbtCompound()
        assertEquals(MahjongScoringStickDenomination.P100, MahjongScoringStickItemStackData.readNbt(null))
        nbt.putString(MahjongScoringStickItemStackData.NBT_KEY_DENOMINATION, "P999")
        assertEquals(MahjongScoringStickDenomination.P100, MahjongScoringStickItemStackData.readNbt(nbt))
        nbt.putInt(MahjongScoringStickItemStackData.NBT_KEY_DENOMINATION, 99)
        assertEquals(MahjongScoringStickDenomination.P100, MahjongScoringStickItemStackData.readNbt(nbt))
    }

    /** 四種面額在夾限後仍對應四個不同的模型值。 */
    @Test
    fun `model predicate values remain distinct within unit interval`() {
        val values = MahjongScoringStickDenomination.entries.map { it.toModelPredicateValue() }
        assertEquals(values.size, values.distinct().size)
        assertEquals(0.0f, values.first())
        assertEquals(1.0f, values.last())
    }
}
