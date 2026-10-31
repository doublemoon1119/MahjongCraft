package com.doublemoon1119.mahjongcraft.platform.fabric.client.table

import com.doublemoon1119.mahjongcraft.platform.minecraft.table.TableSpaceCellDto
import com.doublemoon1119.mahjongcraft.platform.minecraft.table.TableSpaceObstructionPayloadDto
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.uuid.Uuid

/** 驗證客戶端保存的擺牌層標示。 */
class ClientTableSpaceObstructionStoreTest {
    /** 收到標示後該桌視為被擋住，只在同一個維度列出格子。 */
    @Test
    fun `obstruction is kept per table and listed by dimension`() {
        val store = ClientTableSpaceObstructionStore()

        store.apply(payload(TABLE, OVERWORLD, CELL))

        assertTrue(store.isObstructed(TABLE))
        assertEquals(listOf(CELL), store.cellsIn(OVERWORLD))
        assertTrue(store.cellsIn(NETHER).isEmpty())
    }

    /** 空標示與移除桌子都會清除該桌。 */
    @Test
    fun `empty payload and table removal clear the table`() {
        val store = ClientTableSpaceObstructionStore()
        store.apply(payload(TABLE, OVERWORLD, CELL))

        store.apply(payload(TABLE, OVERWORLD))
        assertFalse(store.isObstructed(TABLE))

        store.apply(payload(TABLE, OVERWORLD, CELL))
        store.clearTable(TABLE)
        assertFalse(store.isObstructed(TABLE))
    }

    /** 格子數超過擺牌層或桌子 ID 無效時忽略。 */
    @Test
    fun `invalid payloads are ignored`() {
        val store = ClientTableSpaceObstructionStore()

        store.apply(payload(TABLE, OVERWORLD, *Array(10) { TableSpaceCellDto(it, 65, 0) }))
        store.apply(TableSpaceObstructionPayloadDto(tableId = "not-a-uuid", dimensionId = OVERWORLD, cells = listOf(CELL)))

        assertFalse(store.isObstructed(TABLE))
        assertTrue(store.cellsIn(OVERWORLD).isEmpty())
    }

    /**
     * 建立一份標示。
     *
     * @param tableId 桌子 UUID。
     * @param dimensionId 維度識別碼。
     * @param cells 被擋住的格子。
     * @return 標示。
     */
    private fun payload(
        tableId: Uuid,
        dimensionId: String,
        vararg cells: TableSpaceCellDto,
    ): TableSpaceObstructionPayloadDto = TableSpaceObstructionPayloadDto(tableId.toString(), dimensionId, cells.toList())

    /** 測試用的桌子、維度與格子。 */
    private companion object {
        /** 測試桌子。 */
        val TABLE: Uuid = Uuid.parse("00000000-0000-0000-0000-000000000001")

        /** 主世界。 */
        const val OVERWORLD: String = "minecraft:overworld"

        /** 地獄。 */
        const val NETHER: String = "minecraft:the_nether"

        /** 擺牌層的一格。 */
        val CELL = TableSpaceCellDto(0, 65, 0)
    }
}
