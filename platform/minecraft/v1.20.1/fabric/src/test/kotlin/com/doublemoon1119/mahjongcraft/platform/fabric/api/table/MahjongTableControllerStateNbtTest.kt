@file:OptIn(MahjongCraftInternalApi::class)

package com.doublemoon1119.mahjongcraft.platform.fabric.api.table

import com.doublemoon1119.mahjongcraft.platform.minecraft.api.MahjongCraftInternalApi
import com.doublemoon1119.mahjongcraft.platform.minecraft.api.table.MahjongTableControllerState
import net.minecraft.nbt.NbtCompound
import kotlin.test.Test
import kotlin.test.assertEquals

/** 驗證麻將桌狀態的 NBT 存讀檔。 */
class MahjongTableControllerStateNbtTest {
    /** 桌子 ID 與兩條時間軸都能經由 NBT 往返保存。 */
    @Test
    fun `state survives nbt round trip`() {
        val original = MahjongTableControllerState {}
        original.extendPresentationUntil(120L)
        original.extendContinuingWinPresentationUntil(80L)
        val nbt = NbtCompound().also(original::writeNbt)

        val restored = MahjongTableControllerState {}.also { it.readNbt(nbt) }

        assertEquals(original.tableId, restored.tableId)
        assertEquals(120L, restored.presentationBusyUntilGameTime)
        assertEquals(80L, restored.continuingWinPresentationBusyUntilGameTime)
    }

    /** 損壞的桌子 ID 不覆蓋目前的 ID。 */
    @Test
    fun `corrupt table id keeps current id`() {
        val state = MahjongTableControllerState {}
        val generated = state.tableId

        state.readNbt(NbtCompound().apply { putString("TableId", "not-a-uuid") })

        assertEquals(generated, state.tableId)
    }
}
