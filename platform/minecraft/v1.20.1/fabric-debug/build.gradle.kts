import com.doublemoon1119.mahjongcraft.buildlogic.includeSourcesOf
import com.doublemoon1119.mahjongcraft.buildlogic.includeTestSourcesOf

plugins {
    alias(libs.plugins.mahjongcraft.minecraft.loader)
    // debug 情境與規則的資料類型以 kotlinx.serialization 編碼。
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.fabric.loom)
    // 讓 debugKoinApplication 在編譯期檢查 debug 服務的依賴。
    alias(libs.plugins.koin.compiler)
}

configure<com.doublemoon1119.mahjongcraft.buildlogic.MinecraftModPackagingExtension> {
    modId.set("mahjongcraft_debug")
    // 版本與本體相同；fabric.mod.json 要求同一版本的本體。
    loader.set("fabric")
    // 只在開發時使用，CI 不收集它的 JAR。
    distributable.set(false)
    displayName.set("MahjongCraft Debug Tools")
    description.set("Development-only debug commands and tools for MahjongCraft.")
    dependencyRequirements.put("minecraft", mc1201.versions.require.fabric.minecraft)
    dependencyRequirements.put("fabricLoader", mc1201.versions.require.fabric.loader)
    dependencyRequirements.put("fabricApi", mc1201.versions.require.fabric.api)
    dependencyRequirements.put("fabricLanguageKotlin", mc1201.versions.require.fabric.language.kotlin)
}

// debug 中呼叫 Minecraft 但不用載入器的程式在 :minecraft_v1.20.1_mod-debug 與它一起編譯的跨版本共用
// platform/minecraft/mod-debug，不需要 Minecraft 的程式、翻譯與材質在 :minecraft_common-debug。本體開發執行只載入這個模組、不帶它的依賴，因此兩者的原始碼與資源都納入本模組，由 Loom 一起
// 重新映射並打包。
includeSourcesOf(":minecraft_common-debug", "src/jvmMain")
includeSourcesOf(":minecraft_v1.20.1_mod-debug")
// 各載入器共用的 debug 組裝測試；本模組提供 Fabric 的服務組合。
includeTestSourcesOf(":minecraft_v1.20.1_mod-debug", "src/loaderTest/kotlin")

dependencies {
    minecraft(mc1201.minecraft)
    mappings(loom.officialMojangMappings())
    modImplementation(mc1201.fabric.loader)
    modImplementation(mc1201.fabric.api)
    modImplementation(mc1201.fabric.language.kotlin)

    // Loom 子專案使用未重新映射的 namedElements，避免在開發環境讀取 intermediary 名稱。
    implementation(project(path = ":minecraft_v1.20.1_fabric", configuration = "namedElements"))
    // 執行與測試時沿用本體完整的依賴清單。
    runtimeOnly(project(path = ":minecraft_v1.20.1_fabric", configuration = "namedRuntimeElements"))
    // 本體附帶的專案模組與函式庫，與載入器模組相同的共用清單。
    minecraftModLibraries.addTo("implementation")
    implementation(libs.kotlinx.coroutines)
    implementation(libs.kotlinx.serialization.json)

    testImplementation(kotlin("test"))
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.kotlinx.serialization.json)
    testImplementation(project(":testing:testing-mahjong-logic"))
    testImplementation(project(":testing:testing-mahjong-flow"))
}
