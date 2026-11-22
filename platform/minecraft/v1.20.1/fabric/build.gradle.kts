import com.doublemoon1119.mahjongcraft.buildlogic.MinecraftModPackagingExtension

plugins {
    alias(libs.plugins.mahjongcraft.minecraft.loader)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.fabric.loom)
    alias(libs.plugins.koin.compiler)
    alias(libs.plugins.mahjongcraft.public.api.abi)
}

publicApiAbi {
    includedNames.addAll(
        "com.doublemoon1119.mahjongcraft.platform.fabric.api.event.**",
        "com.doublemoon1119.mahjongcraft.platform.fabric.api.character.**",
        "com.doublemoon1119.mahjongcraft.platform.fabric.api.seating.**",
        "com.doublemoon1119.mahjongcraft.platform.fabric.api.registration.**",
        "com.doublemoon1119.mahjongcraft.platform.fabric.api.table.**",
        "com.doublemoon1119.mahjongcraft.platform.fabric.api.tableprop.**",
        "com.doublemoon1119.mahjongcraft.platform.fabric.extension.NativeMahjongExtension",
        "com.doublemoon1119.mahjongcraft.platform.fabric.extension.NativeMahjongClientExtension",
    )
}

version = libs.versions.minecraft.mod.version.get()

configure<MinecraftModPackagingExtension> {
    dependencyRequirements.put("minecraft", libs.versions.minecraft1201)
    dependencyRequirements.put("fabricLoader", libs.versions.fabricLoaderRequirement1201)
    dependencyRequirements.put("fabricApi", libs.versions.fabricApiRequirement1201)
    dependencyRequirements.put("fabricLanguageKotlin", libs.versions.fabricLanguageKotlinRequirement1201)
}

// 提供未重新映射的本體與完整執行依賴，供同版本的其他開發用 mod 啟動。
configurations.create("namedRuntimeElements") {
    isCanBeResolved = false
    isCanBeConsumed = true
    extendsFrom(configurations.implementation.get(), configurations.runtimeOnly.get())
    outgoing.artifact(tasks.named("jar"))
}

repositories {
    // ModMenu 與相容測試模組只發布在 Modrinth 的 Maven 倉庫；這個倉庫只提供 Modrinth 的套件。
    exclusiveContent {
        forRepository { maven("https://api.modrinth.com/maven") { name = "Modrinth" } }
        filter { includeGroup("maven.modrinth") }
    }
}

// 開發執行時可選載入的相容測試模組，以 -PmahjongcraftDevMods 選擇；不打包，也不成為模組的依賴。
devMods {
    group(
        "iris",
        libs.sodium1201,
        libs.iris1201,
        libs.irisJcpp1201,
        libs.irisGlslTransformer1201,
        libs.irisAntlrRuntime1201,
    )
    group("modmenu", libs.modMenu1201)
}

loom {
    accessWidenerPath = file("src/main/resources/mahjongcraft.accesswidener")

    runs {
        named("client") {
            // 分離 client 與 server 的 log、設定及世界資料，避免同時測試時互相覆寫。
            runDirectory.set(layout.projectDirectory.dir("run/client"))
        }
        named("server") {
            runDirectory.set(layout.projectDirectory.dir("run/server"))
        }
    }
}

tasks.withType<Test>().configureEach {
    // 將測試期間由 Minecraft logging 建立的相對檔案限制在可由 clean 移除的 build 目錄。
    val testRunDirectory = layout.buildDirectory.dir("test-runs/$name")
    workingDir(testRunDirectory)
    doFirst {
        testRunDirectory.get().asFile.mkdirs()
    }
}

// assets/mahjongcraft 底下的貼圖/模型/語言檔實體上放在 :minecraft_common（跨版本、跨 loader 共用，
// 避免每個版本/loader 模組各自留一份重複的素材），這裡只是把那個目錄多接一條 srcDir 進本模組的
// resources，讓既有的 processResources/打包流程照樣把它們收進最終的 mod jar，不需要額外的複製 task。
//
// :minecraft_v1.20.1_mod 的程式與資源（例如配方）由本模組一起編譯與打包：mod 模組自己的 JAR 使用 Mojang 名稱，
// Fabric 執行時需要 intermediary 名稱，因此不直接依賴它的 JAR，而是把原始碼納入本模組，由 Loom 一起重新映射。
val minecraftModDirectory = project(":minecraft_v1.20.1_mod").projectDir
sourceSets {
    main {
        java.srcDir(minecraftModDirectory.resolve("src/main/java"))
        resources {
            srcDir(project(":minecraft_common").projectDir.resolve("src/jvmMain/resources"))
            srcDir(minecraftModDirectory.resolve("src/main/resources"))
        }
    }
}
kotlin.sourceSets.main {
    kotlin.srcDir(minecraftModDirectory.resolve("src/main/kotlin"))
}
// mod 模組的 access transformer 給 Forge 與 mod 模組自己編譯用；Fabric 讀自己的 access widener。
tasks.processResources {
    exclude("META-INF/accesstransformer.cfg")
}

