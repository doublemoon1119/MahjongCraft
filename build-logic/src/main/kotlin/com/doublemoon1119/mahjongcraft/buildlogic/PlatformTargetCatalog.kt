package com.doublemoon1119.mahjongcraft.buildlogic

import com.akuleshov7.ktoml.Toml
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import org.gradle.api.GradleException
import java.io.File

/**
 * KToml 反序列化使用的 catalog 文件。
 *
 * @property schemaVersion Catalog schema 版本。
 * @property targets 正式平台 target 清單。
 */
@Serializable
private data class PlatformTargetCatalogDocument(
    @SerialName("schema-version")
    val schemaVersion: Int,
    val targets: List<PlatformTargetDefinition> = emptyList(),
)

/**
 * KToml 反序列化使用的平台中立 target 定義。
 *
 * @property id 穩定完整 ID。
 * @property platform 平台種類。
 * @property javaToolchain JDK toolchain。
 * @property javaRelease JVM bytecode release。
 * @property modules 應載入的 module paths。
 * @property exampleModules 啟用範例建置時額外載入的 module paths。
 * @property attributes 平台自行定義的附加欄位。
 * @property providedVersions 執行環境提供的函式庫版本。
 */
@Serializable
private data class PlatformTargetDefinition(
    val id: String,
    val platform: String,
    @SerialName("java-toolchain")
    val javaToolchain: Int,
    @SerialName("java-release")
    val javaRelease: Int,
    val modules: List<String>,
    @SerialName("example-modules")
    val exampleModules: List<String> = emptyList(),
    val attributes: Map<String, String> = emptyMap(),
    @SerialName("provided-versions")
    val providedVersions: Map<String, String> = emptyMap(),
)

/** 解析並驗證受版控的平台 target TOML。 */
object PlatformTargetCatalog {
    /** 目前支援的 catalog schema。 */
    const val SUPPORTED_SCHEMA_VERSION: Int = 1

    /**
     * 找出 repository 中所有平台 target 檔案：`gradle/platform/<平台>/<版本>/targets.toml`。
     *
     * @param rootDirectory repository 根目錄。
     * @return 依相對路徑排序的檔案。沒有時為空。
     */
    fun targetFiles(rootDirectory: File): List<File> = rootDirectory.resolve(TARGET_FILE_DIRECTORY)
        .listFiles().orEmpty()
        .filter(File::isDirectory)
        .flatMap { platform -> platform.listFiles().orEmpty().filter(File::isDirectory) }
        .map { version -> version.resolve(TARGET_FILE_NAME) }
        .filter(File::isFile)
        .sortedBy { it.relativeTo(rootDirectory).invariantSeparatorsPath }

    /**
     * 載入並驗證 repository 中所有平台 target 檔案的 target。
     *
     * @param rootDirectory 用於找出 target 檔案與驗證 module 路徑的 repository 根目錄。
     * @return 依檔案路徑與檔案內順序排列的已驗證 target。沒有 target 檔案時為空。
     */
    fun loadAll(rootDirectory: File): List<PlatformTarget> {
        val targets = targetFiles(rootDirectory).flatMap(::parse)
        validateTargets(targets, rootDirectory)
        return targets
    }

    /**
     * 解析單一 target 檔案並驗證其中的 target。
     *
     * @param file 要解析的 TOML target 檔案。
     * @param rootDirectory 用於解析及驗證 target module 路徑的 repository 根目錄。
     * @return 依檔案順序排列的已驗證 target。
     */
    fun load(file: File, rootDirectory: File): List<PlatformTarget> {
        val targets = parse(file)
        validateTargets(targets, rootDirectory)
        return targets
    }

