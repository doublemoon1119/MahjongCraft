import com.doublemoon1119.mahjongcraft.buildlogic.MinecraftModPackagingExtension
import com.doublemoon1119.mahjongcraft.buildlogic.ModPackagingTestArguments
import com.doublemoon1119.mahjongcraft.buildlogic.UnpackLibraryJars
import com.doublemoon1119.mahjongcraft.buildlogic.hideForgeDeprecationsOfVanillaApis
import com.doublemoon1119.mahjongcraft.buildlogic.includeSourcesOf
import com.doublemoon1119.mahjongcraft.buildlogic.kotlinForForge
import com.doublemoon1119.mahjongcraft.buildlogic.minecraftModAccessTransformer

plugins {
    alias(libs.plugins.mahjongcraft.minecraft.loader)
    alias(libs.plugins.moddev.legacyforge)
}

version = libs.versions.minecraft.mod.version.get()

// 開發執行要載入本體的輸出，本體的設定須先完成。
evaluationDependsOn(":minecraft_v1.20.1_forge")
val mainMod = project(":minecraft_v1.20.1_forge")
val mainModSourceSets = mainMod.extensions.getByType<SourceSetContainer>()

configure<MinecraftModPackagingExtension> {
    modId.set("mahjongcraft_example")
    displayName.set("MahjongCraft Extension Example")
    description.set("An isolated example of extending MahjongCraft.")
    archiveBaseName.set("mahjongcraft-example-forge-1.20.1")
    // 範例模組不是交付給玩家的成品，CI 不收集它的 JAR。
    distributable.set(false)
    dependencyRequirements.put("minecraft", mc1201.versions.require.forge.minecraft)
    dependencyRequirements.put("forge", mc1201.versions.require.forge.loader)
    dependencyRequirements.put("kotlinForForge", mc1201.versions.require.forge.kotlinforforge)
}

repositories {
    // Kotlin for Forge 只發布在作者自己的 Maven 倉庫。
    kotlinForForge()
}

// 範例中呼叫 Minecraft 1.20.1 但不用載入器的程式在 :example-minecraft_v1.20.1_mod；比照本體，把原始碼納入本模組，
// 由 ModDevGradle 一起轉成 Forge 執行時的名稱並打包。
includeSourcesOf(":example-minecraft_v1.20.1_mod")

// 與 Fabric 共用的程式只能用原版寫法，不顯示 Forge 對原版寫法的過時警告。
hideForgeDeprecationsOfVanillaApis()

// 只收集兩份範例程式庫，不將它們依賴的本體與 Kotlin 套件再打包一次。
val exampleLibraries = configurations.create("exampleLibraries") {
    isCanBeConsumed = false
    isTransitive = false
}

dependencies {
    modImplementation(mc1201.kotlinforforge)
    modCompileOnly(mc1201.kotlinforforge.lib)

    // 對本體編譯；開發執行時本體由下方的開發用 mod 載入，不放在啟動 classpath，以免同一個 mod 出現兩次。
    compileOnly(mainMod)
    compileOnly(project(":mahjong-extension-api"))
    compileOnly(project(":minecraft_common"))
    compileOnly(project(":example:example-mahjong-extension"))
    compileOnly(project(":example-minecraft_common"))
    exampleLibraries(project(":example:example-mahjong-extension"))
    exampleLibraries(project(":example-minecraft_common"))
    // 本體的 mixin 使用 MixinExtras；正式環境由本體的 JAR 附帶，開發執行由這個依賴提供。
    runtimeOnly(mc1201.mixinextras.forge)

    testImplementation(kotlin("test"))
}

// 開發執行時範例程式庫要與範例同屬一個 mod，才看得到 Kotlin for Forge 提供的 Kotlin；解開放進只供開發執行的 source set。
val unpackLibraryJars = objects.newInstance<UnpackLibraryJars>()
val unpackExampleLibraries = tasks.register<Sync>("unpackExampleLibraries") {
    from(exampleLibraries.elements.map(unpackLibraryJars)) {
        exclude("META-INF/MANIFEST.MF")
    }
    into(layout.buildDirectory.dir("exampleLibraries"))
}
val exampleLibrariesSourceSet = sourceSets.create("exampleLibraries") {
    output.dir(mapOf("builtBy" to unpackExampleLibraries), layout.buildDirectory.dir("exampleLibraries"))
}

legacyForge {
    version = mc1201.versions.forge.get()
    // 與本體使用同一份 access transformer，開發用的 Minecraft 與本體的開發執行相同。
    accessTransformers.from(minecraftModAccessTransformer())
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
    mods {
        create("mahjongcraft_example") {
            sourceSet(sourceSets.main.get())
            sourceSet(exampleLibrariesSourceSet)
        }
        // 本體以它自己開發執行時的內容載入：本體的輸出與解開的函式庫。
        create("mahjongcraft") {
            sourceSet(mainModSourceSets.main.get())
            sourceSet(mainModSourceSets.getByName("devLibraries"))
        }
    }
    // 單元測試只測不需要啟動遊戲的程式；測試也要對 Minecraft 與 Forge 的類別編譯。
    addModdingDependenciesTo(sourceSets.test.get())
}

// 開發執行載入本體的 mixin；範例自己沒有 mixin。
mixin {
    config("mahjongcraft.forge.mixins.json")
    config("mahjongcraft.mixins.json")
}

tasks.jar {
    dependsOn(exampleLibraries)
    // 將範例程式庫的內容合併進同一個 mod；不產生額外的 Forge mod 或 jar-in-jar。
    from(exampleLibraries.elements.map(unpackLibraryJars)) {
        exclude("META-INF/MANIFEST.MF")
    }
}

// 測試讀取轉成 Forge 執行時名稱後、實際交付的兩份 JAR。
val mainModJar = mainMod.tasks.named("reobfJar")
val exampleModJar = tasks.named("reobfJar")
val packagingTestArguments = objects.newInstance<ModPackagingTestArguments>().apply {
    mainJar.from(mainModJar)
    exampleJar.from(exampleModJar)
}
tasks.test {
    dependsOn(mainModJar, exampleModJar)
    jvmArgumentProviders.add(packagingTestArguments)
}
