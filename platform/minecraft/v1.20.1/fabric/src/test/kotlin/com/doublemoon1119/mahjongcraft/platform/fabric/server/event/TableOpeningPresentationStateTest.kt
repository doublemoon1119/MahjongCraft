package com.doublemoon1119.mahjongcraft.platform.fabric.server.event

import com.doublemoon1119.mahjongcraft.logic.table.layout.TileWallPhysicalLayout
import com.doublemoon1119.mahjongcraft.logic.table.layout.TileWallSideArrangement
import com.doublemoon1119.mahjongcraft.logic.table.layout.TileWallSideReference
import com.doublemoon1119.mahjongcraft.platform.minecraft.dice.MahjongTableFacing
import com.doublemoon1119.mahjongcraft.platform.minecraft.dice.MahjongTableSide
import com.doublemoon1119.mahjongcraft.platform.minecraft.table.TableLocation
import com.doublemoon1119.mahjongcraft.platform.minecraft.tile.MahjongTileWallPresentation
import com.doublemoon1119.mahjongcraft.platform.minecraft.tile.MahjongTileWallSideLayout
import kotlinx.coroutines.async
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertNull
import kotlin.uuid.Uuid

/** 驗證開局四個階段之間的暫存資料。 */
class TableOpeningPresentationStateTest {
    /** 各測試使用的獨立開局呈現暫存狀態。 */
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
        state.beginWall(TABLE, wallDropTicks = 40, stacksPerSide = 17, sideCount = 4)

