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
        tracker.record(tableId, ROUND, mapOf(0 to 0.5))
        tracker.record(tableId, ROUND, mapOf(0 to 0.25))
        assertEquals(mapOf(0 to 0.25), tracker.find(tableId))

        tracker.clear(tableId)
        assertNull(tracker.find(tableId))
    }

    /** 只更新單一座位時保留同一局其他座位的寬度；換局後舊紀錄不再當成這一局的寬度。 */
    @Test
    fun `seat records merge within a round and reset across rounds`() {
        val tracker = TableCornerWidthTracker()
        val tableId = Uuid.random()
        val nextRound = ROUND.copy(comboCount = ROUND.comboCount + 1)

        tracker.record(tableId, ROUND, mapOf(0 to 0.5, 1 to 0.0))
        tracker.recordSeat(tableId, ROUND, seatIndex = 1, width = 0.25)
        assertEquals(mapOf(0 to 0.5, 1 to 0.25), tracker.find(tableId, ROUND))
        assertNull(tracker.find(tableId, nextRound))

        tracker.recordSeat(tableId, nextRound, seatIndex = 0, width = 0.0)
        assertEquals(mapOf(0 to 0.0), tracker.find(tableId, nextRound))
        assertNull(tracker.find(tableId, ROUND))
    }

    private companion object {
        /** 測試用的那一局。 */
        val ROUND = TableRound(roundNumber = 1, comboCount = 0)
    }
}
