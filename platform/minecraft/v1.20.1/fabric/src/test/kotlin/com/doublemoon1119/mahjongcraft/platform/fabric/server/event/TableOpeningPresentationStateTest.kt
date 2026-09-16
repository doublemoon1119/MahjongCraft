package com.doublemoon1119.mahjongcraft.platform.fabric.server.event

import com.doublemoon1119.mahjongcraft.logic.table.layout.TileWallPhysicalLayout
import com.doublemoon1119.mahjongcraft.platform.minecraft.dice.MahjongTableFacing
import com.doublemoon1119.mahjongcraft.platform.minecraft.table.TableLocation
import com.doublemoon1119.mahjongcraft.platform.minecraft.tile.MahjongTileWallPresentation
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.uuid.Uuid

/** 驗證開局四個階段之間的暫存資料。 */
class TableOpeningPresentationStateTest {
    private val state = TableOpeningPresentationState()

    /** 沒有發布過牌牆時動畫不需要延後。 */
    @Test
    fun `needs no delay before any wall is published`() {
        assertEquals(0, state.wallDropTicks(TABLE))
    }

    /** 沒有發布過牌牆時墩數未知，呼叫端應放棄需要它的呈現。 */
    @Test
    fun `reports an unknown stack count before any wall is published`() {
        assertNull(state.wallStacksPerSide(TABLE))
    }

    /** 牌牆發布後兩個讀取端都取得本局的值。 */
    @Test
    fun `publishes the drop ticks and the stack count of this round`() {
        state.beginWall(TABLE, wallDropTicks = 40, stacksPerSide = 17)

        assertEquals(40, state.wallDropTicks(TABLE))
        assertEquals(17, state.wallStacksPerSide(TABLE))
    }

    /** 兩個讀取端各自安全讀取，不會互相消費。 */
    @Test
    fun `keeps the values readable more than once`() {
        state.beginWall(TABLE, wallDropTicks = 40, stacksPerSide = 17)

        repeat(3) { assertEquals(40, state.wallDropTicks(TABLE)) }
        repeat(3) { assertEquals(17, state.wallStacksPerSide(TABLE)) }
    }

    /** 下一局的牌牆覆寫上一局的值。 */
    @Test
    fun `overwrites the values of the previous round`() {
        state.beginWall(TABLE, wallDropTicks = 40, stacksPerSide = 17)

        state.beginWall(TABLE, wallDropTicks = 24, stacksPerSide = 9)

        assertEquals(24, state.wallDropTicks(TABLE))
        assertEquals(9, state.wallStacksPerSide(TABLE))
    }

    /** 每張桌子各自保存，互不影響。 */
    @Test
    fun `keeps the tables apart`() {
        state.beginWall(TABLE, wallDropTicks = 40, stacksPerSide = 17)
        state.beginWall(OTHER_TABLE, wallDropTicks = 24, stacksPerSide = 9)

        assertEquals(40, state.wallDropTicks(TABLE))
        assertEquals(24, state.wallDropTicks(OTHER_TABLE))
    }

    /** 沒有排定開門時發牌不會取到任何開門資料。 */
    @Test
    fun `consumes no opening while none is armed`() {
        state.beginWall(TABLE, wallDropTicks = 40, stacksPerSide = 17)

        assertNull(state.consumeOpening(TABLE))
    }

    /** 排定的開門資料由發牌取出。 */
    @Test
    fun `hands the armed opening to the deal`() {
        state.armOpening(TABLE, openingPresentation())

        assertEquals(openingPresentation(), state.consumeOpening(TABLE))
    }

    /** 開門一局只播一次。 */
    @Test
    fun `plays the opening only once`() {
        state.armOpening(TABLE, openingPresentation())
        state.consumeOpening(TABLE)

        assertNull(state.consumeOpening(TABLE))
    }

    /** 牌牆呈現沒有成立時本局不播開門。 */
    @Test
    fun `plays no opening after the wall presentation failed`() {
        state.armOpening(TABLE, openingPresentation())

        state.cancelOpening(TABLE)

        assertNull(state.consumeOpening(TABLE))
    }

    /** 上一局尚未播出的開門資料不會跨到下一局。 */
    @Test
    fun `drops an unplayed opening when the next round starts`() {
        state.armOpening(TABLE, openingPresentation())

        state.beginWall(TABLE, wallDropTicks = 24, stacksPerSide = 9)

        assertNull(state.consumeOpening(TABLE))
    }

    /** 開局失敗後整張桌子回到初始狀態。 */
    @Test
    fun `resets the table after a failed opening`() {
        state.beginWall(TABLE, wallDropTicks = 40, stacksPerSide = 17)
        state.armOpening(TABLE, openingPresentation())

        state.clear(TABLE)

        assertEquals(0, state.wallDropTicks(TABLE))
        assertNull(state.wallStacksPerSide(TABLE))
        assertNull(state.consumeOpening(TABLE))
    }

    /** 清除一張桌子不影響其他桌。 */
    @Test
    fun `clears only the given table`() {
        state.beginWall(TABLE, wallDropTicks = 40, stacksPerSide = 17)
        state.beginWall(OTHER_TABLE, wallDropTicks = 24, stacksPerSide = 9)

        state.clear(TABLE)

        assertEquals(24, state.wallDropTicks(OTHER_TABLE))
        assertEquals(9, state.wallStacksPerSide(OTHER_TABLE))
    }

    /** 建立測試用的開門呈現資料。 */
    private fun openingPresentation() = MahjongTileWallPresentation(
        tableId = TABLE,
        tableLocation = TableLocation(dimensionId = "minecraft:overworld", x = 0, y = 64, z = 0),
        tableFacing = MahjongTableFacing.NORTH,
        dealerSeatIndex = 0,
        assemblyStructure = emptyMap(),
        finalLayout = TileWallPhysicalLayout(emptyMap()),
        deadWallTileIds = emptySet(),
        diceCount = 2,
    )

    private companion object {
        val TABLE: Uuid = Uuid.parse("00000000-0000-0000-0000-000000000001")
        val OTHER_TABLE: Uuid = Uuid.parse("00000000-0000-0000-0000-000000000002")
    }
}
