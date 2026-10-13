package com.doublemoon1119.mahjongcraft.platform.minecraft.extension

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

/** 驗證 Minecraft presentation registry 診斷快照的差集行為。 */
class MinecraftPresentationRegistrationSnapshotTest {
    /** 建構後修改呼叫端的集合不會改變既有快照。 */
    @Test
    fun `snapshot detaches input categories and keys`() {
        val keys = mutableSetOf("z", "a")
        val categories = mutableListOf(MinecraftPresentationRegistrationSnapshotCategory("tile", "Tile", keys))
        val captured = MinecraftPresentationRegistrationSnapshot(categories)

        keys += "later"
        categories.clear()

        assertEquals(listOf(presentationCategory("tile", "Tile", "a", "z")), captured.toCategories())
    }

    /** 空分類不列入報告，沒有新增 key 時差異為空。 */
    @Test
    fun `snapshot filters empty categories and unchanged additions`() {
        val captured = snapshot(category("empty", "Empty"), category("tile", "Tile", "z", "a"))

        assertEquals(listOf(presentationCategory("tile", "Tile", "a", "z")), captured.toCategories())
        assertEquals(emptyList(), captured.additionsSince(captured))
    }

    /** 來源可以沒有新增項目，但不能含沒有 key 的分類；分類 key 必須排序且不重複。 */
    @Test
    fun `report rejects empty categories and unordered or duplicate ids`() {
        assertFailsWith<IllegalArgumentException> {
            MinecraftPresentationRegistrationSource("example:test", listOf(presentationCategory("empty", "Empty")))
        }
        listOf(listOf("z", "a"), listOf("a", "a")).forEach { keys ->
            assertFailsWith<IllegalArgumentException> {
                MinecraftPresentationRegistrationCategory("tile", "Tile", keys)
            }
        }
    }

    /** 差集保留分類順序，且排除 callback 前已存在的 built-in key。 */
    @Test
    fun `report only keys added after baseline`() {
        val baseline = snapshot(
            category("tile", "Tile", "built_in"),
            category("sound", "Sound"),
        )
        val current = snapshot(
            category("tile", "Tile", "built_in", "third_party"),
            category("sound", "Sound", "custom_sound"),
        )

        assertEquals(
            listOf(
                presentationCategory("tile", "Tile", "third_party"),
                presentationCategory("sound", "Sound", "custom_sound"),
            ),
            baseline.additionsSince(current),
        )
    }

    /**
     * 建立測試使用的 presentation 快照。
     *
     * @param categories 快照內的登記分類。
     * @return 以指定分類建立的快照。
     */
    private fun snapshot(vararg categories: MinecraftPresentationRegistrationSnapshotCategory) = MinecraftPresentationRegistrationSnapshot(
        categories.toList(),
    )

    /**
     * 建立測試使用的 presentation 分類。
     *
     * @param id 分類識別碼。
     * @param displayName 分類顯示名稱。
     * @param registrationKeys 分類內的登記 key。
     * @return 建立完成的分類快照。
     */
    private fun category(
        id: String,
        displayName: String,
        vararg registrationKeys: String,
    ): MinecraftPresentationRegistrationSnapshotCategory = MinecraftPresentationRegistrationSnapshotCategory(
        id,
        displayName,
        registrationKeys.toSet(),
    )

    /**
     * 建立測試使用的呈現輸出分類。
     *
     * @param id 分類識別碼。
     * @param displayName 分類診斷名稱。
     * @param registrationIds 此來源新增的註冊識別碼。
     * @return 排序後的報告分類。
     */
    private fun presentationCategory(
        id: String,
        displayName: String,
        vararg registrationIds: String,
    ): MinecraftPresentationRegistrationCategory = MinecraftPresentationRegistrationCategory(
        id,
        displayName,
        registrationIds.sorted(),
    )
}
