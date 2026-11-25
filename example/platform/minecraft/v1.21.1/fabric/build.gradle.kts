import com.doublemoon1119.mahjongcraft.buildlogic.MinecraftModPackagingExtension
import com.doublemoon1119.mahjongcraft.buildlogic.ModPackagingTestArguments
import com.doublemoon1119.mahjongcraft.buildlogic.UnpackLibraryJars
import com.doublemoon1119.mahjongcraft.buildlogic.autotestClientArguments
import com.doublemoon1119.mahjongcraft.buildlogic.autotestClientEnvironment
import com.doublemoon1119.mahjongcraft.buildlogic.autotestSystemProperties
import com.doublemoon1119.mahjongcraft.buildlogic.clientRunDirectory
import com.doublemoon1119.mahjongcraft.buildlogic.includeSharedVersionSources
import com.doublemoon1119.mahjongcraft.buildlogic.includeSourcesOf
import com.doublemoon1119.mahjongcraft.buildlogic.modrinth
import com.doublemoon1119.mahjongcraft.buildlogic.registerAutotestClientTasks
import net.fabricmc.loom.task.RemapJarTask

plugins {
    alias(libs.plugins.mahjongcraft.minecraft.loader)
    alias(libs.plugins.fabric.loom)
}

// 一起編譯跨版本共用的 example/platform/minecraft/fabric；這裡只放 1.21.1 的 mod 設定與打包測試要比對的版本內容。
includeSharedVersionSources()

configure<MinecraftModPackagingExtension> {
    modId.set("mahjongcraft_example")
    displayName.set("MahjongCraft Extension Example")
    description.set("An isolated example of extending MahjongCraft.")
    archiveBaseName.set("mahjongcraft-example-fabric-1.21.1")
    // 範例模組不是交付給玩家的成品，CI 不收集它的 JAR。
    distributable.set(false)
    dependencyRequirements.put("minecraft", mc1211.versions.require.fabric.minecraft)
    dependencyRequirements.put("fabricLoader", mc1211.versions.require.fabric.loader)
    dependencyRequirements.put("fabricApi", mc1211.versions.require.fabric.api)
    dependencyRequirements.put("fabricLanguageKotlin", mc1211.versions.require.fabric.language.kotlin)
}

// 只收集兩份範例程式庫，不將它們依賴的本體與 Kotlin 套件再打包一次。
val exampleLibraries = configurations.create("exampleLibraries") {
    isCanBeConsumed = false
    isTransitive = false
}

// 測試讀取正式發布的 JAR，不以另一個專案的設定先後順序查找 remapJar 任務。
val mainModArtifact = configurations.create("mainModArtifact") {
    isCanBeConsumed = false
    isTransitive = false
}

repositories {
    // 相容測試模組只發布在 Modrinth 的 Maven 倉庫。
    modrinth()
}

// 與本體相同的相容測試組合，讓範例的開發執行也能在同樣的環境中測試；本體的組合不會傳到這裡。
devMods {
    group("shaders", mc1211.bundles.dev.fabric.iris)
    group("modmenu", mc1211.bundles.dev.fabric.modmenu)
}

loom {
    mods {
        create("mahjongcraft_example") {
            sourceSet(sourceSets.main.get())
            configuration(exampleLibraries)
        }
        create("mahjongcraft") {
            sourceSet("main", ":minecraft_v1.21.1_fabric")
        }
    }
    runs {
        named("client") {
            runDirectory.set(layout.projectDirectory.dir(clientRunDirectory()))
            // -PmahjongcraftAutotest 時，debug 模組在遊戲啟動後自動執行測試並關閉遊戲，與本體的開發執行相同。
            autotestClientEnvironment().forEach { (name, value) -> environmentVariable(name, value) }
            autotestClientArguments().forEach(::programArg)
            autotestSystemProperties().forEach { (name, value) -> vmArg("-D$name=$value") }
        }
        named("server") {
            // 範例沒有伺服器自動測試，伺服器不帶自動測試開關，平常的開發世界不受影響。
            runDirectory.set(layout.projectDirectory.dir("run/server"))
        }
    }
}

dependencies {
    minecraft(mc1211.minecraft)
    mappings(loom.officialMojangMappings())
    modImplementation(mc1211.fabric.loader)
    modImplementation(mc1211.fabric.api)
    modImplementation(mc1211.fabric.language.kotlin)

    // Loom 子專案使用未重新映射的 namedElements，避免在開發環境讀取 intermediary 名稱。
    implementation(project(path = ":minecraft_v1.21.1_fabric", configuration = "namedElements"))
    // 執行時沿用本體完整的依賴清單，不將它們合併進範例 JAR。
    runtimeOnly(project(path = ":minecraft_v1.21.1_fabric", configuration = "namedRuntimeElements"))
    implementation(project(":mahjong-extension-api"))
    implementation(project(":minecraft_common"))
    implementation(project(":example:example-mahjong-extension"))
    implementation(project(":example-minecraft_common"))
    exampleLibraries(project(":example:example-mahjong-extension"))
    exampleLibraries(project(":example-minecraft_common"))
    mainModArtifact(project(path = ":minecraft_v1.21.1_fabric", configuration = "runtimeElements"))
    // 開發專用的 debug 模組只在開發執行時載入，讓範例也能使用 debug 指令與自動測試；不進入測試、不打包。
    localRuntime(project(path = ":minecraft_v1.21.1_fabric-debug", configuration = "namedElements")) {
        isTransitive = false
    }

    testImplementation(kotlin("test"))
    testImplementation(libs.kotlinx.serialization.json)
}

val exampleModJar = tasks.named<RemapJarTask>("remapJar")
val packagingTestArguments = objects.newInstance<ModPackagingTestArguments>().apply {
    mainJar.from(mainModArtifact)
    exampleJar.from(exampleModJar.flatMap { it.archiveFile })
}
tasks.test {
    // 對實際 remap 後的產物驗證，不以普通 JVM fixture 代替真正的 mod 打包。
    dependsOn(exampleModJar, mainModArtifact)
    jvmArgumentProviders.add(packagingTestArguments)
}

// 範例中呼叫 Minecraft 但不用載入器的程式在 :example-minecraft_v1.21.1_mod 與它一起編譯的跨版本共用
// example/platform/minecraft/mod；它們的 JAR 使用 Mojang 名稱，因此把原始碼納入本模組，由 Loom 一起重新映射並打包。
includeSourcesOf(":example-minecraft_v1.21.1_mod")

val unpackLibraryJars = objects.newInstance<UnpackLibraryJars>()
tasks.jar {
    dependsOn(exampleLibraries)
    // 將範例程式庫的內容合併進同一個 mod；不產生額外的 Fabric mod 或 nested JAR。
    from(exampleLibraries.elements.map(unpackLibraryJars)) {
        exclude("META-INF/MANIFEST.MF")
    }
}

// 總任務 autotest 也執行範例的用戶端自動測試。
registerAutotestClientTasks(clientTask = "runClient")
