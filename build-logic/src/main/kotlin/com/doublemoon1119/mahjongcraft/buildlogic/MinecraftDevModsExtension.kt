package com.doublemoon1119.mahjongcraft.buildlogic

import org.gradle.api.GradleException
import org.gradle.api.Project
import org.gradle.api.artifacts.MinimalExternalModuleDependency
import org.gradle.api.artifacts.dsl.DependencyHandler
import org.gradle.api.provider.Property
import org.gradle.api.provider.Provider
import javax.inject.Inject

/**
 * 開發執行時可選載入的模組組合，例如相容性測試用的光影模組或模組清單。
 *
 * 各載入器模組以 [group] 宣告自己的組合；開發者以 `-PmahjongcraftDevMods` 列出要載入的組合名稱。被選中的組合
 * 加入只在開發執行時使用的設定，不打包進模組，也不成為模組的依賴。沒有選擇任何組合時不加入任何東西。
 *
 * @param selected 開關選擇的組合名稱。
 * @param dependencyHandler 加入依賴的對象。
 * @param project 擁有這個擴充的專案，用來登記放入光影包的任務。
 */
abstract class MinecraftDevModsExtension @Inject constructor(
    private val selected: Set<String>,
    private val dependencyHandler: DependencyHandler,
    private val project: Project,
) {
    /** 選中的組合要加入的設定名稱；Fabric Loom 模組預設為只在開發執行時載入的 `modLocalRuntime`。 */
    abstract val configurationName: Property<String>

    /** 已宣告的組合名稱，依宣告順序。 */
    private val declared = linkedSetOf<String>()

    /** 已宣告的組合名稱，依宣告順序。 */
    internal val groupNames: Set<String>
        get() = declared

    /**
     * 宣告一個組合；開關選中這個組合時立刻加入它的模組，讓載入器外掛在處理開發依賴前就看得到。
     *
     * @param name 組合名稱，只用小寫英文字母、數字與連字號。
     * @param dependencies 組合包含的模組，可以是依賴座標字串或版本目錄的項目。
     */
    fun group(name: String, vararg dependencies: Any) {
        MinecraftDevMods.requireValidName(name)
        if (!declared.add(name)) throw GradleException("Development mod group '$name' is declared more than once")
        if (name !in selected) return
        val configuration = configurationName.orNull
            ?: throw GradleException("Development mod group '$name' is selected but this project has no development runtime configuration")
        dependencies.forEach { dependency ->
            if (dependency is Provider<*>) dependencyHandler.addProvider(configuration, dependency) else dependencyHandler.add(configuration, dependency)
        }
    }

    /**
     * 指定組合被選中時，開發用戶端啟動前放入並開啟這個光影包；手動開啟的用戶端與自動測試都直接在光影下執行。光影包以 zip 檔
     * 取得，只在開發執行時使用，不打包。
     *
     * @param group 已以 [group] 宣告、載入光影模組的組合名稱。
     * @param pack 光影包，例如版本目錄中 Modrinth Maven 倉庫上的項目。
     * @throws GradleException 組合還沒宣告時拋出。
     */
    fun shaderPack(group: String, pack: Provider<MinimalExternalModuleDependency>) {
        if (group !in declared) throw GradleException("Development mod group '$group' must be declared before its shader pack")
        if (group !in selected) return
        project.registerDevShaderPack(pack)
    }
}

/** 開發執行時可選載入模組的開關解析與檢查。 */
internal object MinecraftDevMods {
    /** 選擇組合的 Gradle property 名稱。 */
    const val PROPERTY_NAME: String = "mahjongcraftDevMods"

    /** 組合名稱的格式。 */
    private val NAME_REGEX = Regex("[a-z0-9]+(?:-[a-z0-9]+)*")

    /**
     * 解析開關的值。
     *
     * @param value 以逗號分隔的組合名稱；沒有給時為 null。
     * @return 去掉空白與重複後的組合名稱；沒有給或只有空白時為空。
     */
    fun parse(value: String?): Set<String> = value.orEmpty()
        .split(',')
        .map(String::trim)
        .filter(String::isNotEmpty)
        .onEach(::requireValidName)
        .toCollection(linkedSetOf())

    /**
     * 確認組合名稱的格式。
     *
     * @param name 組合名稱。
     * @throws GradleException 名稱不符合格式時拋出。
     */
    fun requireValidName(name: String) {
        if (!NAME_REGEX.matches(name)) {
            throw GradleException("Invalid development mod group name '$name': use lowercase letters, digits and hyphens")
        }
    }

    /**
     * 確認選擇的組合都已宣告。
     *
     * 開關對整個建置有效；沒有宣告任何組合的專案沒有可選的開發用模組，直接略過開關。
     *
     * @param projectPath 專案路徑，寫進錯誤訊息。
     * @param selected 開關選擇的組合名稱。
     * @param declared 專案宣告的組合名稱。
     * @throws GradleException 專案有宣告組合、而選擇了其中沒有的組合時拋出，並列出可用的組合。
     */
    fun requireDeclared(projectPath: String, selected: Set<String>, declared: Set<String>) {
        if (declared.isEmpty()) return
        val unknown = selected - declared
        if (unknown.isEmpty()) return
        val available = declared.joinToString().ifEmpty { "none" }
        throw GradleException(
            "Unknown development mod groups for $projectPath: ${unknown.joinToString()}. Available: $available. " +
                "Set them with -P$PROPERTY_NAME=<group>[,<group>...].",
        )
    }
}
