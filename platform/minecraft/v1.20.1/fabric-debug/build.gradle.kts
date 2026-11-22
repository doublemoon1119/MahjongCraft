plugins {
    alias(libs.plugins.mahjongcraft.minecraft.loader)
    // debug 情境與規則的資料類型以 kotlinx.serialization 編碼。
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.fabric.loom)
    // 讓 debugKoinApplication 在編譯期檢查 debug 服務的依賴。
    alias(libs.plugins.koin.compiler)
}

version = libs.versions.minecraft.mod.version.get()

configure<com.doublemoon1119.mahjongcraft.buildlogic.MinecraftModPackagingExtension> {
    modId.set("mahjongcraft_debug")
    // 只在開發時使用，CI 不收集它的 JAR。
    distributable.set(false)
    displayName.set("MahjongCraft Debug Tools")
    description.set("Development-only debug commands and tools for MahjongCraft.")
    dependencyRequirements.put("minecraft", mc1201.versions.require.fabric.minecraft)
    dependencyRequirements.put("fabricLoader", mc1201.versions.require.fabric.loader)
    dependencyRequirements.put("fabricApi", mc1201.versions.require.fabric.api)
    dependencyRequirements.put("fabricLanguageKotlin", mc1201.versions.require.fabric.language.kotlin)
}

tasks.withType<Test>().configureEach {
    // 將測試期間由 Minecraft logging 建立的相對檔案限制在可由 clean 移除的 build 目錄。
    val testRunDirectory = layout.buildDirectory.dir("test-runs/$name")
    workingDir(testRunDirectory)
    doFirst {
        testRunDirectory.get().asFile.mkdirs()
    }
}

// debug 中呼叫 Minecraft 1.20.1 但不用載入器的程式在 :minecraft_v1.20.1_mod-debug，不需要 Minecraft 的程式、翻譯與材質在
// :minecraft_common-debug。本體開發執行只載入這個模組、不帶它的依賴，因此兩者的原始碼與資源都納入本模組，由 Loom 一起
// 重新映射並打包。
val commonDebugDirectory = project(":minecraft_common-debug").projectDir
val modDebugDirectory = project(":minecraft_v1.20.1_mod-debug").projectDir
sourceSets {
    main {
        resources.srcDir(commonDebugDirectory.resolve("src/jvmMain/resources"))
    }
}
kotlin.sourceSets.main {
    kotlin.srcDir(commonDebugDirectory.resolve("src/jvmMain/kotlin"))
    kotlin.srcDir(modDebugDirectory.resolve("src/main/kotlin"))
}

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
    testImplementation(libs.kotlinx.serialization.json)
    testImplementation(project(":testing:testing-mahjong-logic"))
    testImplementation(project(":testing:testing-mahjong-flow"))
}
