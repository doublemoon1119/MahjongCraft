plugins {
    `kotlin-dsl`
    `java-gradle-plugin`

    // build-logic 由 Gradle 的 Kotlin DSL compiler 編譯，因此 serialization compiler plugin
    // 必須跟隨 Gradle distribution 的 embedded Kotlin，而不是 MahjongCraft 的 Kotlin release train。
    // Gradle 只提供 embedded Kotlin 版本；serialization plugin 本身仍會獨立解析。
    kotlin("plugin.serialization") version embeddedKotlinVersion
}

group = "com.doublemoon1119.mahjongcraft.buildlogic"
version = "0.0.0-dev"

gradlePlugin {
    plugins {
        register("baseConvention") {
            id = "mahjongcraft.base"
            implementationClass = "com.doublemoon1119.mahjongcraft.buildlogic.MahjongCraftBasePlugin"
        }
        register("kotlinMultiplatformConvention") {
            id = "mahjongcraft.kotlin-multiplatform"
            implementationClass = "com.doublemoon1119.mahjongcraft.buildlogic.MahjongCraftKotlinMultiplatformPlugin"
        }
        register("kotlinJvmConvention") {
            id = "mahjongcraft.kotlin-jvm"
            implementationClass = "com.doublemoon1119.mahjongcraft.buildlogic.MahjongCraftKotlinJvmPlugin"
        }
        register("minecraftVersionCommonConvention") {
            id = "mahjongcraft.minecraft-version-common"
            implementationClass = "com.doublemoon1119.mahjongcraft.buildlogic.MahjongCraftMinecraftVersionCommonPlugin"
        }
        register("minecraftLoaderConvention") {
            id = "mahjongcraft.minecraft-loader"
            implementationClass = "com.doublemoon1119.mahjongcraft.buildlogic.MahjongCraftMinecraftLoaderPlugin"
        }
        register("platformTargetsSettings") {
            id = "mahjongcraft.platform-targets"
            implementationClass = "com.doublemoon1119.mahjongcraft.buildlogic.PlatformTargetsSettingsPlugin"
        }
        register("targetManagement") {
            id = "mahjongcraft.target-management"
            implementationClass = "com.doublemoon1119.mahjongcraft.buildlogic.TargetManagementPlugin"
        }
        register("repositoryVerification") {
            id = "mahjongcraft.repository-verification"
            implementationClass = "com.doublemoon1119.mahjongcraft.buildlogic.RepositoryVerificationPlugin"
        }
    }
}

dependencies {
    implementation(libs.kotlin.gradle.plugin)
    implementation(libs.ktlint.gradle.plugin)
    implementation(libs.ktoml.core)
    implementation(libs.kotlinx.serialization.json)
    testImplementation(kotlin("test"))
    testImplementation(gradleTestKit())
}

tasks.test {
    useJUnitPlatform()
}
