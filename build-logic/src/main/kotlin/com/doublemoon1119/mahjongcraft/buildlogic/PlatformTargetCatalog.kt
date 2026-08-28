package com.doublemoon1119.mahjongcraft.buildlogic

import com.akuleshov7.ktoml.Toml
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import org.gradle.api.GradleException
import java.io.File

/** 單一正式平台建置目標的通用設定。 */
data class PlatformTarget(
    /** 供 CLI、環境變數與 CI 使用的穩定完整 ID。 */
    val id: String,
    /** 平台種類；核心載入器不假設任何特定平台。 */
    val platform: String,
    /** 編譯此 target 使用的 JDK toolchain。 */
    val javaToolchain: Int,
    /** 此 target 產出的最低 JVM bytecode 版本。 */
    val javaRelease: Int,
    /** Settings 階段必須載入的模組目錄。 */
    val modules: List<String>,
    /** 由各平台自行定義並解讀的額外屬性。 */
    val attributes: Map<String, String>,
)

/** KToml 反序列化使用的 catalog 文件。 */
@Serializable
private data class PlatformTargetCatalogDocument(
    /** Catalog schema 版本。 */
    @SerialName("schema-version")
    val schemaVersion: Int,
    /** 正式平台 target 清單。 */
    val targets: List<PlatformTargetDefinition> = emptyList(),
)

/** KToml 反序列化使用的平台中立 target 定義。 */
@Serializable
private data class PlatformTargetDefinition(
    /** 穩定完整 ID。 */
    val id: String,
    /** 平台種類。 */
    val platform: String,
    /** JDK toolchain。 */
    @SerialName("java-toolchain")
    val javaToolchain: Int,
    /** JVM bytecode release。 */
    @SerialName("java-release")
    val javaRelease: Int,
    /** 應載入的 module paths。 */
    val modules: List<String>,
    /** 平台自行定義的附加欄位。 */
    val attributes: Map<String, String> = emptyMap(),
)

/** 解析並驗證受版控的平台 target TOML。 */
object PlatformTargetCatalog {
    /** 目前支援的 catalog schema。 */
    const val SUPPORTED_SCHEMA_VERSION: Int = 1

    /** 解析 [file] 並驗證所有 target。 */
    fun load(file: File, rootDirectory: File): List<PlatformTarget> {
        if (!file.isFile) throw GradleException("Platform target catalog does not exist: $file")
        val document = try {
            Toml.decodeFromString(PlatformTargetCatalogDocument.serializer(), file.readText())
        } catch (error: Exception) {
            throw GradleException("Invalid platform target catalog: ${error.message}", error)
        }
        if (document.schemaVersion != SUPPORTED_SCHEMA_VERSION) {
            throw GradleException(
                "Unsupported platform target catalog schema ${document.schemaVersion}; " +
                    "expected $SUPPORTED_SCHEMA_VERSION",
            )
        }
        val targets = document.targets.map { definition ->
            PlatformTarget(
                id = definition.id.trim(),
                platform = definition.platform.trim(),
                javaToolchain = definition.javaToolchain,
                javaRelease = definition.javaRelease,
                modules = definition.modules,
                attributes = definition.attributes,
            )
        }
        validateTargets(targets, rootDirectory)
        return targets
    }

    /** 只讀取 local properties 中的 target fallback。 */
    fun readLocalTarget(file: File): String? {
        if (!file.isFile) return null
        return java.util.Properties().apply { file.inputStream().use(::load) }
            .getProperty(TARGET_PROPERTY_NAME)
            ?.trim()
            ?.takeIf(String::isNotEmpty)
    }

    /** 依固定優先序選出 target；空白輸入視為未指定。 */
    fun resolveSelectedTargetId(
        gradleProperty: String?,
        environmentVariable: String?,
        localProperty: String?,
    ): String = sequenceOf(gradleProperty, environmentVariable, localProperty)
        .mapNotNull { it?.trim()?.takeIf(String::isNotEmpty) }
        .firstOrNull()
        ?: CORE_TARGET_ID

    private fun validateTargets(targets: List<PlatformTarget>, rootDirectory: File) {
        val duplicateIds = targets.groupingBy(PlatformTarget::id).eachCount().filterValues { it > 1 }.keys
        if (duplicateIds.isNotEmpty()) throw GradleException("Duplicate platform target IDs: ${duplicateIds.sorted()}")
        targets.forEach { target ->
            if (target.id == CORE_TARGET_ID) throw GradleException("'$CORE_TARGET_ID' is reserved and cannot appear in the catalog")
            if (!TARGET_ID_REGEX.matches(target.id)) throw GradleException("Invalid platform target ID: ${target.id}")
            if (!TARGET_ID_REGEX.matches(target.platform)) throw GradleException("Invalid platform ID: ${target.platform}")
            if (target.javaToolchain !in JAVA_VERSION_RANGE) {
                throw GradleException("Platform target ${target.id} has invalid java-toolchain: ${target.javaToolchain}")
            }
            if (target.javaRelease !in JAVA_VERSION_RANGE) {
                throw GradleException("Platform target ${target.id} has invalid java-release: ${target.javaRelease}")
            }
            val missingAttributes = REQUIRED_ATTRIBUTES_BY_PLATFORM[target.platform].orEmpty() - target.attributes.keys
            if (missingAttributes.isNotEmpty()) {
                throw GradleException(
                    "Platform target ${target.id} is missing ${target.platform} attributes: " +
                        missingAttributes.sorted(),
                )
            }
            if (target.modules.isEmpty()) throw GradleException("Platform target ${target.id} has no modules")
            val duplicates = target.modules.groupingBy(String::lowercase).eachCount().filterValues { it > 1 }.keys
            if (duplicates.isNotEmpty()) throw GradleException("Platform target ${target.id} repeats modules: $duplicates")
            target.modules.forEach { module ->
                val directory = rootDirectory.resolve(module).canonicalFile
                if (!directory.toPath().startsWith(rootDirectory.canonicalFile.toPath()) || !directory.isDirectory) {
                    throw GradleException("Platform target ${target.id} references missing module: $module")
                }
            }
        }
    }

    /** Core-only 的保留 target ID。 */
    const val CORE_TARGET_ID: String = "core"

    /** 統一 target property 名稱。 */
    const val TARGET_PROPERTY_NAME: String = "mahjongcraftTarget"

    /** 簡短 target 環境變數名稱。 */
    const val TARGET_ENVIRONMENT_NAME: String = "MAHJONGCRAFT_TARGET"

    private val JAVA_VERSION_RANGE = 8..99
    private val REQUIRED_ATTRIBUTES_BY_PLATFORM = mapOf(
        "minecraft" to setOf("minecraft-version", "loader"),
    )
    private val TARGET_ID_REGEX = Regex("[a-z0-9]+(?:[._-][a-z0-9]+)*")
}
