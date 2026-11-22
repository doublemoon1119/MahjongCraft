// 範例擴充中呼叫 Minecraft 1.20.1、但不使用任何載入器的部分：家具的座位、方塊與實體邏輯、角色的座位規則等。
// 由各載入器的範例模組一起編譯與打包；這個模組自己只對原版 Minecraft 編譯與測試，確認其中的程式不依賴載入器，
// 產出的 JAR 不發布。

plugins {
    alias(libs.plugins.mahjongcraft.minecraft.mod)
    alias(libs.plugins.moddev.legacyforge)
}

version = libs.versions.minecraft.mod.version.get()

legacyForge {
    // 原版模式：只提供套用 Mojang 名稱的原版 Minecraft，不含任何載入器。
    mcpVersion = mc1201.versions.minecraft.get()
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

// 與載入器的範例模組使用相同的專案模組；載入器的範例模組編譯這裡的原始碼時自行宣告同樣的依賴。
dependencies {
    implementation(project(":minecraft_v1.20.1_mod"))
    implementation(project(":minecraft_common"))
    implementation(project(":mahjong-extension-api"))
    implementation(project(":example:example-mahjong-extension"))
    implementation(project(":example-minecraft_common"))

    testImplementation(kotlin("test"))
}
