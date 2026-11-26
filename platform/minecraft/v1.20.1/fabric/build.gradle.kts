import com.doublemoon1119.mahjongcraft.buildlogic.AUTOTEST_SERVER_RESUME
import com.doublemoon1119.mahjongcraft.buildlogic.AUTOTEST_SERVER_RUN_DIRECTORY
import com.doublemoon1119.mahjongcraft.buildlogic.AUTOTEST_SERVER_START
import com.doublemoon1119.mahjongcraft.buildlogic.MinecraftModPackagingExtension
import com.doublemoon1119.mahjongcraft.buildlogic.SERVER_RUN_DIRECTORY
import com.doublemoon1119.mahjongcraft.buildlogic.autotestClientArguments
import com.doublemoon1119.mahjongcraft.buildlogic.autotestClientEnvironment
import com.doublemoon1119.mahjongcraft.buildlogic.autotestServerSystemProperties
import com.doublemoon1119.mahjongcraft.buildlogic.autotestSystemProperties
import com.doublemoon1119.mahjongcraft.buildlogic.clientRunDirectory
import com.doublemoon1119.mahjongcraft.buildlogic.includeResourcesOf
import com.doublemoon1119.mahjongcraft.buildlogic.includeSharedVersionSources
import com.doublemoon1119.mahjongcraft.buildlogic.includeSourcesOf
import com.doublemoon1119.mahjongcraft.buildlogic.modrinth
import com.doublemoon1119.mahjongcraft.buildlogic.registerAutotestClientTasks
import com.doublemoon1119.mahjongcraft.buildlogic.registerAutotestServerTasks

plugins {
    alias(libs.plugins.mahjongcraft.minecraft.loader)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.fabric.loom)
    alias(libs.plugins.koin.compiler)
}

configure<MinecraftModPackagingExtension> {
    dependencyRequirements.put("minecraft", mc1201.versions.require.fabric.minecraft)
    dependencyRequirements.put("fabricLoader", mc1201.versions.require.fabric.loader)
    dependencyRequirements.put("fabricApi", mc1201.versions.require.fabric.api)
    dependencyRequirements.put("fabricLanguageKotlin", mc1201.versions.require.fabric.language.kotlin)
}

// 提供未重新映射的本體與完整執行依賴，供同版本的其他開發用 mod 啟動。
configurations.create("namedRuntimeElements") {
    isCanBeResolved = false
    isCanBeConsumed = true
    extendsFrom(configurations.implementation.get(), configurations.runtimeOnly.get())
    outgoing.artifact(tasks.named("jar"))
}

repositories {
    // ModMenu 與相容測試模組只發布在 Modrinth 的 Maven 倉庫。
    modrinth()
}

// 開發執行時可選載入的相容測試模組，以 -PmahjongcraftDevMods 選擇；不打包，也不成為模組的依賴。
devMods {
    group("shaders", mc1201.bundles.dev.fabric.iris)
    group("modmenu", mc1201.bundles.dev.fabric.modmenu)
    shaderPack(group = "shaders", pack = mc1201.dev.shader.complementary.reimagined)
}

