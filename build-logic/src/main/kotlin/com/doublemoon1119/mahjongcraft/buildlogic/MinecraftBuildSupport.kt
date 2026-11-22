package com.doublemoon1119.mahjongcraft.buildlogic

import org.gradle.api.Project
import org.gradle.api.artifacts.dsl.RepositoryHandler
import org.gradle.api.tasks.SourceSetContainer
import org.gradle.kotlin.dsl.getByType
import org.gradle.kotlin.dsl.maven
import org.jetbrains.kotlin.gradle.dsl.KotlinJvmProjectExtension

/**
 * 把另一個專案的原始碼與資源一起編譯並打包進這個模組。
 *
 * 用於載入器模組納入版本 mod 模組等原始碼：那些模組的 JAR 使用 Mojang 名稱，各載入器要把它們與自己的程式一起轉成執行時
 * 的名稱。不存在的資料夾會被略過。
 *
 * @param projectPath 提供原始碼的專案路徑。
 * @param sourceDirectory 相對於該專案的原始碼資料夾，例如 `src/main` 或 Kotlin 多平台模組的 `src/jvmMain`。
 */
fun Project.includeSourcesOf(projectPath: String, sourceDirectory: String = "src/main") {
    val directory = project(projectPath).projectDir.resolve(sourceDirectory)
    extensions.getByType<SourceSetContainer>().named("main").configure {
        java.srcDir(directory.resolve("java"))
        resources.srcDir(directory.resolve("resources"))
    }
    extensions.getByType<KotlinJvmProjectExtension>().sourceSets.named("main").configure {
        kotlin.srcDir(directory.resolve("kotlin"))
    }
}

/**
 * 把另一個專案的資源一起打包進這個模組，例如跨版本共用模組的材質與翻譯。
 *
 * @param projectPath 提供資源的專案路徑。
 * @param sourceDirectory 相對於該專案的原始碼資料夾，例如 Kotlin 多平台模組的 `src/jvmMain`。
 */
fun Project.includeResourcesOf(projectPath: String, sourceDirectory: String) {
    val directory = project(projectPath).projectDir.resolve(sourceDirectory)
    extensions.getByType<SourceSetContainer>().named("main").configure {
        resources.srcDir(directory.resolve("resources"))
    }
}

/** 加入 Modrinth 的 Maven 倉庫；它只提供 Modrinth 的套件，其他套件不會向它查詢。 */
fun RepositoryHandler.modrinth() {
    exclusiveContent {
        forRepository { maven("https://api.modrinth.com/maven") { name = "Modrinth" } }
        filter { includeGroup("maven.modrinth") }
    }
}
