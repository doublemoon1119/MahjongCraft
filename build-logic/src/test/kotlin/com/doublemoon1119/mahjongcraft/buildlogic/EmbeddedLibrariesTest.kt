package com.doublemoon1119.mahjongcraft.buildlogic

import kotlin.test.Test
import kotlin.test.assertEquals

/** 驗證 jar-in-jar 清單的讀取與缺少函式庫的比對。 */
class EmbeddedLibrariesTest {
    /** 清單中每個 JAR 以座標與實際版本表示。 */
    @Test
    fun `reads embedded libraries from jar in jar metadata`() {
        val metadata = """
            {
              "jars": [
                {
                  "identifier": { "group": "io.insert-koin", "artifact": "koin-core-jvm" },
                  "version": { "range": "[4.2.2,)", "artifactVersion": "4.2.2" },
                  "path": "META-INF/jarjar/koin-core-jvm-4.2.2.jar",
                  "isObfuscated": false
                }
              ]
            }
        """.trimIndent()

        assertEquals(setOf("io.insert-koin:koin-core-jvm:4.2.2"), embeddedLibraries(metadata))
    }

    /** 全部都已附帶時沒有缺少。 */
    @Test
    fun `accepts every required library embedded`() {
        val libraries = setOf("io.insert-koin:koin-core-jvm:4.2.2", "co.touchlab:stately-strict-jvm:2.1.0")

        assertEquals(emptyList(), findMissingEmbeddedLibraries(libraries, libraries + "org.example:extra:1.0"))
    }

    /** 沒有附帶的函式庫與版本不同的函式庫都列出。 */
    @Test
    fun `reports missing and mismatched libraries`() {
        val missing = findMissingEmbeddedLibraries(
            required = setOf("co.touchlab:stately-strict-jvm:2.1.0", "io.insert-koin:koin-core-jvm:4.2.2"),
            embedded = setOf("io.insert-koin:koin-core-jvm:4.2.1"),
        )

        assertEquals(
            listOf("co.touchlab:stately-strict-jvm:2.1.0", "io.insert-koin:koin-core-jvm:4.2.2 (embedded version 4.2.1)"),
            missing,
        )
    }
}
