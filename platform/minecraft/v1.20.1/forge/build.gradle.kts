import com.doublemoon1119.mahjongcraft.buildlogic.MinecraftModPackagingExtension
import com.doublemoon1119.mahjongcraft.buildlogic.StripModuleInfo
import com.doublemoon1119.mahjongcraft.buildlogic.UnpackLibraryJars
import com.doublemoon1119.mahjongcraft.buildlogic.VerifyNoSplitPackagesTask
import net.neoforged.moddevgradle.tasks.JarJar

plugins {
    alias(libs.plugins.mahjongcraft.minecraft.loader)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.moddev.legacyforge)
    alias(libs.plugins.koin.compiler)
}

version = libs.versions.minecraft.mod.version.get()

configure<MinecraftModPackagingExtension> {
    // 載入器接點尚未全部實作，不交付給玩家。
    distributable.set(false)
    dependencyRequirements.put("minecraft", libs.versions.minecraftRequirement1201)
    dependencyRequirements.put("forge", libs.versions.forgeRequirement1201)
    dependencyRequirements.put("kotlinForForge", libs.versions.kotlinForForgeRequirement1201)
}

repositories {
    // Kotlin for Forge 只發布在作者自己的 Maven 倉庫；這個倉庫只提供 Kotlin for Forge 的套件。
    exclusiveContent {
        forRepository { maven("https://thedarkcolour.github.io/KotlinForForge/") { name = "KotlinForForge" } }
        filter { includeGroup("thedarkcolour") }
    }
}

// :minecraft_v1.20.1_mod 的程式與資源由本模組一起編譯與打包（同 Fabric），由 ModDevGradle 一起轉成 Forge 執行時的名稱。
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

legacyForge {
    version = libs.versions.forge1201.get()
    // 與 :minecraft_v1.20.1_mod 共用同一份 access transformer；它也隨 mod 模組的資源一起打包，Forge 執行時讀同一份。
    accessTransformers.from(minecraftModDirectory.resolve("src/main/resources/META-INF/accesstransformer.cfg"))
    validateAccessTransformers = true
    runs {
        create("client") {
            client()
            gameDirectory.set(layout.projectDirectory.dir("run/client"))
        }
        create("server") {
            server()
            gameDirectory.set(layout.projectDirectory.dir("run/server"))
            programArgument("--nogui")
        }
    }
}

// 本專案的模組與函式庫：編譯時直接依賴，正式 JAR 以 jar-in-jar 附帶，開發執行時解開放進 mod。
val projectLibraries = listOf(
    ":minecraft_common",
    ":mahjong-logic",
    ":mahjong-ai",
    ":mahjong-flow:mahjong-flow-common",
    ":mahjong-flow:mahjong-flow-client",
    ":mahjong-flow:mahjong-flow-server",
    ":mahjong-flow:mahjong-flow-network-dto",
    ":mahjong-flow:mahjong-flow-persistence-format",
    ":mahjong-extension-api",
    ":mahjong-bundled-extensions",
)

// Kotlin multiplatform 函式庫寫出 -jvm 座標：以通用座標宣告時，jar-in-jar 解析到的 -jvm 變體會失去 Maven 座標。
// jar-in-jar 不套用 BOM，Koin 直接寫出 BOM 的版本。
val externalLibraries = listOf(
    "io.insert-koin:koin-core-jvm:${libs.versions.koin.bom.get()}",
    "com.akuleshov7:ktoml-core-jvm:${libs.versions.ktoml.get()}",
    "org.jetbrains.kotlinx:kotlinx-datetime-jvm:${libs.versions.kotlinx.datetime.get()}",
    "${libs.exposed.core.get()}",
    "${libs.exposed.jdbc.get()}",
    "${libs.sqlite.jdbc.get()}",
)

dependencies {
    modImplementation(libs.kotlinforforge)

    projectLibraries.forEach { implementation(project(it)) }
    implementation(project.dependencies.platform(libs.koin.bom))
    implementation(libs.koin.core)
    implementation(libs.ktoml.core)
    implementation(libs.exposed.core)
    implementation(libs.exposed.jdbc)
    implementation(libs.sqlite.jdbc)

    projectLibraries.forEach { jarJar(project(it)) { excludeKotlinProvidedByForge() } }
}

