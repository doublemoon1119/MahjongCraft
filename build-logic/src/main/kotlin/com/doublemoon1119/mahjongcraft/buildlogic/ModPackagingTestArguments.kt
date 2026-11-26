package com.doublemoon1119.mahjongcraft.buildlogic

import org.gradle.api.file.ConfigurableFileCollection
import org.gradle.api.tasks.Classpath
import org.gradle.process.CommandLineArgumentProvider

/** 將已打包的正式與範例 mod 路徑交給測試，不在設定階段要求其他專案先建立任務。 */
abstract class ModPackagingTestArguments : CommandLineArgumentProvider {
    /** 正式 mod 的已重新映射 JAR。 */
    @get:Classpath
    abstract val mainJar: ConfigurableFileCollection

    /** 範例 mod 的已重新映射 JAR。 */
    @get:Classpath
    abstract val exampleJar: ConfigurableFileCollection

    /** 以 JVM 系統屬性提供兩份產物的實際路徑。 */
    override fun asArguments(): Iterable<String> = listOf(
        "-Dmahjongcraft.mainJar=${mainJar.singleFile.absolutePath}",
        "-Dmahjongcraft.exampleJar=${exampleJar.singleFile.absolutePath}",
    )
}
