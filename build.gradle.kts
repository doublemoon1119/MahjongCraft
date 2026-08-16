import org.jetbrains.kotlin.gradle.dsl.JvmDefaultMode
import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.dsl.KotlinJvmProjectExtension
import org.jetbrains.kotlin.gradle.dsl.KotlinMultiplatformExtension
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile
import org.jlleitschuh.gradle.ktlint.KtlintExtension

plugins {
    alias(libs.plugins.kotlin.multiplatform) apply false
    alias(libs.plugins.kotlin.jvm) apply false
    alias(libs.plugins.koin.compiler) apply false
    alias(libs.plugins.ktlint) apply false
}

val modId = "mahjongcraft"
val modName = "MahjongCraft"
extra["mahjongcraftModId"] = modId
extra["mahjongcraftModName"] = modName

allprojects {
    group = "com.doublemoon1119.mahjongcraft"
    version = rootProject.libs.versions.project.version.get()

    // 必須先套用 base 插件，才能存取 base.archivesName
    apply(plugin = "base")

    // 取得 libs.versions.toml 中的預設 JVM Toolchain/Release 版本
    val jvmToolchainVersion = rootProject.libs.versions.jvm.toolchain.get().toInt()
    val jvmReleaseVersion = rootProject.libs.versions.jvm.release.get().toInt()

    // 統一設定 Kotlin jvmToolchain (這會自動影響 JavaCompile 和 KotlinCompile)
    plugins.withId("org.jetbrains.kotlin.multiplatform") {
        extensions.configure<KotlinMultiplatformExtension> {
            jvmToolchain(jvmToolchainVersion)
        }

        // 統一套用 Ktlint，排除 KSP（Koin annotations）產生的程式碼
        apply(plugin = "org.jlleitschuh.gradle.ktlint")
        extensions.configure<KtlintExtension> {
            filter {
                exclude("**/generated/**")
            }
        }
    }

    // Minecraft 平台模組（例如 Fabric Loom）用的是純 org.jetbrains.kotlin.jvm，不是 Kotlin Multiplatform，
    // 需要另外套用同一套 jvmToolchain/Ktlint 設定，才能跟其他模組維持一致
    plugins.withId("org.jetbrains.kotlin.jvm") {
        extensions.configure<KotlinJvmProjectExtension> {
            jvmToolchain(jvmToolchainVersion)
        }

        apply(plugin = "org.jlleitschuh.gradle.ktlint")
        extensions.configure<KtlintExtension> {
            filter {
                exclude("**/generated/**")
            }
        }
    }

    // Minecraft loader 模組（Fabric Loom／未來的 NeoForge）的最終產物檔名改用
    // <modid>-<loader>-<mcVersion>，符合玩家熟悉的 mod 命名慣例（modid-loader-mcversion-modversion），
    // 不直接沿用 Gradle 專案名稱本身（minecraft_v1.20.1_fabric 那種底線拼接是給 settings.gradle.kts 內部用的，
    // 不該直接外露成使用者看到的檔名）。mcVersion/loader 從 projectDir 路徑推回來
    // （settings.gradle.kts 把 projectDir 指到 platform/minecraft/<version>/<loader>），
    // 之後複製到其他版本/loader 不需要在這裡另外加設定。
    plugins.withId("fabric-loom") {
        val loader = projectDir.name
        val mcVersion = projectDir.parentFile.name.removePrefix("v")
        extensions.configure<BasePluginExtension> {
            archivesName.set("$modId-$loader-$mcVersion")
        }
    }

    afterEvaluate {
        // Java 編譯選項
        tasks.withType<JavaCompile>().configureEach {
            options.encoding = "UTF-8"
            options.release.set(jvmReleaseVersion)
        }

        // Kotlin 編譯選項
        tasks.withType<KotlinCompile>().configureEach {
            compilerOptions {
                jvmTarget.set(JvmTarget.fromTarget(jvmReleaseVersion.toString()))
                jvmDefault.set(JvmDefaultMode.NO_COMPATIBILITY)
            }
        }
    }

    val baseExtension = extensions.getByType<BasePluginExtension>()
    tasks.withType<Jar>().configureEach {
        from(rootProject.file("LICENSE")) {
            rename { "${it}_${baseExtension.archivesName.get()}" }
        }
    }

    // 統一處理資源替換：mod 中繼資料（id/name/description/license/作者/聯絡資訊）不分版本、
    // 不分 loader 都是同一份，集中在這裡宣告一次，每個 fabric.mod.json/neoforge.mods.toml
    // 只需要用 ${...} 佔位字串引用，不必每個平台模組各自重複填一份、之後改資訊要改 N 個地方
    tasks.withType<ProcessResources>().configureEach {
        val props = mapOf(
            "version" to project.version,
            "id" to modId,
            "name" to modName,
            "description" to "Play Japanese (Riichi) Mahjong with your friends.",
            "license" to "MIT",
            "author" to "doublemoon1119",
            "homepage" to "https://github.com/doublemoon1119/MahjongCraft",
            "sources" to "https://github.com/doublemoon1119/MahjongCraft",
            "issues" to "https://github.com/doublemoon1119/MahjongCraft/issues",
        )
        inputs.properties(props)
        filesMatching(listOf("fabric.mod.json", "META-INF/neoforge.mods.toml")) {
            expand(props)
        }
    }
}

