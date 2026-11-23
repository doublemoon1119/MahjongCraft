import com.doublemoon1119.mahjongcraft.buildlogic.minecraftModAccessTransformer

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
    // 範例一定與本體一起執行，本體開放的原版類別與成員在執行時都可使用；以本體的 access transformer 編譯，與本體的程式相同。
    accessTransformers.from(minecraftModAccessTransformer())
    // 測試同樣以原版 Minecraft 編譯與執行。
    addModdingDependenciesTo(sourceSets.test.get())
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
