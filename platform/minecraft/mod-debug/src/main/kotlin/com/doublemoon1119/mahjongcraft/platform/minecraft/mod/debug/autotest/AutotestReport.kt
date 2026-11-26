package com.doublemoon1119.mahjongcraft.platform.minecraft.mod.debug.autotest

import kotlinx.serialization.Serializable

/**
 * 一次自動測試的報告。
 *
 * @property target 測試的目標，例如 Minecraft 版本與載入器。
 * @property startedAt 開始時間，ISO 8601 格式。
 * @property durationMillis 總耗時（毫秒）。
 * @property status 整體結果：任何步驟失敗或中止時不是通過。
 * @property notes 執行測試的人需要知道的事，例如測試代為同意了 Minecraft EULA。沒有時為空。
 * @property steps 各步驟的結果，依執行順序。
 */
@Serializable
internal data class AutotestReport(
    val target: String,
    val startedAt: String,
    val durationMillis: Long,
    val status: AutotestStatus,
    val notes: List<String>,
    val steps: List<AutotestStepResult>,
)

/**
 * 一個步驟的結果。
 *
 * @property id 步驟識別碼。
 * @property status 步驟結果。
 * @property durationMillis 耗時（毫秒）。
 * @property checks 步驟中的各項檢查。
 * @property screenshots 截圖檔名，相對於報告所在資料夾。
 * @property sounds 步驟期間被攔下的聲音播放要求。
 * @property error 使步驟中斷的錯誤與堆疊。沒有時為 null。
 */
@Serializable
internal data class AutotestStepResult(
    val id: String,
    val status: AutotestStatus,
    val durationMillis: Long,
    val checks: List<AutotestCheck>,
    val screenshots: List<String>,
    val sounds: List<AutotestSoundRecord>,
    val error: String?,
)

/**
 * 一項檢查。
 *
 * @property name 檢查內容。
 * @property passed 是否通過。
 * @property detail 補充說明，例如失敗的原因。沒有時為 null。
 */
@Serializable
internal data class AutotestCheck(val name: String, val passed: Boolean, val detail: String?)

/**
 * 報告中的一次聲音播放要求。
 *
 * @property soundId 聲音事件的識別碼。
 * @property atMillis 自動測試開始後多久發生（毫秒）。
 */
@Serializable
internal data class AutotestSoundRecord(val soundId: String, val atMillis: Long)
