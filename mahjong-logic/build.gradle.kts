import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

plugins {
    alias(libs.plugins.kotlin.multiplatform)
}

val generatedProjectMetadataDir = layout.buildDirectory.dir("generated/sources/projectMetadata/commonMain/kotlin")
val generateProjectMetadata = tasks.register("generateProjectMetadata") {
    group = "build setup"
    description = "Generates shared compile-time project metadata from the root Gradle configuration."
    val projectId = rootProject.extra["projectId"] as String
    val projectDisplayName = rootProject.extra["projectDisplayName"] as String
    val outputFile = generatedProjectMetadataDir.map {
        it.file("com/doublemoon1119/mahjongcraft/metadata/GeneratedMahjongCraftMetadata.kt")
    }
    inputs.property("projectId", projectId)
    inputs.property("projectDisplayName", projectDisplayName)
    outputs.file(outputFile)
    doLast {
        val file = outputFile.get().asFile
        file.parentFile.mkdirs()
        file.writeText(
            """
            package com.doublemoon1119.mahjongcraft.metadata

            /** 由 Gradle project metadata 產生的內部編譯期常數。 */
            internal object GeneratedMahjongCraftMetadata {
                /** MahjongCraft 的穩定專案 ID。 */
                const val PROJECT_ID: String = "$projectId"

                /** MahjongCraft 的玩家可見名稱。 */
                const val PROJECT_DISPLAY_NAME: String = "$projectDisplayName"
            }
            """.trimIndent() + "\n",
        )
    }
}

kotlin {
    jvm()

    sourceSets {
        commonMain {
            kotlin.srcDir(generatedProjectMetadataDir)
        }

        commonTest.dependencies {
            implementation(kotlin("test"))
            implementation(project(":testing:testing-mahjong-logic"))
        }
    }
}

tasks.withType<KotlinCompile>().configureEach {
    dependsOn(generateProjectMetadata)
}

tasks.matching { it.name.startsWith("runKtlint") }.configureEach {
    dependsOn(generateProjectMetadata)
}
