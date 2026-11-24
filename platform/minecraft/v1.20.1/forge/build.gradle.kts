import com.doublemoon1119.mahjongcraft.buildlogic.AUTOTEST_SERVER_RESUME
import com.doublemoon1119.mahjongcraft.buildlogic.AUTOTEST_SERVER_START
import com.doublemoon1119.mahjongcraft.buildlogic.MinecraftModLibraryKind
import com.doublemoon1119.mahjongcraft.buildlogic.MinecraftModPackagingExtension
import com.doublemoon1119.mahjongcraft.buildlogic.StripModuleInfo
import com.doublemoon1119.mahjongcraft.buildlogic.UnpackLibraryJars
import com.doublemoon1119.mahjongcraft.buildlogic.VerifyEmbeddedLibrariesTask
import com.doublemoon1119.mahjongcraft.buildlogic.VerifyNoSplitPackagesTask
import com.doublemoon1119.mahjongcraft.buildlogic.autotestClientArguments
import com.doublemoon1119.mahjongcraft.buildlogic.autotestClientEnvironment
import com.doublemoon1119.mahjongcraft.buildlogic.autotestServerSystemProperties
import com.doublemoon1119.mahjongcraft.buildlogic.autotestSystemProperties
import com.doublemoon1119.mahjongcraft.buildlogic.clientRunDirectory
import com.doublemoon1119.mahjongcraft.buildlogic.hideForgeDeprecationsOfVanillaApis
import com.doublemoon1119.mahjongcraft.buildlogic.includeResourcesOf
import com.doublemoon1119.mahjongcraft.buildlogic.includeSourcesOf
import com.doublemoon1119.mahjongcraft.buildlogic.kotlinForForge
import com.doublemoon1119.mahjongcraft.buildlogic.minecraftModAccessTransformer
import com.doublemoon1119.mahjongcraft.buildlogic.modrinth
import com.doublemoon1119.mahjongcraft.buildlogic.registerAutotestClientTasks
import com.doublemoon1119.mahjongcraft.buildlogic.registerAutotestServerTasks
import com.doublemoon1119.mahjongcraft.buildlogic.serverRunDirectory
import net.neoforged.moddevgradle.tasks.JarJar
import org.gradle.api.artifacts.component.ModuleComponentIdentifier

plugins {
    alias(libs.plugins.mahjongcraft.minecraft.loader)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.moddev.legacyforge)
    alias(libs.plugins.koin.compiler)
}

/** Forge 專用 mixin 的設定檔名稱。 */
val forgeMixinConfig = "mahjongcraft.forge.mixins.json"

/** 與 Fabric 共用、放在 :minecraft_v1.20.1_mod 的 mixin 的設定檔名稱。 */
val sharedMixinConfig = "mahjongcraft.mixins.json"

configure<MinecraftModPackagingExtension> {
    dependencyRequirements.put("minecraft", mc1201.versions.require.forge.minecraft)
    dependencyRequirements.put("forge", mc1201.versions.require.forge.loader)
    dependencyRequirements.put("kotlinForForge", mc1201.versions.require.forge.kotlinforforge)
}

repositories {
    // Kotlin for Forge 只發布在作者自己的 Maven 倉庫。
    kotlinForForge()
    // Oculus 與 Embeddium 只發布在 Modrinth 的 Maven 倉庫。
    modrinth()
}

// 開發執行時可選載入的相容測試模組，以 -PmahjongcraftDevMods 選擇；不打包，也不成為模組的依賴。
devMods {
    // Oculus 與它需要的 Embeddium，測試光影。
    group("shaders", mc1201.bundles.dev.forge.oculus)
    // Forge 內建的模組清單就有設定按鈕，不需要另外的模組；宣告這個組合，讓同時選擇 Fabric 時同一個開關也能使用。
    group("modmenu")
}

// 材質、模型與翻譯放在跨版本、跨載入器共用的 :minecraft_common，與本模組的資源一起打包。
// :minecraft_v1.20.1_mod 的程式與資源由本模組一起編譯與打包（同 Fabric），由 ModDevGradle 一起轉成 Forge 執行時的名稱。
includeResourcesOf(":minecraft_common", "src/jvmMain")
includeSourcesOf(":minecraft_v1.20.1_mod")

// 與 Fabric 共用的程式只能用原版寫法，不顯示 Forge 對原版寫法的過時警告。
hideForgeDeprecationsOfVanillaApis()

legacyForge {
    version = mc1201.versions.forge.get()
    // 與 :minecraft_v1.20.1_mod 共用同一份 access transformer；它也隨 mod 模組的資源一起打包，Forge 執行時讀同一份。
    accessTransformers.from(minecraftModAccessTransformer())
    validateAccessTransformers = true
    runs {
        create("client") {
            client()
            gameDirectory.set(layout.projectDirectory.dir(clientRunDirectory()))
            // -PmahjongcraftAutotest 時，debug 模組在遊戲啟動後自動執行測試並關閉遊戲。
            autotestSystemProperties().forEach { (name, value) -> systemProperty(name, value) }
            autotestClientEnvironment().forEach { (name, value) -> environment(name, value) }
            autotestClientArguments().forEach(::programArgument)
        }
        create("server") {
            server()
            gameDirectory.set(layout.projectDirectory.dir(serverRunDirectory()))
            // -PmahjongcraftAutotest 時是伺服器自動測試的第一段：開局、打到一半後關機。
            autotestServerSystemProperties(AUTOTEST_SERVER_START).forEach { (name, value) -> systemProperty(name, value) }
            programArgument("--nogui")
        }
        // 伺服器自動測試的第二段：讀回第一段留下的世界；只由 autotestServer 任務接在第一段之後執行。
        create("autotestServerResume") {
            server()
            disableIdeRun()
            gameDirectory.set(layout.projectDirectory.dir(serverRunDirectory()))
            autotestServerSystemProperties(AUTOTEST_SERVER_RESUME).forEach { (name, value) -> systemProperty(name, value) }
            programArgument("--nogui")
        }
    }
}
registerAutotestClientTasks(clientTask = "runClient")
registerAutotestServerTasks(startTask = "runServer", resumeTask = "runAutotestServerResume", portOffset = 1)

