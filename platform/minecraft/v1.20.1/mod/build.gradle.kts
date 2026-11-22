// Minecraft 1.20.1 中與載入器無關的程式與資源，由各載入器模組一起編譯；這個模組自己只對原版 Minecraft 編譯與測試，
// 確認其中的程式不依賴任何載入器，產出的 JAR 不發布。
//
// `data/mahjongcraft/recipes` 放在這裡，不放在跨版本共用的 :minecraft_common：配方 JSON 的 `result` 欄位格式會隨
// Minecraft 版本改變（1.20.1 用 `item`，1.21 起改用 `id`），新增版本時在該版本自己的 mod 模組另外維護一份。
// 模型、材質、語言檔目前沒有隨版本改變，維持放在 :minecraft_common。

plugins {
    alias(libs.plugins.mahjongcraft.minecraft.mod)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.moddev.legacyforge)
    alias(libs.plugins.koin.compiler)
    alias(libs.plugins.mahjongcraft.public.api.abi)
}

publicApiAbi {
    includedNames.addAll(
        "com.doublemoon1119.mahjongcraft.platform.minecraft.mod.api.event.**",
        "com.doublemoon1119.mahjongcraft.platform.minecraft.mod.api.character.**",
        "com.doublemoon1119.mahjongcraft.platform.minecraft.mod.api.seating.**",
        "com.doublemoon1119.mahjongcraft.platform.minecraft.mod.api.registration.**",
        "com.doublemoon1119.mahjongcraft.platform.minecraft.mod.api.table.**",
        "com.doublemoon1119.mahjongcraft.platform.minecraft.mod.api.tableprop.**",
        "com.doublemoon1119.mahjongcraft.platform.minecraft.mod.extension.NativeMahjongExtension",
        "com.doublemoon1119.mahjongcraft.platform.minecraft.mod.extension.NativeMahjongClientExtension",
    )
}

version = libs.versions.minecraft.mod.version.get()

legacyForge {
    // 原版模式：只提供套用 Mojang 名稱的原版 Minecraft，不含任何載入器。
    mcpVersion = mc1201.versions.minecraft.get()
    // 預設讀取 src/main/resources/META-INF/accesstransformer.cfg；開放的對象與 Fabric 的 access widener 相同，Forge 執行時讀同一份。
    validateAccessTransformers = true
    // 測試同樣以原版 Minecraft 編譯與執行。
    addModdingDependenciesTo(sourceSets.test.get())
}

// 準備 Minecraft 的工具預設另找 Java 21 執行；改用執行 Gradle 的 Java，不需要另外安裝或下載 JDK（CI 停用自動下載）。
tasks.withType<net.neoforged.nfrtgradle.NeoFormRuntimeTask>().configureEach {
    javaExecutable.set(
        javaToolchains.launcherFor { languageVersion.set(JavaLanguageVersion.current()) }
            .map { it.executablePath.asFile.absolutePath },
    )
}

tasks.withType<Test>().configureEach {
    // 將測試期間由 Minecraft logging 建立的相對檔案限制在可由 clean 移除的 build 目錄。
    val testRunDirectory = layout.buildDirectory.dir("test-runs/$name")
    workingDir(testRunDirectory)
    doFirst {
        testRunDirectory.get().asFile.mkdirs()
    }
}

// 與載入器模組使用相同的專案模組與函式庫；載入器模組編譯這裡的原始碼時自行宣告同樣的依賴，並負責打包。
dependencies {
    implementation(project(":minecraft_common"))
    implementation(project(":mahjong-logic"))
    implementation(project(":mahjong-ai"))
    implementation(project(":mahjong-flow:mahjong-flow-common"))
    implementation(project(":mahjong-flow:mahjong-flow-client"))
    implementation(project(":mahjong-flow:mahjong-flow-server"))
    implementation(project(":mahjong-flow:mahjong-flow-network-dto"))
    implementation(project(":mahjong-flow:mahjong-flow-persistence-format"))
    implementation(project(":mahjong-extension-api"))
    implementation(project(":mahjong-bundled-extensions"))
    implementation(project.dependencies.platform(libs.koin.bom))
    implementation(libs.koin.core)
    implementation(libs.kotlinx.coroutines)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.ktoml.core)
    implementation(libs.exposed.core)
    implementation(libs.exposed.jdbc)
    implementation(libs.sqlite.jdbc)

    testImplementation(kotlin("test"))
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(project(":testing:testing-mahjong-logic"))
    testImplementation(project(":testing:testing-mahjong-flow"))
}