loom {
    accessWidenerPath = file("src/main/resources/mahjongcraft.accesswidener")

    runs {
        named("client") {
            // 分離 client 與 server 的 log、設定及世界資料，避免同時測試時互相覆寫。
            runDirectory.set(layout.projectDirectory.dir(clientRunDirectory()))
            // -PmahjongcraftAutotest 時，debug 模組在遊戲啟動後自動執行測試並關閉遊戲。
            autotestClientEnvironment().forEach { (name, value) -> environmentVariable(name, value) }
            autotestClientArguments().forEach(::programArg)
            autotestSystemProperties().forEach { (name, value) -> vmArg("-D$name=$value") }
        }
        named("server") {
            runDirectory.set(layout.projectDirectory.dir(SERVER_RUN_DIRECTORY))
        }
        // 伺服器自動測試的兩段都有專用的執行設定與執行目錄，只由 autotestServer 任務依序執行。
        // 第一段：開局、打到一半後關機。
        register("autotestServerStart") {
            server()
            ideConfigGenerated(false)
            runDirectory.set(layout.projectDirectory.dir(AUTOTEST_SERVER_RUN_DIRECTORY))
            autotestServerSystemProperties(AUTOTEST_SERVER_START).forEach { (name, value) -> vmArg("-D$name=$value") }
        }
        // 第二段：讀回第一段留下的世界。
        register("autotestServerResume") {
            server()
            ideConfigGenerated(false)
            runDirectory.set(layout.projectDirectory.dir(AUTOTEST_SERVER_RUN_DIRECTORY))
            autotestServerSystemProperties(AUTOTEST_SERVER_RESUME).forEach { (name, value) -> vmArg("-D$name=$value") }
        }
    }
}
registerAutotestClientTasks(clientTask = "runClient")
registerAutotestServerTasks(startTask = "runAutotestServerStart", resumeTask = "runAutotestServerResume")

// 一起編譯跨版本共用的 platform/minecraft/fabric；這裡只放 1.20.1 的 Fabric 專屬部分，例如網路傳輸與 mod 的設定檔。
includeSharedVersionSources()

// 材質、模型與翻譯放在跨版本、跨載入器共用的 :minecraft_common，與本模組的資源一起打包。
// :minecraft_v1.20.1_mod 與它一起編譯的跨版本共用 platform/minecraft/mod，程式與資源（例如配方）由本模組一起編譯與打包：
// mod 模組自己的 JAR 使用 Mojang 名稱，Fabric 執行時需要 intermediary 名稱，因此不直接依賴它的 JAR，而是把原始碼納入
// 本模組，由 Loom 一起重新映射。
includeResourcesOf(":minecraft_common", "src/jvmMain")
includeSourcesOf(":minecraft_v1.20.1_mod")

// mod 模組的 access transformer 給 Forge 與 mod 模組自己編譯用；Fabric 讀自己的 access widener。
tasks.processResources {
    exclude("META-INF/accesstransformer.cfg")
}

dependencies {
    minecraft(mc1201.minecraft)
    mappings(loom.officialMojangMappings())
    modImplementation(mc1201.fabric.loader)
    modImplementation(mc1201.fabric.api)
    modImplementation(mc1201.fabric.language.kotlin)
    // ModMenu 整合只對 ModMenu 的 API 編譯；玩家有安裝 ModMenu 時才會載入整合的進入點。
    modCompileOnly(mc1201.mod.menu)
    // 光影的陰影判斷只對 Iris 的 API 編譯；沒有安裝 Iris 時不會載入 Iris 的類別。
    modCompileOnly(mc1201.iris)
    // 程式不使用 Sodium。Iris 有改動 Sodium 類別的 mixin，Loom 重新映射 Iris 時要看得到 Sodium 才能改寫它們的目標；
    // 重新映射的結果在編譯與開發執行之間共用，編譯設定少了 Sodium 會讓開發執行時的 Iris 找不到目標而啟動失敗。
    modCompileOnly(mc1201.sodium)
    // 開發專用的 debug 模組只在開發執行時載入；不進入測試、不打包。它自己依賴本體，所以不帶入它的依賴。
    localRuntime(project(path = ":minecraft_v1.20.1_fabric-debug", configuration = "namedElements")) {
        isTransitive = false
    }

    minecraftModLibraries.addTo("implementation")
    // 這些不是 Fabric mod，玩家的遊戲中不會有它們，因此以 Loom 的 include 附帶在 mod JAR 中；Fabric Language Kotlin
    // 已提供的函式庫不附帶，同一個類別才不會在遊戲中出現兩份。
    minecraftModLibraries.addTo("include", excludeRuntimeProvided = true)

    testImplementation(kotlin("test"))
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(project(":mahjong-ai"))
    testImplementation(project(":testing:testing-mahjong-logic"))
    testImplementation(project(":testing:testing-mahjong-flow"))
}
