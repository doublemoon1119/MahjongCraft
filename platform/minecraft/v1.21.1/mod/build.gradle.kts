import com.doublemoon1119.mahjongcraft.buildlogic.includeSharedVersionSources

// Minecraft 1.21.1 中與載入器無關的程式與資源，由各載入器模組一起編譯；這個模組自己只對原版 Minecraft 編譯與測試，
// 確認其中的程式不依賴任何載入器，產出的 JAR 不發布。
//
// 各 Minecraft 版本相同的程式、資源與測試放在跨版本共用的 platform/minecraft/mod，這個模組一起編譯；這裡只放 1.21.1
// 的轉接與版本專屬的檔案，例如資料包與 access transformer。

plugins {
    alias(libs.plugins.mahjongcraft.minecraft.mod)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.moddev)
    alias(libs.plugins.koin.compiler)
    alias(libs.plugins.mahjongcraft.public.api.abi)
}

// 一起編譯跨版本共用的 platform/minecraft/mod。
includeSharedVersionSources()

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

neoForge {
    // 原版模式：只提供套用 Mojang 名稱的原版 Minecraft，不含任何載入器。
    neoFormVersion = mc1211.versions.neoform.get()
    // 讀取 src/main/resources/META-INF/accesstransformer.cfg；Fabric 由本 mod 的 access widener 開放同樣的對象。
    validateAccessTransformers = true
    // 測試同樣以原版 Minecraft 編譯與執行。
    addModdingDependenciesTo(sourceSets.test.get())
}

// 與載入器模組使用相同的專案模組與函式庫；載入器模組編譯這裡的原始碼時自行宣告同樣的依賴，並負責打包。
dependencies {
    // 本體附帶的專案模組與函式庫，與載入器模組相同的共用清單。
    minecraftModLibraries.addTo("implementation")
    implementation(libs.kotlinx.coroutines)
    implementation(libs.kotlinx.serialization.json)
    // 共用的 mixin 只對註解編譯；各載入器在執行時提供 Mixin 與 MixinExtras。
    compileOnly(mc1211.mixin)
    compileOnly(mc1211.mixinextras.common)

    testImplementation(kotlin("test"))
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(project(":testing:testing-mahjong-logic"))
    testImplementation(project(":testing:testing-mahjong-flow"))
}
