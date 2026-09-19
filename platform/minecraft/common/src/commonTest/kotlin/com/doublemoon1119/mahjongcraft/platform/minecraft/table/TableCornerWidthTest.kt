package com.doublemoon1119.mahjongcraft.platform.minecraft.table

import com.doublemoon1119.mahjongcraft.logic.table.TableState
import com.doublemoon1119.mahjongcraft.logic.table.Wind
import com.doublemoon1119.mahjongcraft.testing.logic.table.FakeMahjongPlayerFactory
import com.doublemoon1119.mahjongcraft.testing.logic.table.FakeTableStateFactory
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.uuid.Uuid

/** 驗證副露角落寬度的查詢與紀錄。 */
class TableCornerWidthTest {
    private val tableState = FakeTableStateFactory.create(
        players = Wind.entries.map { wind -> FakeMahjongPlayerFactory.create(seatWind = wind) },
    )

    /** 沒有登記描述的規則，每個座位都不佔用角落。 */
    @Test
    fun `a rule without a describer reserves no corner for any seat`() {
        val describer: TablePropDescriber? = null

        assertEquals(mapOf(0 to 0.0, 1 to 0.0, 2 to 0.0, 3 to 0.0), describer.cornerWidthsBySeat(tableState))
    }

    /** 描述回報的寬度依座位列出。 */
    @Test
    fun `widths are listed per seat as the describer reports them`() {
        val describer = object : TablePropDescriber {
            override fun describe(tableState: TableState) = emptyList<TablePropPlacement>()

            override fun cornerWidth(tableState: TableState, seatIndex: Int) = seatIndex * 0.25
        }

        assertEquals(mapOf(0 to 0.0, 1 to 0.25, 2 to 0.5, 3 to 0.75), describer.cornerWidthsBySeat(tableState))
    }

    /** 紀錄以最新一次為準，清除後查不到。 */
    @Test
    fun `tracker keeps the latest record until cleared`() {
        val tracker = TableCornerWidthTracker()
        val tableId = Uuid.random()

        assertNull(tracker.find(tableId))
        tracker.record(tableId, mapOf(0 to 0.5))
        tracker.record(tableId, mapOf(0 to 0.25))
        assertEquals(mapOf(0 to 0.25), tracker.find(tableId))

        tracker.clear(tableId)
        assertNull(tracker.find(tableId))
    }
}
