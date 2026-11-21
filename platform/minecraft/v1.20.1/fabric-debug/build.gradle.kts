plugins {
    alias(libs.plugins.mahjongcraft.minecraft.loader)
    // 與本體相同的 Kotlin 編譯器外掛組合：外掛組合不同時兩個專案以不同的類別載入器載入 Loom，Loom 設定 Minecraft 會失敗。
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.fabric.loom)
    alias(libs.plugins.koin.compiler)
}

version = libs.versions.minecraft.mod.version.get()

configure<com.doublemoon1119.mahjongcraft.buildlogic.MinecraftModPackagingExtension> {
    modId.set("mahjongcraft_debug")
    displayName.set("MahjongCraft Debug Tools")
    description.set("Development-only debug commands and tools for MahjongCraft.")
    dependencyRequirements.put("minecraft", libs.versions.minecraft1201)
    dependencyRequirements.put("fabricLoader", libs.versions.fabricLoaderRequirement1201)
    dependencyRequirements.put("fabricApi", libs.versions.fabricApiRequirement1201)
    dependencyRequirements.put("fabricLanguageKotlin", libs.versions.fabricLanguageKotlinRequirement1201)
}

tasks.withType<Test>().configureEach {
    // 將測試期間由 Minecraft logging 建立的相對檔案限制在可由 clean 移除的 build 目錄。
    val testRunDirectory = layout.buildDirectory.dir("test-runs/$name")
    workingDir(testRunDirectory)
    doFirst {
        testRunDirectory.get().asFile.mkdirs()
    }
    // 本體的語系檔在 classpath 上與這個模組的路徑相同，語系檔測試改以來源目錄讀取這個模組自己的檔案。
    val langDirectory = layout.projectDirectory.dir("src/main/resources/assets/mahjongcraft/lang")
    inputs.dir(langDirectory).withPropertyName("debugLangDirectory")
    systemProperty("mahjongcraft.debug.langDirectory", langDirectory.asFile.absolutePath)
}

dependencies {
    minecraft(libs.minecraft1201)
    mappings("net.fabricmc:yarn:${libs.versions.yarnMappings1201.get()}:v2")
    modImplementation(libs.fabric.loader)
    modImplementation(libs.fabricApi1201)
    modImplementation(libs.fabric.language.kotlin)

    // Loom 子專案使用未重新映射的 namedElements，避免在開發環境讀取 intermediary 名稱。
    implementation(project(path = ":minecraft_v1.20.1_fabric", configuration = "namedElements"))
    // 執行與測試時沿用本體完整的依賴清單。
    runtimeOnly(project(path = ":minecraft_v1.20.1_fabric", configuration = "namedRuntimeElements"))
    implementation(project(":minecraft_common"))
    implementation(project(":minecraft_v1.20.1_common"))
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
    implementation(libs.exposed.core)
    implementation(libs.exposed.jdbc)

    testImplementation(kotlin("test"))
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.kotlinx.serialization.json)
    testImplementation(project(":testing:testing-mahjong-logic"))
    testImplementation(project(":testing:testing-mahjong-flow"))
}
