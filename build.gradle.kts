plugins {
    base
    alias(libs.plugins.mahjongcraft.target.management)
}

group = "com.doublemoon1119.mahjongcraft"
version = "0.0.0-dev"

val projectId = "mahjongcraft"
val projectDisplayName = "MahjongCraft"
extra["projectId"] = projectId
extra["projectDisplayName"] = projectDisplayName

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

/** 驗證所有目前載入的 Gradle project 都明確使用其 release train 版本。 */
val verifyProjectVersionPolicy = tasks.register("verifyProjectVersionPolicy") {
    group = "verification"
    description = "Verifies every loaded project uses its declared MahjongCraft release-train version."
    doLast {
        val minecraftVersion = libs.versions.minecraft.mod.version.get()
        val logicVersion = libs.versions.logic.version.get()
        val flowVersion = libs.versions.flow.version.get()
        val aiVersion = libs.versions.ai.version.get()
        val extensionApiVersion = libs.versions.extension.api.version.get()
        val violations = rootProject.allprojects.mapNotNull { candidate ->
            val expected = when {
                candidate.path == ":" -> "0.0.0-dev"
                candidate.path == ":mahjong-logic" -> logicVersion
                candidate.path == ":mahjong-ai" -> aiVersion
                candidate.path == ":mahjong-extension-api" -> extensionApiVersion
                candidate.path == ":mahjong-flow" || candidate.path.startsWith(":mahjong-flow:") -> flowVersion
                candidate.path == ":testing" || candidate.path.startsWith(":testing:") -> "0.0.0-dev"
                candidate.projectDir.toPath().startsWith(rootDir.resolve("platform/minecraft").toPath()) -> minecraftVersion
                else -> return@mapNotNull "${candidate.path} has no version policy"
            }
            if (candidate.version.toString() == expected) null else "${candidate.path}: expected $expected, found ${candidate.version}"
        }
        if (violations.isNotEmpty()) {
            throw GradleException("Project version policy violations:\n" + violations.joinToString("\n") { "  - $it" })
        }
    }
}

tasks.named("check") {
    dependsOn(verifyNoDocsTempReferences)
    dependsOn(verifyProjectVersionPolicy)
}