/** `docs/temp` 不進 git，clone 的人本機沒有這些檔案，擋掉原始碼註解引用其中任何檔名。 */
val verifyNoDocsTempReferences = tasks.register("verifyNoDocsTempReferences") {
    group = "verification"
    description = "Fails if any Kotlin source references a file under the gitignored docs/temp directory."

    doLast {
        if (!file("docs/temp").exists()) return@doLast

        val gitOutput = ProcessBuilder("git", "ls-files", "--others", "--ignored", "--exclude-standard", "--", "docs/temp")
            .directory(rootDir)
            .redirectErrorStream(true)
            .start()
            .let { process ->
                val output = process.inputStream.bufferedReader().readText()
                process.waitFor()
                output
            }

        val ignoredFileNames = gitOutput.lineSequence()
            .map { File(it.trim()).name }
            .filter { it.isNotBlank() }
            .toSet()
        if (ignoredFileNames.isEmpty()) return@doLast

        val violations = mutableListOf<String>()
        allprojects.forEach { proj ->
            proj.projectDir.resolve("src").walkTopDown()
                .filter { it.isFile && it.extension == "kt" }
                .forEach { file ->
                    val text = file.readText()
                    ignoredFileNames.forEach { name ->
                        if (text.contains(name)) {
                            violations += "${file.relativeTo(rootDir)} references docs/temp file: $name"
                        }
                    }
                }
        }

        if (violations.isNotEmpty()) {
            throw GradleException(
                "Found references to gitignored docs/temp files in source comments " +
                    "(move the explanation into the comment itself instead of citing the file):\n" +
                    violations.joinToString("\n") { "  - $it" },
            )
        }
    }
}

tasks.named("check") {
    dependsOn(verifyNoDocsTempReferences)
}

/**
 * 開發環境切換任務
 * 用法:
 *   - 切換到 Minecraft: ./gradlew switchTarget -PtoPlatform=minecraft -PtoMcVer=v1.20.1 -PtoMcLoader=fabric
 */
tasks.register("switchTarget") {
    group = "mahjong"
    description = "Updates local.dev.properties to switch the active development platform and version."

    doLast {
        val toPlatform = project.findProperty("toPlatform")?.toString()

        if (toPlatform == null) {
            logger.error("ERROR: Missing parameter 'toPlatform'. Usage: ./gradlew switchTarget -PtoPlatform=minecraft")
            return@doLast
        }

        val propFile = rootProject.file("local.dev.properties")
        var content = "# Generated by switchTarget task\ntargetPlatform=$toPlatform\n"

        when (toPlatform) {
            "minecraft" -> {
                val toMcVer = project.findProperty("toMcVer")?.toString()
                val toMcLoader = project.findProperty("toMcLoader")?.toString()

                if (toMcVer == null || toMcLoader == null) {
                    logger.error("ERROR: For Minecraft, 'toMcVer' and 'toMcLoader' are required. Usage: ./gradlew switchTarget -PtoPlatform=minecraft -PtoMcVer=v1.20.1 -PtoMcLoader=fabric")
                    return@doLast
                }
                content += "targetMinecraftVersion=$toMcVer\n"
                content += "targetMinecraftLoader=$toMcLoader\n"
                logger.lifecycle("SUCCESS: Development target set to $toPlatform (Version: $toMcVer, Loader: $toMcLoader)")
            }
            // 可以為其他平台添加邏輯
            else -> {
                logger.lifecycle("SUCCESS: Development target set to $toPlatform")
            }
        }

        propFile.writeText(content.trim())

        logger.lifecycle("NOTE: Please re-import or reload Gradle project in your IDE to apply changes.")
    }
}
