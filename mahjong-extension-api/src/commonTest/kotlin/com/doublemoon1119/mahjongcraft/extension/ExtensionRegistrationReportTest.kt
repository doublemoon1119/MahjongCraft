package com.doublemoon1119.mahjongcraft.extension

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** 驗證第三方 extension 註冊報告的統計與穩定文字格式。 */
class ExtensionRegistrationReportTest {
    /** 沒有 extension 時只輸出單行零項摘要。 */
    @Test
    fun formatEmptyReport() {
        val report = ExtensionRegistrationReport(emptyList(), emptyList())

        assertEquals("Loaded 0 third-party extensions.", ExtensionRegistrationReportFormatter.format(report))
        assertEquals(0, report.registrationCount)
        assertEquals(0, report.categoryCount)
    }

    /** 有註冊內容時依輸入分類順序輸出正確樹枝與統計。 */
    @Test
    fun formatPopulatedReport() {
        val report = ExtensionRegistrationReport(
            extensionIds = listOf("ext_a", "ext_b"),
            categories = listOf(
                category("tile_asset", "Tile Asset", "asset_a"),
                category("rule_name", "Rule Module Display Name", "rule_a"),
            ),
        )

        assertEquals(
            """
            Loaded 4 third-party registration(s) across 3 categories from 2 extension(s):
            ├── Mahjong Extension (2): [ext_a, ext_b]
            ├── Tile Asset (1): [asset_a]
            └── Rule Module Display Name (1): [rule_a]
            """.trimIndent(),
            ExtensionRegistrationReportFormatter.format(report),
        )
    }

    /** INFO 超過每類上限時標示省略數量，完整格式仍保留全部 ID。 */
    @Test
    fun truncateLongCategoryOnlyInBoundedFormat() {
        val ids = (1..11).map { index -> "asset_${index.toString().padStart(2, '0')}" }
        val report = ExtensionRegistrationReport(
            extensionIds = listOf("ext_assets"),
            categories = listOf(ExtensionRegistrationCategory("tile_asset", "Tile Asset", ids)),
        )

        val bounded = ExtensionRegistrationReportFormatter.format(report)
        val complete = ExtensionRegistrationReportFormatter.format(report, Int.MAX_VALUE)

        assertTrue(report.isTruncatedAt(8))
        assertTrue(bounded.contains("asset_08, … (+3 more)]"))
        assertFalse(bounded.contains("asset_09"))
        assertTrue(complete.contains("asset_11"))
    }

    /** 快照差集只回傳 callback 新增的 key，不包含 built-in key。 */
    @Test
    fun snapshotReportsOnlyAdditions() {
        val baseline = ExtensionRegistrationSnapshot(
            listOf(ExtensionRegistrationSnapshotCategory("tile", "Tile", setOf("built_in"))),
        )
        val current = ExtensionRegistrationSnapshot(
            listOf(
                ExtensionRegistrationSnapshotCategory("tile", "Tile", setOf("built_in", "third_party")),
                ExtensionRegistrationSnapshotCategory("sound", "Sound", setOf("custom_sound")),
            ),
        )

        assertEquals(
            listOf(
                category("tile", "Tile", "third_party"),
                category("sound", "Sound", "custom_sound"),
            ),
            baseline.additionsSince(current),
        )
    }

    /** 建立測試使用的排序後註冊分類。 */
    private fun category(
        id: String,
        displayName: String,
        vararg registrationIds: String,
    ): ExtensionRegistrationCategory = ExtensionRegistrationCategory(id, displayName, registrationIds.sorted())
}
