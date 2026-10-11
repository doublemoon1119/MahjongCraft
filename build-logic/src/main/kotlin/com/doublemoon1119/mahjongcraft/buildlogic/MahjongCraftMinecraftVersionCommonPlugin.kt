package com.doublemoon1119.mahjongcraft.buildlogic

import org.gradle.api.Plugin
import org.gradle.api.Project

/** 套用 Minecraft 版本特定 common module 的 target Java 政策。 */
class MahjongCraftMinecraftVersionCommonPlugin : Plugin<Project> {
    /** 套用 KMP convention 並覆寫為目前 target 的 Java 設定。 */
    override fun apply(project: Project) = with(project) {
        pluginManager.apply("mahjongcraft.kotlin-multiplatform")
        configureMultiplatformJvm(targetJavaToolchain(), targetJavaRelease(), overrideDefaults = true)
    }
}
