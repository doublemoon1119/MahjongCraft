package com.doublemoon1119.mahjongcraft.platform.minecraft.mod.development

import com.doublemoon1119.mahjongcraft.flow.common.game.model.WinCelebrationRequest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertSame
import kotlin.uuid.Uuid

/** 驗證和牌展示調整與牌桌結束通知的套用順序。 */
class DevelopmentHooksTest {
    /** 測試用的演出請求。 */
    private val request = WinCelebrationRequest(winningTileId = Uuid.random(), isTsumo = true, winners = emptyList(), revealsWinnerHands = false)

    /** 沒有登記任何調整時，演出請求原樣傳回。 */
    @Test
    fun `adjusters keep the request when nothing is registered`() {
        assertSame(request, WinCelebrationRequestAdjusters().adjust(Uuid.random(), request))
    }

    /** 調整依登記順序套用，每個調整收到前一個調整的結果。 */
    @Test
    fun `adjusters apply in registration order`() {
        val tableId = Uuid.random()
        val adjusters = WinCelebrationRequestAdjusters()
        val seen = mutableListOf<Pair<Uuid, Boolean>>()
        adjusters.register { id, current ->
            seen += id to current.isTsumo
            current.copy(isTsumo = false)
        }
        adjusters.register { id, current ->
            seen += id to current.isTsumo
            current.copy(revealsWinnerHands = true)
        }

        val adjusted = adjusters.adjust(tableId, request)

        assertEquals(listOf(tableId to true, tableId to false), seen)
        assertEquals(request.copy(isTsumo = false, revealsWinnerHands = true), adjusted)
    }

    /** 牌桌結束時依登記順序通知每個接收者。 */
    @Test
    fun `table end listeners are notified in registration order`() {
        val tableId = Uuid.random()
        val listeners = TableEndListeners()
        val calls = mutableListOf<String>()
        listeners.register { id -> calls += "first:$id" }
        listeners.register { id -> calls += "second:$id" }

        listeners.notifyTableEnded(tableId)

        assertEquals(listOf("first:$tableId", "second:$tableId"), calls)
    }
}
