// 開發專用 debug 工具中呼叫 Minecraft 1.20.1、但不使用任何載入器的部分：debug 指令、情境、壓力測試與歷史生成。
// 由各載入器的 debug 模組一起編譯與打包；這個模組自己只對原版 Minecraft 編譯與測試，確認其中的程式不依賴載入器，
// 產出的 JAR 不發布。

plugins {
    alias(libs.plugins.mahjongcraft.minecraft.mod)
    // debug 情境與規則的資料類型以 kotlinx.serialization 編碼。
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.moddev.legacyforge)
    alias(libs.plugins.koin.compiler)
}

version = libs.versions.minecraft.mod.version.get()

legacyForge {
    // 原版模式：只提供套用 Mojang 名稱的原版 Minecraft，不含任何載入器。
    mcpVersion = mc1201.versions.minecraft.get()
    // 與 :minecraft_v1.20.1_mod 讀取同一份 access transformer，debug 程式看得到本體開放的原版成員。
    accessTransformers.from(project(":minecraft_v1.20.1_mod").file("src/main/resources/META-INF/accesstransformer.cfg"))
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
    // 本體的語系檔在 classpath 上與 debug 語系檔的路徑相同，語系檔測試改以來源目錄讀取 debug 自己的檔案。
    val langDirectory = project(":minecraft_common-debug").layout.projectDirectory.dir("src/jvmMain/resources/assets/mahjongcraft/lang")
    inputs.dir(langDirectory).withPropertyName("debugLangDirectory")
    systemProperty("mahjongcraft.debug.langDirectory", langDirectory.asFile.absolutePath)
}

// 與載入器的 debug 模組使用相同的專案模組與函式庫；載入器的 debug 模組編譯這裡的原始碼時自行宣告同樣的依賴。
dependencies {
    implementation(project(":minecraft_v1.20.1_mod"))
    implementation(project(":minecraft_common-debug"))
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
    implementation(libs.exposed.core)
    implementation(libs.exposed.jdbc)

    testImplementation(kotlin("test"))
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(project(":testing:testing-mahjong-logic"))
    testImplementation(project(":testing:testing-mahjong-flow"))
}
