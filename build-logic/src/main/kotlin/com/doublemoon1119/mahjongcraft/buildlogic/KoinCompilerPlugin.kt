package com.doublemoon1119.mahjongcraft.buildlogic

import org.gradle.api.GradleException
import org.gradle.api.Project
import org.gradle.api.provider.Property

/** 控制 Koin 完整依賴檢查的 Gradle property。 */
internal const val KOIN_STRICT_SAFETY_PROPERTY: String = "mahjongcraftKoinStrictSafety"

/** Koin 編譯外掛的 plugin ID。 */
private const val KOIN_COMPILER_PLUGIN_ID: String = "io.insert-koin.compiler.plugin"

/** Koin 編譯外掛的設定名稱。 */
private const val KOIN_COMPILER_EXTENSION_NAME: String = "koinCompiler"

/** 外掛的資訊性輸出不算警告時使用的等級。 */
private const val KOIN_LOG_SEVERITY_INFO: String = "info"

/**
 * 設定套用 Koin 編譯外掛的模組。
 *
 * 資訊性輸出：
 *
 * - 外掛的資訊性輸出改為資訊等級，例如沒有啟動點的模組回報的 `compile-safety validation skipped`。編譯警告會讓建置
 *   失敗，這類輸出不是程式的問題。
 * - 外掛的診斷不受影響，例如缺少依賴。
 *
 * 完整依賴檢查的時機：
 *
 * - 外掛發現模組含有 Koin 的啟動點時，會讓編譯任務永遠不算最新，每次建置都重新編譯並檢查整張依賴關係。
 *   這樣能抓到其他模組改了登記內容、卻沒有改公開介面的情況。
 * - 這個檢查預設只在 CI 執行。本機以 [KOIN_STRICT_SAFETY_PROPERTY] 開啟，例如提交前的完整驗證。
 * - 關閉時，模組自己重新編譯仍會檢查依賴關係。
 *
 * 外掛不是 build-logic 的依賴，因此以設定名稱與屬性名稱取得它的設定。
 */
internal fun Project.configureKoinCompilerPlugin() {
    pluginManager.withPlugin(KOIN_COMPILER_PLUGIN_ID) {
        val extension = extensions.getByName(KOIN_COMPILER_EXTENSION_NAME)
        extension.property<String>("getLogSeverity").set(KOIN_LOG_SEVERITY_INFO)
        val enabled = koinStrictSafetyEnabled(
            property = providers.gradleProperty(KOIN_STRICT_SAFETY_PROPERTY).orNull,
            ci = providers.environmentVariable("CI").orNull,
        )
        if (!enabled) {
            // 外掛只在兩者都設定時才關閉：strictSafety 設為 false，並以 strictSafetyForceOff 確認。
            extension.property<Boolean>("getStrictSafety").set(false)
            extension.property<Boolean>("getStrictSafetyForceOff").set(true)
        }
    }
}

/**
 * 以 getter 名稱取得外掛設定中的屬性。
 *
 * @param T 屬性值的型別。
 * @param getter 屬性的 getter 名稱。
 * @return 該屬性。
 */
@Suppress("UNCHECKED_CAST")
private fun <T : Any> Any.property(getter: String): Property<T> = javaClass.getMethod(getter).invoke(this) as Property<T>

/**
 * 解析是否執行 Koin 完整依賴檢查。
 *
 * @param property [KOIN_STRICT_SAFETY_PROPERTY] 的值。沒有指定時為 null。
 * @param ci 環境變數 `CI` 的值。不在 CI 中時為 null。
 * @return 有指定 property 時依它決定。沒有指定時只在 CI 中執行。
 * @throws GradleException property 不是 `true` 或 `false`。
 */
internal fun koinStrictSafetyEnabled(property: String?, ci: String?): Boolean = when (property) {
    null -> ci != null && ci != "false"
    "true" -> true
    "false" -> false
    else -> throw GradleException("Invalid -P$KOIN_STRICT_SAFETY_PROPERTY='$property'; expected true or false")
}