    /**
     * 解析單一 target 檔案，不驗證 target 內容。
     *
     * @param file 要解析的 TOML target 檔案。
     * @return 依檔案順序排列的 target。
     */
    private fun parse(file: File): List<PlatformTarget> {
        if (!file.isFile) throw GradleException("Platform target catalog does not exist: $file")
        val document = try {
            Toml.decodeFromString(PlatformTargetCatalogDocument.serializer(), file.readText())
        } catch (error: Exception) {
            throw GradleException("Invalid platform target catalog $file: ${error.message}", error)
        }
        if (document.schemaVersion != SUPPORTED_SCHEMA_VERSION) {
            throw GradleException(
                "Unsupported platform target catalog schema ${document.schemaVersion}; " +
                    "expected $SUPPORTED_SCHEMA_VERSION",
            )
        }
        return document.targets.map { definition ->
            PlatformTarget(
                id = definition.id.trim(),
                platform = definition.platform.trim(),
                javaToolchain = definition.javaToolchain,
                javaRelease = definition.javaRelease,
                modules = definition.modules.map(::normalizeModulePath),
                exampleModules = definition.exampleModules.map(::normalizeModulePath),
                attributes = definition.attributes,
                providedVersions = definition.providedVersions.mapKeys { it.key.trim() }.mapValues { it.value.trim() },
            )
        }
    }

    /**
     * 只讀取 local properties 中的 target fallback。
     *
     * @param file 含有 target property 的本機設定檔。
     * @return 去除空白後的 target ID。檔案不存在或 property 為空白時回傳 null。
     */
    fun readLocalTarget(file: File): String? {
        if (!file.isFile) return null
        return java.util.Properties().apply { file.inputStream().use(::load) }
            .getProperty(TARGET_PROPERTY_NAME)
            ?.trim()
            ?.takeIf(String::isNotEmpty)
    }

    /**
     * 依固定優先序選出 target 的指定。空白輸入視為未指定。
     *
     * @param gradleProperty 命令列或 Gradle property 指定的 target。
     * @param environmentVariable 環境變數指定的 target。
     * @param localProperty 本機設定檔中的 fallback target。
     * @return 依 Gradle property、環境變數、本機設定檔順序選出的指定，可能是以逗號分隔的多個 ID。皆未指定時回傳 core ID。
     */
    fun resolveSelectedTargetId(
        gradleProperty: String?,
        environmentVariable: String?,
        localProperty: String?,
    ): String = sequenceOf(gradleProperty, environmentVariable, localProperty)
        .mapNotNull { it?.trim()?.takeIf(String::isNotEmpty) }
        .firstOrNull()
        ?: CORE_TARGET_ID

    /**
     * 解析選擇的 target 清單。
     *
     * @param selection 以逗號分隔的 target ID，例如同一個 Minecraft 版本的兩個載入器。`core` 只能單獨出現。
     * @param targets catalog 中的 target。
     * @return 依指定順序排列的 target。選擇 `core` 時為空。
     * @throws GradleException 清單有空白項目、重複的 ID、與其他 target 並列的 `core` 或不存在的 ID。
     */
    fun selectTargets(selection: String, targets: List<PlatformTarget>): List<PlatformTarget> {
        val ids = selection.split(',').map(String::trim)
        if (ids.any(String::isEmpty)) {
            throw GradleException("Invalid MahjongCraft target list '$selection'; separate target IDs with single commas")
        }
        val duplicates = ids.groupingBy { it }.eachCount().filterValues { it > 1 }.keys
        if (duplicates.isNotEmpty()) throw GradleException("MahjongCraft target list '$selection' repeats ${duplicates.sorted()}")
        if (CORE_TARGET_ID in ids) {
            if (ids.size > 1) throw GradleException("'$CORE_TARGET_ID' selects no platform target and cannot be combined: $selection")
            return emptyList()
        }
        return ids.map { id ->
            targets.singleOrNull { it.id == id } ?: throw GradleException(
                "Unknown MahjongCraft target '$id'. Available targets: " +
                    (listOf(CORE_TARGET_ID) + targets.map(PlatformTarget::id)).joinToString(),
            )
        }
    }

