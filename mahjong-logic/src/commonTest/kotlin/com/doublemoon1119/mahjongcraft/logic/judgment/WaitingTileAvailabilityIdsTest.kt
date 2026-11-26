package com.doublemoon1119.mahjongcraft.logic.judgment

import kotlin.test.Test
import kotlin.test.assertEquals

/** 驗證跨模組使用的中立等待牌識別碼保持固定。 */
class WaitingTileAvailabilityIdsTest {
    /** 固定中立識別碼的序列化字串，避免共用常數變更時測試同步失去檢查作用。 */
    @Test
    fun `win available id remains the contract literal`() {
        assertEquals("mahjongcraft:win_available", WaitingTileAvailabilityIds.WIN_AVAILABLE)
    }
}
