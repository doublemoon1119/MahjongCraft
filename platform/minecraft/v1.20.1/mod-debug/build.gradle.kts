import com.doublemoon1119.mahjongcraft.buildlogic.includeSharedVersionSources
import com.doublemoon1119.mahjongcraft.buildlogic.minecraftModAccessTransformer

// 開發專用 debug 工具中呼叫 Minecraft 1.20.1、但不使用任何載入器的部分：debug 指令、情境、壓力測試、歷史生成與自動測試。
// 由各載入器的 debug 模組一起編譯與打包；這個模組自己只對原版 Minecraft 編譯與測試，確認其中的程式不依賴載入器，
// 產出的 JAR 不發布。各 Minecraft 版本相同的程式與測試放在跨版本共用的 platform/minecraft/mod-debug，這裡只放 1.20.1
// 專屬的部分，例如自動測試的 mixin。

plugins {
    alias(libs.plugins.mahjongcraft.minecraft.mod)
    // debug 情境與規則的資料類型以 kotlinx.serialization 編碼。
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.moddev.legacyforge)
    alias(libs.plugins.koin.compiler)
}

// 一起編譯跨版本共用的 platform/minecraft/mod-debug。
includeSharedVersionSources()

legacyForge {
    // 原版模式：只提供套用 Mojang 名稱的原版 Minecraft，不含任何載入器。
    mcpVersion = mc1201.versions.minecraft.get()
    // 與 :minecraft_v1.20.1_mod 讀取同一份 access transformer，debug 程式看得到本體開放的原版成員。
    accessTransformers.from(minecraftModAccessTransformer())
    validateAccessTransformers = true
    // 測試同樣以原版 Minecraft 編譯與執行。
    addModdingDependenciesTo(sourceSets.test.get())
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
    // 自動測試的 mixin 只對註解編譯；各載入器在執行時提供 Mixin。
    compileOnly(mc1201.mixin)
    // 本體附帶的專案模組與函式庫，與載入器模組相同的共用清單。
    minecraftModLibraries.addTo("implementation")
    implementation(libs.kotlinx.coroutines)
    implementation(libs.kotlinx.serialization.json)

    testImplementation(kotlin("test"))
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(project(":testing:testing-mahjong-logic"))
    testImplementation(project(":testing:testing-mahjong-flow"))
}
