package com.doublemoon1119.mahjongcraft.buildlogic

import org.gradle.api.GradleException
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

/** 驗證 target 提供的函式庫名稱對應到 mod 不附帶的模組。 */
class RuntimeProvidedModulesTest {
    /** 沒有提供任何函式庫時，只排除 Minecraft 附帶的 SLF4J。 */
    @Test
    fun `excludes only minecraft libraries without provided versions`() {
        assertEquals(listOf(RuntimeProvidedModule("org.slf4j")), runtimeProvidedModules(emptySet()))
    }

    /** Kotlin 排除整個 group，kotlinx 函式庫排除通用與 JVM 兩種座標。 */
    @Test
    fun `maps provided names to modules`() {
        val modules = runtimeProvidedModules(setOf("kotlin", "kotlinx-coroutines", "kotlinx-serialization"))

        assertTrue(RuntimeProvidedModule("org.jetbrains.kotlin") in modules)
        assertTrue(RuntimeProvidedModule("org.jetbrains.kotlinx", "kotlinx-coroutines-core-jvm") in modules)
        assertTrue(RuntimeProvidedModule("org.jetbrains.kotlinx", "kotlinx-serialization-json") in modules)
        assertTrue(RuntimeProvidedModule("org.jetbrains.kotlinx", "kotlinx-serialization-core-jvm") in modules)
    }

    /** 沒有對應模組的名稱會讓建置失敗，避免該附帶或該排除的函式庫被默默忽略。 */
    @Test
    fun `rejects unknown provided names`() {
        assertFailsWith<GradleException> { runtimeProvidedModules(setOf("kotlinx-datetime")) }
    }
}
