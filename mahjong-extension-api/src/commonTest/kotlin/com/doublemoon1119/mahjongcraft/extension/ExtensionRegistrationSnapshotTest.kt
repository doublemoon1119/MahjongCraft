package com.doublemoon1119.mahjongcraft.extension

import kotlin.test.Test
import kotlin.test.assertEquals

/** 驗證核心註冊快照與平台快照共用的集合隔離、排序及空分類契約。 */
class ExtensionRegistrationSnapshotTest {
    /** 快照保留建構時的 key，不受呼叫端集合後續變更影響。 */
    @Test
    fun `snapshot detaches input categories and keys`() {
        val keys = mutableSetOf("z", "a")
        val categories = mutableListOf(ExtensionRegistrationSnapshotCategory("tile", "Tile", keys))
        val captured = ExtensionRegistrationSnapshot(categories)

        keys += "later"
        categories.clear()

        assertEquals(listOf(ExtensionRegistrationCategory("tile", "Tile", listOf("a", "z"))), captured.toCategories())
    }

    /** 分類順序不變、key 排序，沒有新增 key 時不產生空分類。 */
    @Test
    fun `snapshot filters empty categories and unchanged additions`() {
        val captured = ExtensionRegistrationSnapshot(
            listOf(
                ExtensionRegistrationSnapshotCategory("empty", "Empty", emptySet()),
                ExtensionRegistrationSnapshotCategory("tile", "Tile", setOf("z", "a")),
            ),
        )

        assertEquals(listOf(ExtensionRegistrationCategory("tile", "Tile", listOf("a", "z"))), captured.toCategories())
        assertEquals(emptyList(), captured.additionsSince(captured))
    }
}
