plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.fabric.loom)
    alias(libs.plugins.koin.compiler)
}

loom {
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
sourceSets {
    main {
        resources {
            srcDir(project(":minecraft_common").projectDir.resolve("src/jvmMain/resources"))
        }
    }
}

// fabric-language-kotlin 1.13.12+kotlin.2.4.0 已經以 nested jar 形式 bundle 了
// kotlinx-coroutines-core/kotlinx-serialization-json（版本剛好對到 gradle/libs.versions.toml
// 裡固定的版本），這裡拉進來的專案模組如果transitively 帶到同一個 artifact，include() 打包時
// 要排除掉，避免同一個 class 在最終 mod jar 裡出現兩份。
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
    mappings("net.fabricmc:yarn:${libs.versions.yarnMappings1201.get()}:v2")
    modImplementation(libs.fabric.loader)
    modImplementation(libs.fabricApi1201)
    modImplementation(libs.fabric.language.kotlin)

    implementation(project(":minecraft_common"))
    implementation(project(":minecraft_v1.20.1_common"))
    // :mahjong-flow-server 自己對 :mahjong-logic/:mahjong-flow-common 都只用 implementation（不是
    // api），不會透過它把這兩個模組的公開型別（GameAction、MahjongRuleConfig、GameEventPublisher…）
    // 透傳給下游，這裡要實作對應介面/使用對應型別，必須直接宣告依賴才看得到 symbol。
    implementation(project(":mahjong-logic"))
    implementation(project(":mahjong-flow:mahjong-flow-common"))
    implementation(project(":mahjong-flow:mahjong-flow-client"))
    implementation(project(":mahjong-flow:mahjong-flow-server"))
    implementation(project(":mahjong-flow:mahjong-flow-network-dto"))
    implementation(project(":mahjong-flow:mahjong-flow-persistence-dto"))
    implementation(project(":mahjong-extension-api"))
    implementation(project.dependencies.platform(libs.koin.bom))
    implementation(libs.koin.core)
    implementation(libs.koin.annotations)

    // 這幾個都是一般 JVM 專案，不是 Fabric mod，玩家的 classpath 上不會自動有它們，
    // 必須透過 Loom 的 include() 把它們打進最終 mod jar
    include(project(":minecraft_common"))
    include(libs.ktoml.core)
    include(libs.kotlinx.datetime)
    include(project(":minecraft_v1.20.1_common"))
    include(project(":mahjong-logic"))
    include(project(":mahjong-ai"))
    include(project(":mahjong-flow:mahjong-flow-common"), excludeFlkBundledKotlinx)
    include(project(":mahjong-flow:mahjong-flow-client"), excludeFlkBundledKotlinx)
    include(project(":mahjong-flow:mahjong-flow-server"), excludeFlkBundledKotlinx)
    include(project(":mahjong-flow:mahjong-flow-network-dto"), excludeFlkBundledKotlinx)
    include(project(":mahjong-flow:mahjong-flow-persistence-dto"), excludeFlkBundledKotlinx)
    include(project(":mahjong-extension-api"), excludeFlkBundledKotlinx)
    include(project.dependencies.platform(libs.koin.bom))
    include(libs.koin.core)

    testImplementation(kotlin("test"))
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(project(":testing:testing-mahjong-logic"))
    testImplementation(project(":testing:testing-mahjong-flow"))
}
