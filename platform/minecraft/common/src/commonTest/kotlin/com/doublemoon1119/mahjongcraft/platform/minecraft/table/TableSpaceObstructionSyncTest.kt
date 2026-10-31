package com.doublemoon1119.mahjongcraft.platform.minecraft.table

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.uuid.Uuid

/** 驗證擺牌層標示送給誰、何時清除。 */
class TableSpaceObstructionSyncTest {
    /** 從未擋住的桌子不送任何東西。 */
    @Test
    fun `unobstructed table sends nothing`() {
        val sync = TableSpaceObstructionSync()

        assertTrue(sync.update(TABLE, payload(), setOf(ALICE)).isEmpty())
    }

    /** 第一次擋住時送給所有成員。 */
    @Test
    fun `first obstruction reaches every recipient`() {
        val sync = TableSpaceObstructionSync()
        val blocked = payload(CELL_A)

        val deliveries = sync.update(TABLE, blocked, setOf(ALICE, BOB))

        assertEquals(setOf(TableSpaceObstructionDelivery(ALICE, blocked), TableSpaceObstructionDelivery(BOB, blocked)), deliveries.toSet())
    }

    /** 內容不變時只送給新加入的成員，離開的成員收到清除。 */
    @Test
    fun `unchanged content only reaches new recipients and clears departed ones`() {
        val sync = TableSpaceObstructionSync()
        val blocked = payload(CELL_A)
        sync.update(TABLE, blocked, setOf(ALICE, BOB))

        val deliveries = sync.update(TABLE, blocked, setOf(ALICE, CAROL))

        assertEquals(
            setOf(TableSpaceObstructionDelivery(BOB, payload()), TableSpaceObstructionDelivery(CAROL, blocked)),
            deliveries.toSet(),
        )
    }

    /** 被擋住的格子改變時重新送給所有成員。 */
    @Test
    fun `changed content reaches every recipient again`() {
        val sync = TableSpaceObstructionSync()
        sync.update(TABLE, payload(CELL_A), setOf(ALICE))
        val moved = payload(CELL_A, CELL_B)

        assertEquals(listOf(TableSpaceObstructionDelivery(ALICE, moved)), sync.update(TABLE, moved, setOf(ALICE)))
    }

    /** 東西移走後送出清除，之後不再重複送。 */
    @Test
    fun `cleared obstruction is sent once`() {
        val sync = TableSpaceObstructionSync()
        sync.update(TABLE, payload(CELL_A), setOf(ALICE))

        assertEquals(listOf(TableSpaceObstructionDelivery(ALICE, payload())), sync.update(TABLE, payload(), setOf(ALICE)))
        assertTrue(sync.update(TABLE, payload(), setOf(ALICE)).isEmpty())
    }

    /** 已不在等待中的桌子對原本的成員送出清除。 */
    @Test
    fun `tables that stop waiting are cleared`() {
        val sync = TableSpaceObstructionSync()
        sync.update(TABLE, payload(CELL_A), setOf(ALICE))

        assertEquals(listOf(TableSpaceObstructionDelivery(ALICE, payload())), sync.retain(emptySet()))
        assertTrue(sync.retain(emptySet()).isEmpty())
    }

    /** 重設後忘記已送出的標示，下一次擋住會重新送出。 */
    @Test
    fun `reset forgets published obstructions`() {
        val sync = TableSpaceObstructionSync()
        val blocked = payload(CELL_A)
        sync.update(TABLE, blocked, setOf(ALICE))

        sync.reset()

        assertTrue(sync.clear(TABLE).isEmpty())
        assertEquals(listOf(TableSpaceObstructionDelivery(ALICE, blocked)), sync.update(TABLE, blocked, setOf(ALICE)))
    }

    /**
     * 建立測試桌子的標示。
     *
     * @param cells 被擋住的格子。
     * @return 測試桌子的標示。
     */
    private fun payload(vararg cells: TableSpaceCellDto): TableSpaceObstructionPayloadDto = TableSpaceObstructionPayloadDto(tableId = TABLE.toString(), dimensionId = DIMENSION, cells = cells.toList())

    /** 測試用的桌子、玩家與格子。 */
    private companion object {
        /** 測試桌子。 */
        val TABLE: Uuid = Uuid.parse("00000000-0000-0000-0000-000000000001")

        /** 測試玩家。 */
        val ALICE: Uuid = Uuid.parse("00000000-0000-0000-0000-00000000000a")

        /** 測試玩家。 */
        val BOB: Uuid = Uuid.parse("00000000-0000-0000-0000-00000000000b")

        /** 測試玩家。 */
        val CAROL: Uuid = Uuid.parse("00000000-0000-0000-0000-00000000000c")

        /** 測試維度。 */
        const val DIMENSION: String = "minecraft:overworld"

        /** 擺牌層的一格。 */
        val CELL_A = TableSpaceCellDto(0, 65, 0)

        /** 擺牌層的另一格。 */
        val CELL_B = TableSpaceCellDto(1, 65, 0)
    }
}
