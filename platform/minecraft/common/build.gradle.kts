plugins {
    alias(libs.plugins.mahjongcraft.kotlin.multiplatform)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.koin.compiler)
}

version = libs.versions.minecraft.mod.version.get()

kotlin {
    jvm()

    sourceSets {
        commonMain {
            dependencies {
                implementation(project(":mahjong-logic"))
                implementation(project(":mahjong-ai"))
                implementation(project(":mahjong-flow:mahjong-flow-common"))
                implementation(project.dependencies.platform(libs.koin.bom))
                implementation(libs.koin.core)
                implementation(libs.koin.annotations)
                implementation(libs.ktoml.core)
            }
        }

        commonTest.dependencies {
            implementation(kotlin("test"))
        }

        jvmTest {
            dependencies {
                implementation(libs.kotlinx.serialization.json)
            }
            // 部分資源檔驗證測試（例如配方 JSON）驗證的是實際放在 :minecraft_v1.20.1_common 的檔案
            // （見該模組 build.gradle.kts 頂部註解：配方格式會隨 Minecraft 版本破版，不放在真正跨版本
            // 共用的本模組）；這裡多接一條 srcDir 讓測試 classpath 看得到它，不需要把測試搬到另一個
            // 模組或複製一份資源。
            resources.srcDir(project(":minecraft_v1.20.1_common").projectDir.resolve("src/jvmMain/resources"))
        }
    }
}
