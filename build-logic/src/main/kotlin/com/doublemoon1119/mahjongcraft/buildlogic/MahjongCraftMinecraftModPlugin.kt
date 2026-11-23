package com.doublemoon1119.mahjongcraft.buildlogic

import org.gradle.api.Plugin
import org.gradle.api.Project

/**
 * 套用 Minecraft 版本 mod 模組的 target Java 政策；測試的工作目錄放在 build 目錄中。
 *
 * mod 模組放某個 Minecraft 版本中與載入器無關的程式與資源，由各載入器模組一起編譯；它自己以原版 Minecraft 編譯與測試，
 * 確認其中的程式不依賴任何載入器。提供原版 Minecraft 的建置外掛由模組自己的建置檔套用；套用後，準備 Minecraft 的工具改用
 * 目前 target 的 Java。它與載入器模組一樣，以 [MinecraftModLibrariesExtension] 的共用清單宣告依賴。
 */
class MahjongCraftMinecraftModPlugin : Plugin<Project> {
    /** 套用 Kotlin JVM convention 並覆寫為目前 target 的 Java 設定。 */
    override fun apply(project: Project) = with(project) {
        pluginManager.apply("mahjongcraft.kotlin-jvm")
        configureKotlinJvm(targetJavaToolchain(), targetJavaRelease(), overrideDefaults = true)
        extensions.create("minecraftModLibraries", MinecraftModLibrariesExtension::class.java, this)
        isolateTestRunDirectory()
        useTargetJavaForNeoForm()
    }
}