        assertEquals(40, state.wallDropTicks(TABLE))
        assertEquals(17, state.wallStacksPerSide(TABLE))
    }

    /** 兩個讀取端各自安全讀取，不會互相消費。 */
    @Test
    fun `keeps the values readable more than once`() {
        state.beginWall(TABLE, wallDropTicks = 40, stacksPerSide = 17, sideCount = 4)

        repeat(3) { assertEquals(40, state.wallDropTicks(TABLE)) }
        repeat(3) { assertEquals(17, state.wallStacksPerSide(TABLE)) }
    }

    /** 下一局的牌牆覆寫上一局的值。 */
    @Test
    fun `overwrites the values of the previous round`() {
        state.beginWall(TABLE, wallDropTicks = 40, stacksPerSide = 17, sideCount = 4)

        state.beginWall(TABLE, wallDropTicks = 24, stacksPerSide = 9, sideCount = 4)

        assertEquals(24, state.wallDropTicks(TABLE))
        assertEquals(9, state.wallStacksPerSide(TABLE))
    }

    /** 新開局的擲骰序號會持續遞增，不依賴局數或本場數的固定上限。 */
    @Test
    fun `allocates a fresh sequence for each new opening`() {
        state.beginWall(TABLE, wallDropTicks = 40, stacksPerSide = 17, sideCount = 4)
        val first = state.openingRollSequence(TABLE, 0)
        state.beginWall(TABLE, wallDropTicks = 24, stacksPerSide = 9, sideCount = 4)
        val second = state.openingRollSequence(TABLE, 0)

        assertEquals(1L, second!! - first!!)
        assertEquals(second, state.openingRollSequence(TABLE, 1)?.minus(1L))
    }

    /** 不同開局批次的輪次索引即使相同，也不會產生相同序號。 */
    @Test
    fun `does not collide when a later batch starts at an earlier roll index`() {
        state.beginWall(TABLE, wallDropTicks = 40, stacksPerSide = 17, sideCount = 4)
        val firstBatchSecondRoll = state.openingRollSequence(TABLE, 1)

        state.beginWall(TABLE, wallDropTicks = 24, stacksPerSide = 9, sideCount = 4)
        val secondBatchFirstRoll = state.openingRollSequence(TABLE, 0)

        assertNotEquals(firstBatchSecondRoll, secondBatchFirstRoll)
    }

    /** 恢復既有牌牆不應建立新的擲骰序號。 */
    @Test
    fun `restoring a wall keeps the current opening sequence`() {
        state.beginWall(TABLE, wallDropTicks = 40, stacksPerSide = 17, sideCount = 4)
        val sequence = state.openingRollSequence(TABLE, 0)
        state.beginWall(TABLE, wallDropTicks = 40, stacksPerSide = 17, sideCount = 4, isNewOpening = false)

        assertEquals(sequence, state.openingRollSequence(TABLE, 0))
    }

    /** 開局後續階段可讀取牌牆真正開始時的共同時間線錨點。 */
    @Test
    fun `records the wall animation start time`() = runTest {
        state.beginWall(TABLE, wallDropTicks = 40, stacksPerSide = 17, sideCount = 4)
        state.markWallStarted(TABLE, 1234L)

        assertEquals(1234L, state.awaitWallStartGameTime(TABLE))
    }

    /** 後續階段即使先開始，也必須等待牌牆交付同一個實際起點。 */
    @Test
    fun `later stages wait for the wall anchor instead of inventing their own`() = runTest {
        state.beginWall(TABLE, wallDropTicks = 40, stacksPerSide = 17, sideCount = 4)
        val waiting = async { state.awaitWallStartGameTime(TABLE) }
        testScheduler.runCurrent()

        assertEquals(false, waiting.isCompleted)
        state.markWallStarted(TABLE, 5678L)

        assertEquals(5678L, waiting.await())
    }

    /** 清除尚未開始的牌牆會取消等待者，不留下永遠忙碌的開局工作。 */
    @Test
    fun `clearing a wall cancels stages awaiting its anchor`() = runTest {
        state.beginWall(TABLE, wallDropTicks = 40, stacksPerSide = 17, sideCount = 4)
        val waiting = async { state.awaitWallStartGameTime(TABLE) }
        testScheduler.runCurrent()

        state.clear(TABLE)
        testScheduler.runCurrent()

        assertEquals(true, waiting.isCancelled)
    }

    /** 每張桌子各自保存，互不影響。 */
    @Test
    fun `keeps the tables apart`() {
        state.beginWall(TABLE, wallDropTicks = 40, stacksPerSide = 17, sideCount = 4)
        state.beginWall(OTHER_TABLE, wallDropTicks = 24, stacksPerSide = 9, sideCount = 4)

        assertEquals(40, state.wallDropTicks(TABLE))
        assertEquals(24, state.wallDropTicks(OTHER_TABLE))
    }

    /** 沒有排定開門時發牌不會取到任何開門資料。 */
    @Test
    fun `consumes no opening while none is armed`() {
        state.beginWall(TABLE, wallDropTicks = 40, stacksPerSide = 17, sideCount = 4)

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

        state.beginWall(TABLE, wallDropTicks = 24, stacksPerSide = 9, sideCount = 4)

        assertNull(state.consumeOpening(TABLE))
    }

    /** 開局失敗後整張桌子回到初始狀態。 */
    @Test
    fun `resets the table after a failed opening`() {
        state.beginWall(TABLE, wallDropTicks = 40, stacksPerSide = 17, sideCount = 4)
        state.armOpening(TABLE, openingPresentation())

        state.clear(TABLE)

        assertEquals(0, state.wallDropTicks(TABLE))
        assertNull(state.wallStacksPerSide(TABLE))
        assertNull(state.consumeOpening(TABLE))
    }

    /** 清除一張桌子不影響其他桌。 */
    @Test
    fun `clears only the given table`() {
        state.beginWall(TABLE, wallDropTicks = 40, stacksPerSide = 17, sideCount = 4)
        state.beginWall(OTHER_TABLE, wallDropTicks = 24, stacksPerSide = 9, sideCount = 4)

        state.clear(TABLE)

        assertEquals(24, state.wallDropTicks(OTHER_TABLE))
        assertEquals(9, state.wallStacksPerSide(OTHER_TABLE))
    }

    /**
     * 建立測試用的開門呈現資料。
     *
     * @return 主測試牌桌使用、未配置牌實體的開門資料。
     */
    private fun openingPresentation() = MahjongTileWallPresentation(
        tableId = TABLE,
        tableLocation = TableLocation(dimensionId = "minecraft:overworld", x = 0, y = 64, z = 0),
        tableFacing = MahjongTableFacing.NORTH,
        sideLayout = MahjongTileWallSideLayout.resolve(
            arrangement = TileWallSideArrangement(TileWallSideReference.TABLE, listOf(0, 1, 2, 3)),
            dealerSide = MahjongTableSide.SOUTH,
        ),
        assemblyStructure = emptyMap(),
        finalLayout = TileWallPhysicalLayout(emptyMap()),
        deadWallTileIds = emptySet(),
        openingDiceCounts = listOf(2),
    )

    /** 測試共用的牌桌識別碼。 */
    private companion object {
        /** 主要受測牌桌識別碼。 */
        val TABLE: Uuid = Uuid.parse("00000000-0000-0000-0000-000000000001")

        /** 用來驗證桌間隔離的另一張牌桌識別碼。 */
        val OTHER_TABLE: Uuid = Uuid.parse("00000000-0000-0000-0000-000000000002")
    }
}
