package com.doublemoon1119.mahjongcraft.platform.fabric.entity

import com.doublemoon1119.mahjongcraft.platform.minecraft.animation.TablePresentationBusyPolicy
import com.doublemoon1119.mahjongcraft.platform.minecraft.tile.MahjongTileTableLayout
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * 驗證「一張正在動畫的牌該不該讓整桌忙碌」的判斷規則，以及該規則所依賴的豁免到期時間欄位。
 *
 * 這條判斷會擋住玩家輸入、AI、強制自動操作與決策計時器，判斷錯誤的後果是整桌卡住（該放行的沒放行）
 * 或搶跑（該擋的沒擋），因此值得直接覆蓋。規則本身刻意抽成不依賴 `World`／`Entity` 的
 * [TablePresentationBusyPolicy]，才能在沒有 Minecraft 世界的情況下測試。
 */
class TablePresentationBusyPolicyTest {
    /** 沒有豁免的一般牌局動畫（發牌、摸牌、捨牌）必須照舊讓整桌忙碌。 */
    @Test
    fun `an ordinary deal or discard animation still makes the table busy`() {
        assertTrue(
            TablePresentationBusyPolicy.tileBlocksTableBusy(
                isAnimating = true,
                nonBlockingUntilGameTime = NO_EXEMPTION,
                currentGameTime = 1_000L,
            ),
            "Deal/draw/discard animations carry no exemption, so they must keep blocking the table.",
        )
    }

    /** 沒有動畫就不忙碌，不論有沒有豁免。 */
    @Test
    fun `a tile that is not animating never makes the table busy`() {
        assertFalse(
            TablePresentationBusyPolicy.tileBlocksTableBusy(
                isAnimating = false,
                nonBlockingUntilGameTime = NO_EXEMPTION,
                currentGameTime = 1_000L,
            ),
        )
        assertFalse(
            TablePresentationBusyPolicy.tileBlocksTableBusy(
                isAnimating = false,
                nonBlockingUntilGameTime = 2_000L,
                currentGameTime = 1_000L,
            ),
        )
    }

    /**
     * 普通（非役滿）中途胡牌：整段演出期間贏家的真實牌都在播動畫，但整桌**不**忙碌——其他仍在本局
     * 中的玩家要能照常摸打。
     */
    @Test
    fun `an ordinary continuing win never makes the table busy while its tiles animate`() {
        val presentationEnd = 1_000L
        val exemptUntil = presentationEnd + MahjongTileTableLayout.WIN_LAYDOWN_DURATION_TICKS
        listOf(500L, presentationEnd - 1, presentationEnd, exemptUntil - 1).forEach { now ->
            assertFalse(
                TablePresentationBusyPolicy.tileBlocksTableBusy(
                    isAnimating = true,
                    nonBlockingUntilGameTime = exemptUntil,
                    currentGameTime = now,
                ),
                "An ordinary continuing win must not block the table at game time $now.",
            )
        }
    }

    /**
     * 含役滿 showcase 的中途胡牌：showcase 觀看期間必須擋住全桌，但**收尾的蓋牌動畫仍在播放時不擋**。
     *
     * 兩者由不同機制負責，這正是分開建模的重點：
     * - showcase 期間的阻塞來自牌桌的 `presentationBusyUntilGameTime`（本測試以 `tableIsPresenting`
     *   模擬 `TablePresentationBusyTracker` 先檢查的那一項）。
     * - 牌本身自始至終持有豁免，所以 showcase 結束、牌桌時間戳到期之後，即使收尾動畫還在播，整桌也
     *   立刻恢復可操作。
     */
    @Test
    fun `a continuing yakuman blocks only until its showcase ends, not through the conceal animation`() {
        val showcaseEnd = 800L
        val presentationEnd = 1_000L
        val exemptUntil = presentationEnd + MahjongTileTableLayout.WIN_LAYDOWN_DURATION_TICKS

        fun tableBusyAt(now: Long): Boolean {
            // 比照 TablePresentationBusyTracker.isBusy 的順序：先看牌桌共用時間軸，再看牌的動畫。
            val tableIsPresenting = now < showcaseEnd
            return tableIsPresenting ||
                TablePresentationBusyPolicy.tileBlocksTableBusy(
                    isAnimating = true,
                    nonBlockingUntilGameTime = exemptUntil,
                    currentGameTime = now,
                )
        }

        assertTrue(tableBusyAt(0L), "The showcase must be watched from the moment it is scheduled.")
        assertTrue(tableBusyAt(showcaseEnd - 1), "Still inside the showcase window.")
        assertFalse(tableBusyAt(showcaseEnd), "Once the showcase ends the other players must be free again.")
        assertFalse(
            tableBusyAt(presentationEnd),
            "The settlement panel and the conceal animation must not keep blocking the table.",
        )
        assertFalse(tableBusyAt(exemptUntil - 1), "The conceal animation is still playing but must not block.")
    }

    /** 豁免到期之後，同一張牌若還在動畫就恢復阻塞——豁免是有期限的租約，不是永久旗標。 */
    @Test
    fun `the exemption expires and the tile blocks again afterwards`() {
        val exemptUntil = 1_000L
        assertFalse(
            TablePresentationBusyPolicy.tileBlocksTableBusy(true, exemptUntil, exemptUntil - 1),
        )
        assertTrue(
            TablePresentationBusyPolicy.tileBlocksTableBusy(true, exemptUntil, exemptUntil),
            "At the lease's expiry the tile is no longer exempt.",
        )
    }

    private companion object {
        /** 沒有豁免時的欄位值；直接引用生產程式碼的常數，不鏡射字面值。 */
        const val NO_EXEMPTION = NonBlockingPresentationLeaseCodec.NO_LEASE
    }
}
