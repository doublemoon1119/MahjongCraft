package com.doublemoon1119.mahjongcraft.platform.minecraft.mod.vanilla

import com.doublemoon1119.mahjongcraft.platform.minecraft.stick.MahjongScoringStickDenomination
import com.doublemoon1119.mahjongcraft.platform.minecraft.stick.MahjongScoringStickItemData
import net.minecraft.nbt.CompoundTag
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/** 物品 NBT 文字欄位的讀寫。 */
class ItemStackFieldsTest {
    /** 每種點棒面額都能以名稱寫入並讀回。 */
    @Test
    fun `every denomination survives a round trip`() {
        MahjongScoringStickDenomination.entries.forEach { denomination ->
            val nbt = CompoundTag()
            writeItemString(nbt, MahjongScoringStickItemData.DENOMINATION_KEY, MahjongScoringStickItemData.write(denomination))
            assertEquals(denomination, MahjongScoringStickItemData.read(readItemString(nbt, MahjongScoringStickItemData.DENOMINATION_KEY)))
        }
    }

    /** 沒有資料、沒有欄位或欄位不是文字時讀不到名稱，點棒因此使用百分棒。 */
    @Test
    fun `missing or mistyped fields read as no name`() {
        val key = MahjongScoringStickItemData.DENOMINATION_KEY
        assertNull(readItemString(null, key))
        assertNull(readItemString(CompoundTag(), key))
        assertNull(readItemString(CompoundTag().apply { putInt(key, 99) }, key))
        assertEquals(MahjongScoringStickDenomination.P100, MahjongScoringStickItemData.read(readItemString(CompoundTag().apply { putString(key, "P999") }, key)))
    }
}