dependencies {
    modImplementation(mc1201.kotlinforforge)
    // ModDevGradle 不帶入 mod 依賴自己的依賴；Kotlin for Forge 給 mod 用的 API（例如本 mod 的事件匯流排）在 kfflib 中，
    // 執行時由上面的 Kotlin for Forge 本體提供。
    modCompileOnly(mc1201.kotlinforforge.lib)
    testImplementation(kotlin("test"))

    minecraftModLibraries.addTo("implementation")
    // 光影的陰影判斷只對 Oculus 的 API 編譯；API 不含 Minecraft 的類別，不需要轉換名稱。
    compileOnly(mc1201.oculus)
    // 產生 Forge 執行時名稱用的 mixin refmap。
    annotationProcessor(variantOf(mc1201.mixin) { classifier("processor") })
    // 共用的 mixin 使用 MixinExtras；Forge 1.20.1 沒有附帶，開發執行由這個依賴提供，正式 JAR 以 jar-in-jar 附帶。
    compileOnly(mc1201.mixinextras.common)
    annotationProcessor(mc1201.mixinextras.common)
    implementation(mc1201.mixinextras.forge)
}

// 正式 JAR 以 jar-in-jar 附帶函式庫；Kotlin for Forge 已提供的函式庫不附帶。
dependencies {
    minecraftModLibraries.addTo("jarJar", kinds = setOf(MinecraftModLibraryKind.PROJECTS), excludeRuntimeProvided = true)
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
    minecraftModLibraries.addTo(jarJarExternalLibraries.name, kinds = setOf(MinecraftModLibraryKind.EXTERNAL), excludeRuntimeProvided = true)
    jarJarExternalLibraries(mc1201.mixinextras.forge)
}
tasks.named<JarJar>("jarJar") {
    configuration(jarJarExternalLibraries)
}

mixin {
    add(sourceSets.main.get(), "mahjongcraft.refmap.json")
    config(forgeMixinConfig)
    config(sharedMixinConfig)
    // debug 模組的自動測試 mixin；只有開發執行會載入 debug 模組，正式 JAR 的 MixinConfigs 不含它。
    config("mahjongcraft.debug.mixins.json")
}

tasks.jar {
    // Koin 編譯外掛產生的 hints 只在編譯期使用；minecraft_common 的 JAR 也有這個套件，Forge 不允許兩個 JAR 有同名套件。
    exclude("org/koin/plugin/hints/**")
    // ModDevGradle 不會寫入這個屬性；正式環境的 Forge 只從這裡找到 mixin 設定。
    manifest.attributes("MixinConfigs" to listOf(forgeMixinConfig, sharedMixinConfig).joinToString(","))
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
    // Koin 的多平台依賴資料列出 stately，但 Koin 的 JVM 類別沒有使用它，執行時不需要。
    exclude(group = "co.touchlab")
}
dependencies {
    minecraftModLibraries.addTo(devLibraries.name, excludeRuntimeProvided = true)
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
// 開發專用的 debug 工具只在開發執行時以它自己的輸出載入，不進入測試也不打包；本體的程式從不參照 debug 類別。
evaluationDependsOn(":minecraft_v1.20.1_forge-debug")
val debugSourceSets = project(":minecraft_v1.20.1_forge-debug").extensions.getByType<SourceSetContainer>()
legacyForge {
    mods {
        create("mahjongcraft") {
            sourceSet(sourceSets.main.get())
            sourceSet(devLibrariesSourceSet)
        }
        // debug 模組整理過的輸出，不含與本體同名的套件。
        create("mahjongcraft_debug") {
            sourceSet(debugSourceSets.getByName("devMod"))
        }
    }
    // 單元測試只測不需要啟動遊戲的程式；測試也要對 Minecraft 與 Forge 的類別編譯。
    addModdingDependenciesTo(sourceSets.test.get())
}

// 開發執行解析出的函式庫就是執行時需要的全部函式庫；正式 JAR 必須以相同版本附帶每一個。
val verifyEmbeddedLibraries = tasks.register<VerifyEmbeddedLibrariesTask>("verifyEmbeddedLibraries") {
    group = "verification"
    description = "Verifies that the mod JAR embeds every external library it needs at run time."
    modJar.fileProvider(tasks.named("reobfJar").map { it.outputs.files.singleFile })
    requiredLibraries.set(
        devLibraries.incoming.artifacts.resolvedArtifacts.map { artifacts ->
            artifacts.mapNotNull { (it.id.componentIdentifier as? ModuleComponentIdentifier)?.let { id -> "${id.group}:${id.module}:${id.version}" } }.toSet()
        },
    )
}
tasks.check {
    dependsOn(verifyEmbeddedLibraries)
}
