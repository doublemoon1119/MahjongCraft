package com.doublemoon1119.mahjongcraft.buildlogic

import org.gradle.testfixtures.ProjectBuilder
import kotlin.io.path.createTempDirectory
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** 驗證開發用光影包放入執行目錄並開啟。 */
class PrepareDevShaderPackTaskTest {
    /** 複製光影包，在 Iris 設定中指定它並開啟光影，其他設定保留。 */
    @Test
    fun `prepare copies the pack and turns it on`() {
        val root = createTempDirectory("dev-shader-pack").toFile()
        try {
            val pack = root.resolve("cache/pack-r1.zip").apply { parentFile.mkdirs() }.apply { writeText("pack") }
            val runDirectory = root.resolve("run/client")
            val settings = runDirectory.resolve(PrepareDevShaderPackTask.IRIS_SETTINGS).apply { parentFile.mkdirs() }
            settings.writeText("#comment\ncolorSpace=SRGB\nenableShaders=false\nshaderPack=Other.zip\n")
            val project = ProjectBuilder.builder().withProjectDir(root).build()
            val task = project.tasks.register("prepareDevShaderPack", PrepareDevShaderPackTask::class.java) {
                this.runDirectory.set(runDirectory)
                shaderPack.from(pack)
            }.get()

            task.prepare()

            assertTrue(runDirectory.resolve("shaderpacks/pack-r1.zip").isFile)
            assertEquals(listOf("#comment", "colorSpace=SRGB", "shaderPack=pack-r1.zip", "enableShaders=true"), settings.readLines())
        } finally {
            root.deleteRecursively()
        }
    }
}
