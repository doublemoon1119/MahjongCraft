package com.doublemoon1119.mahjongcraft.platform.minecraft.extension

import kotlin.test.Test
import kotlin.test.assertEquals

/** 驗證 Minecraft presentation registry 診斷快照的差集行為。 */
class MinecraftPresentationRegistrationSnapshotTest {
    /** 差集保留分類順序，且排除 callback 前已存在的 built-in key。 */
    @Test
    fun reportOnlyKeysAddedAfterBaseline() {
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
                category("tile", "Tile", "third_party"),
                category("sound", "Sound", "custom_sound"),
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
}
