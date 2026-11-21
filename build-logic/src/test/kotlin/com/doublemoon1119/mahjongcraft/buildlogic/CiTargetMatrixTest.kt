package com.doublemoon1119.mahjongcraft.buildlogic

import kotlin.test.Test
import kotlin.test.assertEquals

/** 驗證 CI 目標矩陣涵蓋 `core` 與每個平台目標，並帶出各自的 Java toolchain。 */
class CiTargetMatrixTest {
    /** 平台目標依目錄順序排在 `core` 之後；有範例模組的目標標示要一起建置範例。 */
    @Test
    fun `matrix lists core and every platform target`() {
        val targets = listOf(
            platformTarget(id = "minecraft-v1.20.1-fabric", javaToolchain = 17, exampleModules = listOf("example/mahjong-extension")),
            platformTarget(id = "minecraft-v1.21.1-neoforge", javaToolchain = 21, exampleModules = emptyList()),
        )

        assertEquals(
            expected = """{"include":[""" +
                """{"target":"core","javaToolchain":17,"examples":false},""" +
                """{"target":"minecraft-v1.20.1-fabric","javaToolchain":17,"examples":true},""" +
                """{"target":"minecraft-v1.21.1-neoforge","javaToolchain":21,"examples":false}]}""",
            actual = ciTargetMatrix(coreJavaToolchain = 17, targets = targets),
        )
    }

    /** 目錄沒有平台目標時只驗證 `core`。 */
    @Test
    fun `matrix without platform targets verifies core only`() {
        assertEquals("""{"include":[{"target":"core","javaToolchain":21,"examples":false}]}""", ciTargetMatrix(coreJavaToolchain = 21, targets = emptyList()))
    }

    /**
     * 建立測試用的平台目標。
     *
     * @param id 目標 ID。
     * @param javaToolchain Java toolchain 版本。
     * @param exampleModules 範例模組路徑。
     * @return 平台目標。
     */
    private fun platformTarget(id: String, javaToolchain: Int, exampleModules: List<String>) = PlatformTarget(
        id = id,
        platform = "minecraft",
        javaToolchain = javaToolchain,
        javaRelease = javaToolchain,
        modules = emptyList(),
        exampleModules = exampleModules,
        attributes = emptyMap(),
    )
}
