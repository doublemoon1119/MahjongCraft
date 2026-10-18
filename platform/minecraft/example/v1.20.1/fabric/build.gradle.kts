import com.doublemoon1119.mahjongcraft.buildlogic.MinecraftModPackagingExtension
import com.doublemoon1119.mahjongcraft.buildlogic.ModPackagingTestArguments
import com.doublemoon1119.mahjongcraft.buildlogic.UnpackLibraryJars
import net.fabricmc.loom.task.RemapJarTask

plugins {
    alias(libs.plugins.mahjongcraft.minecraft.loader)
    alias(libs.plugins.fabric.loom)
}

version = libs.versions.minecraft.mod.version.get()

configure<MinecraftModPackagingExtension> {
    modId.set("mahjongcraft_example")
    displayName.set("MahjongCraft Extension Example")
    description.set("An isolated example of extending MahjongCraft.")
    archiveBaseName.set("mahjongcraft-example-fabric-1.20.1")
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

loom {
    mods {
        create("mahjongcraft_example") {
            sourceSet(sourceSets.main.get())
            configuration(exampleLibraries)
        }
        create("mahjongcraft") {
            sourceSet("main", ":minecraft_v1.20.1_fabric")
        }
    }
    runs {
        named("client") {
            runDirectory.set(layout.projectDirectory.dir("run/client"))
        }
        named("server") {
            runDirectory.set(layout.projectDirectory.dir("run/server"))
        }
    }
}

dependencies {
    minecraft(libs.minecraft1201)
    mappings("net.fabricmc:yarn:${libs.versions.yarnMappings1201.get()}:v2")
    modImplementation(libs.fabric.loader)
    modImplementation(libs.fabricApi1201)
    modImplementation(libs.fabric.language.kotlin)

    // Loom 子專案使用未重新映射的 namedElements，避免在開發環境讀取 intermediary 名稱。
    implementation(project(path = ":minecraft_v1.20.1_fabric", configuration = "namedElements"))
    // 執行時沿用本體完整的依賴清單，不將它們合併進範例 JAR。
    runtimeOnly(project(path = ":minecraft_v1.20.1_fabric", configuration = "namedRuntimeElements"))
    implementation(project(":mahjong-extension-api"))
    implementation(project(":minecraft_common"))
    implementation(project(":example:example-mahjong-extension"))
    implementation(project(":minecraft_example_common"))
    exampleLibraries(project(":example:example-mahjong-extension"))
    exampleLibraries(project(":minecraft_example_common"))
    mainModArtifact(project(path = ":minecraft_v1.20.1_fabric", configuration = "runtimeElements"))

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

val unpackLibraryJars = objects.newInstance<UnpackLibraryJars>()
tasks.jar {
    dependsOn(exampleLibraries)
    // 將範例程式庫的內容合併進同一個 mod；不產生額外的 Fabric mod 或 nested JAR。
    from(exampleLibraries.elements.map(unpackLibraryJars)) {
        exclude("META-INF/MANIFEST.MF")
    }
}
