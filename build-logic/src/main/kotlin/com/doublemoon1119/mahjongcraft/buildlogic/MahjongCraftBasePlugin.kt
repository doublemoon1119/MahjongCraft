package com.doublemoon1119.mahjongcraft.buildlogic

import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.plugins.BasePluginExtension
import org.gradle.api.tasks.bundling.Jar
import org.gradle.kotlin.dsl.configure
import org.gradle.kotlin.dsl.withType

/** 套用所有 MahjongCraft 模組共用的 Gradle 基礎設定。 */
class MahjongCraftBasePlugin : Plugin<Project> {
    /** 設定 group、Base plugin 與 Jar 內的授權檔。 */
    override fun apply(project: Project) = with(project) {
        pluginManager.apply("base")
        group = "com.doublemoon1119.mahjongcraft"
        val archiveName = extensions.getByType(BasePluginExtension::class.java).archivesName
        tasks.withType<Jar>().configureEach {
            from(rootProject.file("LICENSE")) {
                rename { "${it}_${archiveName.get()}" }
            }
        }
    }
}
