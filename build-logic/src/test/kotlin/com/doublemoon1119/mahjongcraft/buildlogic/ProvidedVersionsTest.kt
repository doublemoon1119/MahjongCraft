package com.doublemoon1119.mahjongcraft.buildlogic

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** 驗證專案版本與建置目標提供版本的比對。 */
class ProvidedVersionsTest {
    /** 版本逐段比較，缺少的段落視為 0。 */
    @Test
    fun `compares numeric versions by segment`() {
        assertEquals(0, compareNumericVersions("2.4", "2.4.0"))
        assertTrue(compareNumericVersions("2.4", "2.4.21")!! < 0)
        assertTrue(compareNumericVersions("1.13.0", "1.11.0")!! > 0)
        assertTrue(compareNumericVersions("1.10.0", "1.9.0")!! > 0)
        assertNull(compareNumericVersions("1.11.0-RC", "1.11.0"))
    }

    /** 版本不高於每個目標提供的版本時沒有違規。 */
    @Test
    fun `accepts versions within every target`() {
        val violations = findProvidedVersionViolations(
            kotlinApiVersion = "2.4",
            catalogVersions = mapOf("kotlinx-serialization" to "1.11.0"),
            providedVersions = mapOf(
                "older" to mapOf("kotlin" to "2.4.20", "kotlinx-serialization" to "1.11.0"),
                "newer" to mapOf("kotlin" to "2.4.21", "kotlinx-serialization" to "1.13.0"),
            ),
        )

        assertEquals(emptyList(), violations)
    }

    /** Kotlin 只比對 API 版本上限，編譯器版本可以較新。 */
    @Test
    fun `compares the kotlin api version instead of the compiler`() {
        val violations = findProvidedVersionViolations(
            kotlinApiVersion = "2.5",
            catalogVersions = mapOf("kotlin" to "2.4.0"),
            providedVersions = mapOf("target" to mapOf("kotlin" to "2.4.20")),
        )

        assertEquals(listOf("target provides kotlin 2.4.20, lower than kotlin-api-version 2.5"), violations)
    }

    /** 函式庫版本高於任一目標提供的版本時列出該目標。 */
    @Test
    fun `reports a library newer than one target provides`() {
        val violations = findProvidedVersionViolations(
            kotlinApiVersion = "2.4",
            catalogVersions = mapOf("kotlinx-serialization" to "1.13.0"),
            providedVersions = mapOf(
                "older" to mapOf("kotlinx-serialization" to "1.11.0"),
                "newer" to mapOf("kotlinx-serialization" to "1.13.0"),
            ),
        )

        assertEquals(listOf("older provides kotlinx-serialization 1.11.0, lower than kotlinx-serialization 1.13.0"), violations)
    }

    /** 目標提到 catalog 沒有的版本名稱時視為錯誤，避免打錯字讓檢查失效。 */
    @Test
    fun `reports a provided name missing from the catalog`() {
        val violations = findProvidedVersionViolations(
            kotlinApiVersion = "2.4",
            catalogVersions = emptyMap(),
            providedVersions = mapOf("target" to mapOf("kotlinx-coroutine" to "1.11.0")),
        )

        assertEquals(listOf("target provides kotlinx-coroutine, which is not a libs catalog version"), violations)
    }

    /** 無法比較的版本視為錯誤。 */
    @Test
    fun `reports versions that cannot be compared`() {
        val violations = findProvidedVersionViolations(
            kotlinApiVersion = "2.4",
            catalogVersions = mapOf("kotlinx-datetime" to "0.7.1-0.6.x-compat"),
            providedVersions = mapOf("target" to mapOf("kotlinx-datetime" to "0.8.0")),
        )

        assertEquals(
            listOf("target: cannot compare kotlinx-datetime 0.7.1-0.6.x-compat with provided kotlinx-datetime 0.8.0"),
            violations,
        )
    }
}