    /**
     * 驗證 target ID、平台設定、Java 版本與 repository 內的 module 目錄。
     *
     * @param targets 待驗證的平台 target 清單。
     * @param rootDirectory repository 根目錄，用於檢查 target 對應的 module 目錄。
     */
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
            validateModulePaths(target, rootDirectory)
        }
    }

    /** 驗證正式與範例模組的路徑、存在性及分類。 */
    private fun validateModulePaths(target: PlatformTarget, rootDirectory: File) {
        val formal = target.modules.map(::normalizeModulePath)
        val examples = target.exampleModules.map(::normalizeModulePath)
        val duplicateFormal = formal.groupingBy(String::lowercase).eachCount().filterValues { it > 1 }.keys
        if (duplicateFormal.isNotEmpty()) {
            throw GradleException("Platform target ${target.id} repeats modules: $duplicateFormal")
        }
        val duplicateExamples = examples.groupingBy(String::lowercase).eachCount().filterValues { it > 1 }.keys
        if (duplicateExamples.isNotEmpty()) {
            throw GradleException("Platform target ${target.id} repeats example modules: $duplicateExamples")
        }
        val overlap = formal.map(String::lowercase).toSet().intersect(examples.map(String::lowercase).toSet())
        if (overlap.isNotEmpty()) {
            throw GradleException("Platform target ${target.id} declares modules as both formal and example: $overlap")
        }
        formal.forEach { module ->
            if (isExampleModulePath(module)) {
                throw GradleException("Platform target ${target.id} has an example path in formal modules: $module")
            }
        }
        examples.forEach { module ->
            if (!isExampleModulePath(module)) {
                throw GradleException("Platform target ${target.id} has non-example path in example-modules: $module")
            }
            if (module.startsWith("example/platform/")) {
                val platformParts = module.removePrefix("example/platform/").split('/')
                if (platformParts.size < 2 || platformParts.any(String::isEmpty)) {
                    throw GradleException("Platform example module must specify a platform and module: $module")
                }
            } else if (module.startsWith("example/") && module.removePrefix("example/").contains('/')) {
                throw GradleException("Root example module must be an immediate child of example/: $module")
            }
        }
        val canonicalPaths = mutableMapOf<String, String>()
        (formal + examples).forEach { module ->
            val directory = rootDirectory.resolve(module).canonicalFile
            if (!directory.toPath().startsWith(rootDirectory.canonicalFile.toPath()) || !directory.isDirectory) {
                throw GradleException("Platform target ${target.id} references missing module: $module")
            }
            val canonicalKey = directory.path.lowercase()
            val previous = canonicalPaths.putIfAbsent(canonicalKey, module)
            if (previous != null && previous != module) {
                throw GradleException(
                    "Platform target ${target.id} maps modules to the same directory: $previous and $module",
                )
            }
        }
    }

    /** 正規化並拒絕會離開 repository 的 module 路徑。 */
    private fun normalizeModulePath(path: String): String {
        val normalized = path.trim().replace('\\', '/')
        if (normalized.isEmpty() || normalized.startsWith('/') || normalized.split('/').any { it == ".." }) {
            throw GradleException("Invalid platform module path: $path")
        }
        return normalized.removeSuffix("/")
    }

    /** 判斷路徑是否屬於範例區域，避免正式模組誤載入範例。 */
    private fun isExampleModulePath(path: String): Boolean =
        path.startsWith("example/") || path.contains("/example/")

    /** 平台 target 檔案所在的資料夾，相對於 repository 根目錄。 */
    const val TARGET_FILE_DIRECTORY: String = "gradle/platform"

    /** 每個平台版本資料夾中的 target 檔名。 */
    const val TARGET_FILE_NAME: String = "targets.toml"

    /** Core-only 的保留 target ID。 */
    const val CORE_TARGET_ID: String = "core"

    /** 統一 target property 名稱。 */
    const val TARGET_PROPERTY_NAME: String = "mahjongcraftTarget"

    /** 簡短 target 環境變數名稱。 */
    const val TARGET_ENVIRONMENT_NAME: String = "MAHJONGCRAFT_TARGET"

    /** 啟用範例模組的 Gradle property 名稱。 */
    const val EXAMPLE_PROPERTY_NAME: String = "mahjongcraftExample"

    /** 支援的 Java toolchain 與 release 數值範圍。 */
    private val JAVA_VERSION_RANGE = 8..99

    /** 各已知平台必須提供的 attribute 名稱。 */
    private val REQUIRED_ATTRIBUTES_BY_PLATFORM = mapOf(
        "minecraft" to setOf("minecraft-version", "loader"),
    )

    /** Target 與平台 ID 共用的格式限制。 */
    private val TARGET_ID_REGEX = Regex("[a-z0-9]+(?:[._-][a-z0-9]+)*")
}