// fabric-language-kotlin 以 nested jar 形式內附 kotlinx-coroutines-core／kotlinx-serialization-json，
// 版本與 gradle/libs.versions.toml 固定的版本一致。這裡拉進來的專案模組若傳遞帶到同一個 artifact，
// include() 打包時要排除，避免同一個 class 在最終 mod jar 裡出現兩份。
val excludeFlkBundledKotlinx: ModuleDependency.() -> Unit = {
    exclude(group = "org.jetbrains.kotlinx", module = "kotlinx-coroutines-core")
    exclude(group = "org.jetbrains.kotlinx", module = "kotlinx-coroutines-core-jvm")
    exclude(group = "org.jetbrains.kotlinx", module = "kotlinx-serialization-core")
    exclude(group = "org.jetbrains.kotlinx", module = "kotlinx-serialization-core-jvm")
    exclude(group = "org.jetbrains.kotlinx", module = "kotlinx-serialization-json")
    exclude(group = "org.jetbrains.kotlinx", module = "kotlinx-serialization-json-jvm")
}

dependencies {
    minecraft(libs.minecraft1201)
    mappings(loom.officialMojangMappings())
    modImplementation(libs.fabric.loader)
    modImplementation(libs.fabricApi1201)
    modImplementation(libs.fabric.language.kotlin)
    // ModMenu 整合只對 ModMenu 的 API 編譯；玩家有安裝 ModMenu 時才會載入整合的進入點。
    modCompileOnly(libs.modMenu1201)
    // 光影的陰影判斷只對 Iris 的 API 編譯；沒有安裝 Iris 時不會載入 Iris 的類別。
    modCompileOnly(libs.iris1201)
    // 程式不使用 Sodium。Iris 有改動 Sodium 類別的 mixin，Loom 重新映射 Iris 時要看得到 Sodium 才能改寫它們的目標；
    // 重新映射的結果在編譯與開發執行之間共用，編譯設定少了 Sodium 會讓開發執行時的 Iris 找不到目標而啟動失敗。
    modCompileOnly(libs.sodium1201)
    // 開發專用的 debug 模組只在開發執行時載入；不進入測試、不打包。它自己依賴本體，所以不帶入它的依賴。
    localRuntime(project(path = ":minecraft_v1.20.1_fabric-debug", configuration = "namedElements")) {
        isTransitive = false
    }

    implementation(project(":minecraft_common"))
    // :mahjong-flow-server 自己對 :mahjong-logic/:mahjong-flow-common 都只用 implementation（不是
    // api），不會透過它把這兩個模組的公開型別（GameAction、MahjongRuleConfig、GameEventPublisher…）
    // 透傳給下游，這裡要實作對應介面/使用對應型別，必須直接宣告依賴才看得到 symbol。
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
    // Client 設定檔仍由 MahjongClientConfigStore 直接使用 ktoml；:minecraft_common 的
    // implementation 不會把 Toml symbol 透傳給 loader module，因此這裡保留直接依賴。
    implementation(libs.ktoml.core)
    implementation(libs.exposed.core)
    implementation(libs.exposed.jdbc)
    implementation(libs.sqlite.jdbc)

    // 這幾個都是一般 JVM 專案，不是 Fabric mod，玩家的 classpath 上不會自動有它們，
    // 必須透過 Loom 的 include() 把它們打進最終 mod jar
    include(project(":minecraft_common"))
    include(libs.ktoml.core)
    include(libs.exposed.core)
    include(libs.exposed.jdbc)
    include(libs.sqlite.jdbc)
    include(libs.kotlinx.datetime)
    include(project(":mahjong-logic"))
    include(project(":mahjong-ai"))
    include(project(":mahjong-flow:mahjong-flow-common"), excludeFlkBundledKotlinx)
    include(project(":mahjong-flow:mahjong-flow-client"), excludeFlkBundledKotlinx)
    include(project(":mahjong-flow:mahjong-flow-server"), excludeFlkBundledKotlinx)
    include(project(":mahjong-flow:mahjong-flow-network-dto"), excludeFlkBundledKotlinx)
    include(project(":mahjong-flow:mahjong-flow-persistence-format"), excludeFlkBundledKotlinx)
    include(project(":mahjong-extension-api"), excludeFlkBundledKotlinx)
    include(project(":mahjong-bundled-extensions"), excludeFlkBundledKotlinx)
    include(project.dependencies.platform(libs.koin.bom))
    include(libs.koin.core)

    testImplementation(kotlin("test"))
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(project(":mahjong-ai"))
    testImplementation(project(":testing:testing-mahjong-logic"))
    testImplementation(project(":testing:testing-mahjong-flow"))
}
