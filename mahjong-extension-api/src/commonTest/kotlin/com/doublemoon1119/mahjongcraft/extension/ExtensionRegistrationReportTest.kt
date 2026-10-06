package com.doublemoon1119.mahjongcraft.extension

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

/** 驗證依來源分組的登記報告、合併與穩定文字格式。 */
class ExtensionRegistrationReportTest {
    /** 摘要每個來源只列筆數，內建來源顯示為 built-in。 */
    @Test
    fun formatSummary() {
        assertEquals(
            """
            Enabled 4 registration(s) from 3 source(s):
            ├── built-in (2)
            ├── ext_a (2)
            └── ext_b (0)
            """.trimIndent(),
            ExtensionRegistrationReportFormatter.formatSummary(sampleReport()),
        )
    }

    /** 詳細格式在每個來源底下列出所有類別與 ID。 */
    @Test
    fun formatDetails() {
        assertEquals(
            """
            Enabled 4 registration(s) from 3 source(s):
            ├── built-in (2)
            │   └── Tile Asset (2): [asset_a, asset_b]
            ├── ext_a (2)
            │   ├── Tile Asset (1): [asset_c]
            │   └── Rule Module Display Name (1): [rule_a]
            └── ext_b (0)
            """.trimIndent(),
            ExtensionRegistrationReportFormatter.formatDetails(sampleReport()),
        )
    }

    /** 合併時同一來源只出現一次，類別依結果順序串接，來源依第一次出現的順序排列。 */
    @Test
    fun mergeKeepsFirstSeenSourceOrder() {
        val merged = mergeRegistrationSources(
            listOf(ExtensionRegistrationSource(null, listOf(category("tile", "Tile", "built_in"))), ExtensionRegistrationSource("ext_a", emptyList())),
            listOf(
                ExtensionRegistrationSource(null, listOf(category("sound", "Sound", "built_in_sound"))),
                ExtensionRegistrationSource("ext_a", listOf(category("sound", "Sound", "custom_sound"))),
                ExtensionRegistrationSource("ext_b", emptyList()),
            ),
        )

        assertEquals(
            listOf(
                ExtensionRegistrationSource(null, listOf(category("tile", "Tile", "built_in"), category("sound", "Sound", "built_in_sound"))),
                ExtensionRegistrationSource("ext_a", listOf(category("sound", "Sound", "custom_sound"))),
                ExtensionRegistrationSource("ext_b", emptyList()),
            ),
            merged,
        )
    }

    /** 同一來源不能在報告中出現兩次。 */
    @Test
    fun rejectDuplicateSources() {
        assertFailsWith<IllegalArgumentException> {
            ExtensionRegistrationReport(listOf(ExtensionRegistrationSource("ext_a", emptyList()), ExtensionRegistrationSource("ext_a", emptyList())))
        }
    }

    /** 快照列出所有非空類別，差集只回傳之後新增的 key。 */
    @Test
    fun snapshotListsCategoriesAndAdditions() {
        val baseline = ExtensionRegistrationSnapshot(
            listOf(
                ExtensionRegistrationSnapshotCategory("tile", "Tile", setOf("built_in")),
                ExtensionRegistrationSnapshotCategory("sound", "Sound", emptySet()),
            ),
        )
        val current = ExtensionRegistrationSnapshot(
            listOf(
                ExtensionRegistrationSnapshotCategory("tile", "Tile", setOf("built_in", "third_party")),
                ExtensionRegistrationSnapshotCategory("sound", "Sound", setOf("custom_sound")),
            ),
        )

        assertEquals(listOf(category("tile", "Tile", "built_in")), baseline.toCategories())
        assertEquals(
            listOf(
                category("tile", "Tile", "third_party"),
                category("sound", "Sound", "custom_sound"),
            ),
            baseline.additionsSince(current),
        )
    }

    /** 內建來源與兩個 extension 的測試報告。 */
    private fun sampleReport(): ExtensionRegistrationReport = ExtensionRegistrationReport(
        listOf(
            ExtensionRegistrationSource(null, listOf(category("tile_asset", "Tile Asset", "asset_a", "asset_b"))),
            ExtensionRegistrationSource(
                "ext_a",
                listOf(category("tile_asset", "Tile Asset", "asset_c"), category("rule_name", "Rule Module Display Name", "rule_a")),
            ),
            ExtensionRegistrationSource("ext_b", emptyList()),
        ),
    )

    /** 建立測試使用的排序後註冊分類。 */
    private fun category(
        id: String,
        displayName: String,
        vararg registrationIds: String,
    ): ExtensionRegistrationCategory = ExtensionRegistrationCategory(id, displayName, registrationIds.sorted())
}
