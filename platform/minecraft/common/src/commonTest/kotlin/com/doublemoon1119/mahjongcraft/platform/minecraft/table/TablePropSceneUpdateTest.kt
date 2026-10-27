package com.doublemoon1119.mahjongcraft.platform.minecraft.table

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** 驗證桌面物件場景更新在成功與失敗時都維持完整狀態。 */
class TablePropSceneUpdateTest {
    /** 相符的既有物件應保留，不應重新建立或生成。 */
    @Test
    fun `matching props are kept without respawning`() {
        val target = target("kept")
        val existing = mutableListOf("existing")
        var created = 0
        var spawned = 0
        var removed = 0

        val updated = updateTablePropScene(
            targets = listOf(target),
            existing = existing,
            targetOf = { if (it == "existing") target else null },
            create = {
                created += 1
                "new"
            },
            spawn = {
                spawned += 1
                true
            },
            remove = { removed += 1 },
            onSpawned = { error("A matching prop must not be spawned") },
        )

        assertTrue(updated)
        assertEquals(0, created)
        assertEquals(0, spawned)
        assertEquals(0, removed)
    }

    /** 建立任一目標失敗時，舊物件與已準備物件都應清除。 */
    @Test
    fun `null factory clears old and prepared props`() {
        val old = mutableListOf("old")
        val prepared = mutableListOf<String>()
        val removed = mutableListOf<String>()
        val targets = listOf(target("first"), target("second"))

        val updated = updateTablePropScene(
            targets = targets,
            existing = old,
            targetOf = { null },
            create = { requested ->
                if (requested.id == "first") "prepared".also(prepared::add) else null
            },
            spawn = { true },
            remove = removed::add,
            onSpawned = {},
        )

        assertFalse(updated)
        assertEquals(listOf("prepared", "old"), removed)
    }

    /** 部分物件生成失敗時，不應留下已生成的部分場景。 */
    @Test
    fun `partial spawn failure clears old and every prepared prop`() {
        val removed = mutableListOf<String>()
        var spawnCount = 0

        val updated = updateTablePropScene(
            targets = listOf(target("first"), target("second")),
            existing = listOf("old"),
            targetOf = { null },
            create = { requested -> "prepared-${requested.id}" },
            spawn = { ++spawnCount == 1 },
            remove = removed::add,
            onSpawned = {},
        )

        assertFalse(updated)
        assertEquals(setOf("prepared-first", "prepared-second", "old"), removed.toSet())
    }

    /** 所有物件生成成功後，才可以執行出現效果回呼。 */
    @Test
    fun `onSpawned runs only after every prop has spawned`() {
        val spawned = mutableListOf<String>()
        val observedDuringSpawn = mutableListOf<Int>()
        val observedOnSpawned = mutableListOf<Int>()

        val updated = updateTablePropScene(
            targets = listOf(target("first"), target("second")),
            existing = emptyList(),
            targetOf = { null },
            create = { "prepared-${it.id}" },
            spawn = {
                observedDuringSpawn += observedOnSpawned.size
                spawned += it
                true
            },
            remove = {},
            onSpawned = {
                observedOnSpawned += spawned.size
            },
        )

        assertTrue(updated)
        assertEquals(listOf(0, 0), observedDuringSpawn)
        assertEquals(listOf(2, 2), observedOnSpawned)
    }

    /** 出現效果回呼失敗時，應清除場景並將原例外交給呼叫端。 */
    @Test
    fun `onSpawned failure clears scene and propagates`() {
        val removed = mutableListOf<String>()
        val failure = IllegalStateException("appearance failed")

        val thrown = assertFailsWith<IllegalStateException> {
            updateTablePropScene(
                targets = listOf(target("first"), target("second")),
                existing = listOf("old"),
                targetOf = { null },
                create = { "prepared-${it.id}" },
                spawn = { true },
                remove = removed::add,
                onSpawned = { throw failure },
            )
        }

        assertTrue(thrown === failure)
        assertEquals(setOf("prepared-first", "prepared-second", "old"), removed.toSet())
    }

    /**
     * 建立一個使用命名空間識別碼的測試目標。
     *
     * @param id 同一桌內穩定的物件識別碼。
     * @return 測試用桌面物件目標。
     */
    private fun target(id: String): TablePropTarget = TablePropTarget(
        ruleModuleId = "mahjongcraft_test:rule",
        id = id,
        kind = "mahjongcraft_test:prop",
        content = TablePropContent.CustomVariant("mahjongcraft_test:example"),
        x = 0.0,
        y = 0.0,
        z = 0.0,
        yaw = 0.0f,
    )
}
