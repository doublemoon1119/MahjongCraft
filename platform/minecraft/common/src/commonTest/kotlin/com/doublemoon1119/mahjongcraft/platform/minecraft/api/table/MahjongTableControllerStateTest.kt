@file:OptIn(MahjongCraftInternalApi::class)

package com.doublemoon1119.mahjongcraft.platform.minecraft.api.table

import com.doublemoon1119.mahjongcraft.platform.minecraft.api.MahjongCraftInternalApi
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue
import kotlin.uuid.Uuid

/** 驗證麻將桌狀態物件的呈現時間軸與還原。 */
class MahjongTableControllerStateTest {
    /** 時間軸只會延長，而且只有真正延長時才通知方塊實體存檔。 */
    @Test
    fun `timelines only extend and mark dirty when extended`() {
        var dirtyCount = 0
        val state = MahjongTableControllerState { dirtyCount += 1 }

        state.extendPresentationUntil(100L)
        state.extendPresentationUntil(60L)
        state.extendContinuingWinPresentationUntil(40L)
        state.extendContinuingWinPresentationUntil(40L)

        assertEquals(100L, state.presentationBusyUntilGameTime)
        assertEquals(40L, state.continuingWinPresentationBusyUntilGameTime)
        assertEquals(2, dirtyCount)
        assertTrue(state.isPresenting(99L))
        assertFalse(state.isPresenting(100L))
        assertTrue(state.isPresentingContinuingWin(39L))
        assertFalse(state.isPresentingContinuingWin(40L))
    }

    /** 還原會套用存檔內容且不通知存檔；沒有可用 ID 時保留目前的 ID。 */
    @Test
    fun `restore applies saved values without marking dirty`() {
        var dirtyCount = 0
        val state = MahjongTableControllerState { dirtyCount += 1 }
        val generated = state.tableId
        val saved = Uuid.random()

        state.restore(tableId = null, presentationBusyUntilGameTime = 30L, continuingWinPresentationBusyUntilGameTime = 20L)
        assertEquals(generated, state.tableId)
        state.restore(tableId = saved, presentationBusyUntilGameTime = 50L, continuingWinPresentationBusyUntilGameTime = 10L)

        assertEquals(saved, state.tableId)
        assertEquals(50L, state.presentationBusyUntilGameTime)
        assertEquals(10L, state.continuingWinPresentationBusyUntilGameTime)
        assertEquals(0, dirtyCount)
    }

    /** 每個新狀態物件取得不同的桌子 ID。 */
    @Test
    fun `new states get distinct table ids`() {
        assertNotEquals(MahjongTableControllerState {}.tableId, MahjongTableControllerState {}.tableId)
    }
}
