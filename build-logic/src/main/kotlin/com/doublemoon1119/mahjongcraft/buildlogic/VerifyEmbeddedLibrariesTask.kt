package com.doublemoon1119.mahjongcraft.buildlogic

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import org.gradle.api.DefaultTask
import org.gradle.api.GradleException
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.provider.SetProperty
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputFile
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.TaskAction
import java.util.zip.ZipFile

/**
 * 驗證 mod JAR 以 jar-in-jar 附帶了執行時需要的每個外部函式庫，而且版本相同。
 *
 * jar-in-jar 只附帶直接宣告的函式庫。函式庫自己依賴的函式庫沒有另外宣告時，執行時才會找不到類別。
 */
abstract class VerifyEmbeddedLibrariesTask : DefaultTask() {
    /** 要檢查的 mod JAR。 */
    @get:InputFile
    @get:PathSensitive(PathSensitivity.NONE)
    abstract val modJar: RegularFileProperty

    /** 執行時需要的外部函式庫，格式為 `group:artifact:version`。 */
    @get:Input
    abstract val requiredLibraries: SetProperty<String>

    /** 比對 jar-in-jar 的清單並列出缺少或版本不同的函式庫。 */
    @TaskAction
    fun verify() {
        val metadata = ZipFile(modJar.get().asFile).use { zip ->
            zip.getEntry(JAR_IN_JAR_METADATA)?.let { entry -> zip.getInputStream(entry).use { it.readBytes().decodeToString() } }
        }
        val missing = findMissingEmbeddedLibraries(requiredLibraries.get(), metadata?.let(::embeddedLibraries).orEmpty())
        if (missing.isNotEmpty()) {
            throw GradleException("The mod JAR does not embed required libraries:\n" + missing.joinToString("\n") { "  - $it" })
        }
    }
}

/**
 * 讀出 jar-in-jar 清單中附帶的函式庫。
 *
 * @param metadataJson `META-INF/jarjar/metadata.json` 的內容。
 * @return 格式為 `group:artifact:version` 的函式庫。
 */
internal fun embeddedLibraries(metadataJson: String): Set<String> = METADATA_JSON.decodeFromString(JarJarMetadata.serializer(), metadataJson)
    .jars
    .map { "${it.identifier.group}:${it.identifier.artifact}:${it.version.artifactVersion}" }
    .toSet()

/**
 * 找出需要但沒有附帶的函式庫。
 *
 * @param required 執行時需要的函式庫，格式為 `group:artifact:version`。
 * @param embedded 已附帶的函式庫，格式相同。
 * @return 依名稱排序、缺少或版本不同的函式庫。已附帶同名但版本不同時一併寫出附帶的版本。
 */
internal fun findMissingEmbeddedLibraries(required: Set<String>, embedded: Set<String>): List<String> {
    val embeddedVersions = embedded.associate { it.substringBeforeLast(':') to it.substringAfterLast(':') }
    return (required - embedded).sorted().map { library ->
        embeddedVersions[library.substringBeforeLast(':')]?.let { version -> "$library (embedded version $version)" } ?: library
    }
}

/**
 * jar-in-jar 清單。
 *
 * @property jars 附帶的 JAR。
 */
@Serializable
private data class JarJarMetadata(val jars: List<JarJarEntry>)

/**
 * jar-in-jar 清單中的一個 JAR。
 *
 * @property identifier JAR 的 Maven 座標。
 * @property version JAR 的版本。
 */
@Serializable
private data class JarJarEntry(val identifier: JarJarIdentifier, val version: JarJarVersion)

/**
 * 附帶 JAR 的 Maven 座標。
 *
 * @property group Maven group。
 * @property artifact Maven artifact。
 */
@Serializable
private data class JarJarIdentifier(val group: String, val artifact: String)

/**
 * 附帶 JAR 的版本。
 *
 * @property artifactVersion 附帶的實際版本。
 */
@Serializable
private data class JarJarVersion(val artifactVersion: String)

/** jar-in-jar 清單在 JAR 中的路徑。 */
private const val JAR_IN_JAR_METADATA = "META-INF/jarjar/metadata.json"

/** 解析 jar-in-jar 清單，忽略用不到的欄位。 */
private val METADATA_JSON = Json { ignoreUnknownKeys = true }
