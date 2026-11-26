package com.doublemoon1119.mahjongcraft.buildlogic

import org.gradle.api.GradleException
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

/** 驗證 CI 目標矩陣涵蓋 `core` 與每個平台目標，並帶出各自要安裝的 JDK。 */
class CiTargetMatrixTest {
    /**
     * 平台目標依目錄順序排在 `core` 之後。
     *
     * - 有範例模組的目標標示要一起建置範例。
     * - 建置工具另外需要的 JDK 排在編譯用的 toolchain 之後，與 toolchain 相同的版本不重複列出。
     */
    @Test
    fun `matrix lists core and every platform target`() {
        val targets = listOf(
            platformTarget(
                id = "minecraft-v1.20.1-fabric",
                javaToolchain = 17,
                additionalJavaToolchains = listOf(21),
                exampleModules = listOf("example/mahjong-extension"),
            ),
            platformTarget(id = "minecraft-v1.21.1-neoforge", javaToolchain = 21, additionalJavaToolchains = listOf(21), exampleModules = emptyList()),
        )

        assertEquals(
            expected = """{"include":[""" +
                """{"target":"core","javaToolchains":[17],"examples":false},""" +
                """{"target":"minecraft-v1.20.1-fabric","javaToolchains":[17,21],"examples":true},""" +
                """{"target":"minecraft-v1.21.1-neoforge","javaToolchains":[21],"examples":false}]}""",
            actual = ciTargetMatrix(coreJavaToolchain = 17, targets = targets),
        )
    }

    /** 指定平台時只列出那個平台的目標；`core` 只列出內建的 `core`；目錄中沒有的平台被拒絕。 */
    @Test
    fun `matrix can be limited to one platform`() {
        val targets = listOf(
            platformTarget(id = "minecraft-v1.21.1-fabric", javaToolchain = 21, additionalJavaToolchains = emptyList(), exampleModules = emptyList()),
        )

        assertEquals(
            expected = """{"include":[{"target":"minecraft-v1.21.1-fabric","javaToolchains":[21],"examples":false}]}""",
            actual = ciTargetMatrix(coreJavaToolchain = 17, targets = targets, platform = "minecraft"),
        )
        assertEquals(
            expected = """{"include":[{"target":"core","javaToolchains":[17],"examples":false}]}""",
            actual = ciTargetMatrix(coreJavaToolchain = 17, targets = targets, platform = "core"),
        )
        val failure = assertFailsWith<GradleException> { ciTargetMatrix(coreJavaToolchain = 17, targets = targets, platform = "web") }
        assertEquals("No platform targets are declared for CI platform 'web'", failure.message)
    }

    /** 目錄沒有平台目標時只驗證 `core`。 */
    @Test
    fun `matrix without platform targets verifies core only`() {
        assertEquals("""{"include":[{"target":"core","javaToolchains":[21],"examples":false}]}""", ciTargetMatrix(coreJavaToolchain = 21, targets = emptyList()))
    }

    /**
     * 建立測試用的平台目標。
     *
     * @param id 目標 ID。
     * @param javaToolchain Java toolchain 版本。
     * @param additionalJavaToolchains 建置工具另外需要的 JDK 版本。
     * @param exampleModules 範例模組路徑。
     * @return 平台目標。
     */
    private fun platformTarget(
        id: String,
        javaToolchain: Int,
        additionalJavaToolchains: List<Int>,
        exampleModules: List<String>,
    ) = PlatformTarget(
        id = id,
        platform = "minecraft",
        javaToolchain = javaToolchain,
        javaRelease = javaToolchain,
        additionalJavaToolchains = additionalJavaToolchains,
        modules = emptyList(),
        exampleModules = exampleModules,
        attributes = emptyMap(),
    )
}
