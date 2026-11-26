package com.doublemoon1119.mahjongcraft.buildlogic

import org.gradle.api.GradleException
import org.gradle.api.Project

/** 開啟測試覆蓋率量測的 Gradle property。 */
internal const val COVERAGE_PROPERTY: String = "mahjongcraftCoverage"

/** Kover 的 plugin ID。 */
private const val KOVER_PLUGIN_ID: String = "org.jetbrains.kotlinx.kover"

/** 根專案列出要合併的模組所用的 Kover 設定名稱。 */
private const val KOVER_CONFIGURATION: String = "kover"

/** 測試輔助模組所在的專案路徑。這些模組提供測試用的替身，不量覆蓋率。 */
private const val TESTING_PROJECT_PATH: String = ":testing"

/**
 * 在開啟 [COVERAGE_PROPERTY] 時，讓 Kotlin 模組套用 Kover。
 *
 * - Kover 讓模組的每個測試任務都帶著量測 agent，測試因此變慢。沒有開啟時不套用，平常的建置與測試不受影響。
 * - [TESTING_PROJECT_PATH] 底下的測試輔助模組不套用。
 */
internal fun Project.configureCoverage() {
    if (coverageEnabled() && !isTestingSupportProject()) pluginManager.apply(KOVER_PLUGIN_ID)
}

/**
 * 在開啟 [COVERAGE_PROPERTY] 時，讓根專案合併所有套用 Kover 的模組。
 *
 * 根專案的 `koverHtmlReport` 與 `koverXmlReport` 執行這些模組的測試，報告寫在 `build/reports/kover/`。
 */
fun Project.registerCoverageAggregate() {
    if (!coverageEnabled()) return
    pluginManager.apply(KOVER_PLUGIN_ID)
    subprojects.forEach { module ->
        module.pluginManager.withPlugin(KOVER_PLUGIN_ID) { dependencies.add(KOVER_CONFIGURATION, module) }
    }
}

/**
 * 這次建置是否開啟覆蓋率量測。
 *
 * @return 開啟時為 true。
 * @throws GradleException [COVERAGE_PROPERTY] 不是 `true` 或 `false`。
 */
private fun Project.coverageEnabled(): Boolean = coverageEnabled(providers.gradleProperty(COVERAGE_PROPERTY).orNull)

/**
 * 解析是否開啟覆蓋率量測。
 *
 * @param property [COVERAGE_PROPERTY] 的值。沒有指定時為 null。
 * @return 指定為 `true` 時為 true，沒有指定或指定為 `false` 時為 false。
 * @throws GradleException property 不是 `true` 或 `false`。
 */
internal fun coverageEnabled(property: String?): Boolean = when (property) {
    null, "false" -> false
    "true" -> true
    else -> throw GradleException("Invalid -P$COVERAGE_PROPERTY='$property'; expected true or false")
}

/**
 * 是否為 [TESTING_PROJECT_PATH] 或其底下的測試輔助模組。
 *
 * @return 是測試輔助模組時為 true。
 */
private fun Project.isTestingSupportProject(): Boolean = path == TESTING_PROJECT_PATH || path.startsWith("$TESTING_PROJECT_PATH:")
