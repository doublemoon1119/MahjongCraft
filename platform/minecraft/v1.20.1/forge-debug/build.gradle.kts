import com.doublemoon1119.mahjongcraft.buildlogic.MinecraftModPackagingExtension
import com.doublemoon1119.mahjongcraft.buildlogic.hideForgeDeprecationsOfVanillaApis
import com.doublemoon1119.mahjongcraft.buildlogic.includeSourcesOf
import com.doublemoon1119.mahjongcraft.buildlogic.includeTestSourcesOf
import com.doublemoon1119.mahjongcraft.buildlogic.kotlinForForge
import com.doublemoon1119.mahjongcraft.buildlogic.minecraftModAccessTransformer

plugins {
    alias(libs.plugins.mahjongcraft.minecraft.loader)
    // debug 情境與規則的資料類型以 kotlinx.serialization 編碼。
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.moddev.legacyforge)
    // 讓 debugKoinApplication 在編譯期檢查 debug 服務的依賴。
    alias(libs.plugins.koin.compiler)
}

configure<MinecraftModPackagingExtension> {
    modId.set("mahjongcraft_debug")
    // 版本與本體相同；mods.toml 要求同一版本的本體。
    loader.set("forge")
    // 只在開發時使用，CI 不收集它的 JAR。
    distributable.set(false)
    displayName.set("MahjongCraft Debug Tools")
    description.set("Development-only debug commands and tools for MahjongCraft.")
    dependencyRequirements.put("minecraft", mc1201.versions.require.forge.minecraft)
    dependencyRequirements.put("forge", mc1201.versions.require.forge.loader)
    dependencyRequirements.put("kotlinForForge", mc1201.versions.require.forge.kotlinforforge)
}

repositories {
    // 本體的執行依賴包含 Kotlin for Forge，它只發布在作者自己的 Maven 倉庫。
    kotlinForForge()
}

// debug 中呼叫 Minecraft 1.20.1 但不用載入器的程式在 :minecraft_v1.20.1_mod-debug，不需要 Minecraft 的程式、翻譯與材質在
// :minecraft_common-debug。本體的開發執行以本模組的輸出載入 debug 工具，因此兩者的原始碼與資源都納入本模組，由 ModDevGradle
// 一起轉成 Forge 執行時的名稱。
includeSourcesOf(":minecraft_common-debug", "src/jvmMain")
includeSourcesOf(":minecraft_v1.20.1_mod-debug")
// 各載入器共用的 debug 組裝測試；本模組提供 Forge 的服務組合。
includeTestSourcesOf(":minecraft_v1.20.1_mod-debug", "src/loaderTest/kotlin")

// 與 Fabric 共用的程式只能用原版寫法，不顯示 Forge 對原版寫法的過時警告。
hideForgeDeprecationsOfVanillaApis()

// 本體的開發執行以這份輸出載入 debug 工具。Koin 編譯外掛產生的 hints 只在編譯期使用，本體的輸出也有這個套件；Forge 不允許
// 兩個 mod 有同名套件，因此去掉後再交給本體，與本體的 JAR 排除它的做法相同。
val prepareDevMod = tasks.register<Sync>("prepareDevMod") {
    from(sourceSets.main.map { it.output }) {
        exclude("org/koin/plugin/hints/**")
    }
    into(layout.buildDirectory.dir("devMod"))
}
sourceSets.create("devMod") {
    output.dir(mapOf("builtBy" to prepareDevMod), layout.buildDirectory.dir("devMod"))
}

legacyForge {
    version = mc1201.versions.forge.get()
    // 與本體使用同一份 access transformer，debug 程式看得到本體開放的原版成員。
    accessTransformers.from(minecraftModAccessTransformer())
    // 單元測試只測不需要啟動遊戲的程式；測試也要對 Minecraft 與 Forge 的類別編譯。
    addModdingDependenciesTo(sourceSets.test.get())
}

dependencies {
    // 本模組沒有自己的開發執行；這些依賴只供編譯與測試，開發執行時由本體提供。
    implementation(project(":minecraft_v1.20.1_forge"))
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
