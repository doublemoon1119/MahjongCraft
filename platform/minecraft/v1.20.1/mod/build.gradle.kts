// Minecraft 1.20.1 中與載入器無關的程式與資源，由各載入器模組一起編譯；這個模組自己只對原版 Minecraft 編譯與測試，
// 確認其中的程式不依賴任何載入器，產出的 JAR 不發布。
//
// `data/mahjongcraft/recipes` 放在這裡，不放在跨版本共用的 :minecraft_common：配方 JSON 的 `result` 欄位格式會隨
// Minecraft 版本改變（1.20.1 用 `item`，1.21 起改用 `id`），新增版本時在該版本自己的 mod 模組另外維護一份。
// 模型、材質、語言檔目前沒有隨版本改變，維持放在 :minecraft_common。

plugins {
    alias(libs.plugins.mahjongcraft.minecraft.mod)
    alias(libs.plugins.moddev.legacyforge)
}

version = libs.versions.minecraft.mod.version.get()

legacyForge {
    // 原版模式：只提供套用 Mojang 名稱的原版 Minecraft，不含任何載入器。
    mcpVersion = libs.versions.minecraft1201.get()
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

dependencies {
    implementation(project(":minecraft_common"))

    testImplementation(kotlin("test"))
}
