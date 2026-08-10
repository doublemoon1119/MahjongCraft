import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

plugins {
    alias(libs.plugins.kotlin.multiplatform)
}

val generatedModMetadataDir = layout.buildDirectory.dir("generated/sources/modMetadata/commonMain/kotlin")
val generateMinecraftModMetadata = tasks.register("generateMinecraftModMetadata") {
    val modId = rootProject.extra["mahjongcraftModId"] as String
    val modName = rootProject.extra["mahjongcraftModName"] as String
    val outputFile = generatedModMetadataDir.map {
        it.file("com/doublemoon1119/mahjongcraft/platform/minecraft/metadata/MinecraftModMetadata.kt")
    }
    inputs.property("modId", modId)
    inputs.property("modName", modName)
    outputs.file(outputFile)
    doLast {
        val file = outputFile.get().asFile
        file.parentFile.mkdirs()
        file.writeText(
            """
            package com.doublemoon1119.mahjongcraft.platform.minecraft.metadata

            /** 由 Gradle mod metadata 單一來源產生的跨 loader 編譯期常數。 */
            object MinecraftModMetadata {
                /** MahjongCraft 的 mod identifier。 */
                const val MOD_ID: String = "$modId"

                /** MahjongCraft 的玩家可見名稱。 */
                const val MOD_NAME: String = "$modName"
            }
            """.trimIndent() + "\n",
        )
    }
}

kotlin {
    jvm()

    sourceSets {
        commonMain {
            kotlin.srcDir(generatedModMetadataDir)
            dependencies {
                implementation(project(":mahjong-logic"))
            }
        }

        commonTest.dependencies {
            implementation(kotlin("test"))
        }
    }
}

tasks.withType<KotlinCompile>().configureEach {
    dependsOn(generateMinecraftModMetadata)
}

tasks.matching { it.name.startsWith("runKtlint") }.configureEach {
    dependsOn(generateMinecraftModMetadata)
}