// 附帶的外部函式庫去掉 Java 模組設定：例如 kotlinx-datetime 的設定要求名為 kotlin.stdlib 的模組，Kotlin for Forge 並非以這個
// 名稱提供 Kotlin，Forge 會拒絕啟動。Maven 座標不變，jar-in-jar 仍能與其他 mod 協調版本。
// 本專案的模組沒有模組設定，留在 jarJar 不經轉換：經過轉換的專案模組會失去對其 JAR 建置工作的依賴。
val artifactType = Attribute.of("artifactType", String::class.java)
dependencies {
    attributesSchema { attribute(StripModuleInfo.MODULE_INFO_STRIPPED) }
    artifactTypes.getByName("jar").attributes.attribute(StripModuleInfo.MODULE_INFO_STRIPPED, false)
    registerTransform(StripModuleInfo::class) {
        from.attribute(StripModuleInfo.MODULE_INFO_STRIPPED, false).attribute(artifactType, "jar")
        to.attribute(StripModuleInfo.MODULE_INFO_STRIPPED, true).attribute(artifactType, "jar")
    }
}
val jarJarExternalLibraries = configurations.create("jarJarExternalLibraries") {
    // 與 jarJar 相同：只附帶直接宣告的函式庫。
    isTransitive = false
    isCanBeConsumed = false
    attributes.addAllLater(configurations.getByName("jarJar").attributes)
    attributes.attribute(StripModuleInfo.MODULE_INFO_STRIPPED, true)
}
dependencies {
    externalLibraries.forEach { jarJarExternalLibraries(it) { excludeKotlinProvidedByForge() } }
}
tasks.named<JarJar>("jarJar") {
    configuration(jarJarExternalLibraries)
}

tasks.jar {
    // Koin 編譯外掛產生的 hints 只在編譯期使用；minecraft_common 的 JAR 也有這個套件，Forge 不允許兩個 JAR 有同名套件。
    exclude("org/koin/plugin/hints/**")
}

val verifyNoSplitPackages = tasks.register<VerifyNoSplitPackagesTask>("verifyNoSplitPackages") {
    group = "verification"
    description = "Verifies that the mod JAR and its embedded JARs share no package."
    modJar.fileProvider(tasks.named("reobfJar").map { it.outputs.files.singleFile })
}
tasks.check {
    dependsOn(verifyNoSplitPackages)
}

// 開發執行時 Forge 只把 mod 的輸出載入到能看見 Kotlin for Forge 的模組層，放在啟動 classpath 的函式庫看不到 Kotlin；
// 因此把函式庫解開放進只供開發執行的 source set，與本體同屬一個 mod。正式 JAR 不含這些檔案。
val devLibraries = configurations.create("devLibraries") {
    isCanBeConsumed = false
}
dependencies {
    projectLibraries.forEach { devLibraries(project(it)) { excludeKotlinProvidedByForge() } }
    externalLibraries.forEach { devLibraries(it) { excludeKotlinProvidedByForge() } }
}
val unpackLibraryJars = objects.newInstance<UnpackLibraryJars>()
val unpackDevLibraries = tasks.register<Sync>("unpackDevLibraries") {
    from(devLibraries.elements.map(unpackLibraryJars)) {
        exclude("module-info.class", "META-INF/versions/*/module-info.class", "META-INF/MANIFEST.MF", "META-INF/*.SF", "META-INF/*.RSA")
    }
    duplicatesStrategy = DuplicatesStrategy.EXCLUDE
    into(layout.buildDirectory.dir("devLibraries"))
}
val devLibrariesSourceSet = sourceSets.create("devLibraries") {
    output.dir(mapOf("builtBy" to unpackDevLibraries), layout.buildDirectory.dir("devLibraries"))
}
legacyForge {
    mods {
        create("mahjongcraft") {
            sourceSet(sourceSets.main.get())
            sourceSet(devLibrariesSourceSet)
        }
    }
}

/** 排除 Kotlin for Forge 已附帶的 Kotlin 標準函式庫、kotlinx 協程與序列化，以及 Minecraft 已附帶的 SLF4J。 */
fun ModuleDependency.excludeKotlinProvidedByForge() {
    exclude(group = "org.slf4j")
    exclude(group = "org.jetbrains.kotlin")
    exclude(group = "org.jetbrains.kotlinx", module = "kotlinx-coroutines-core")
    exclude(group = "org.jetbrains.kotlinx", module = "kotlinx-coroutines-core-jvm")
    exclude(group = "org.jetbrains.kotlinx", module = "kotlinx-serialization-core")
    exclude(group = "org.jetbrains.kotlinx", module = "kotlinx-serialization-core-jvm")
    exclude(group = "org.jetbrains.kotlinx", module = "kotlinx-serialization-json")
    exclude(group = "org.jetbrains.kotlinx", module = "kotlinx-serialization-json-jvm")
}
