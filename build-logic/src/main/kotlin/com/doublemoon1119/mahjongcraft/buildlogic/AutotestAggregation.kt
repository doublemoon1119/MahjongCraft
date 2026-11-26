package com.doublemoon1119.mahjongcraft.buildlogic

import org.gradle.api.Project
import org.gradle.api.tasks.TaskProvider

/**
 * 在根專案登記總任務 [AUTOTEST_SERVER_TASK] 與 `autotest`。
 *
 * - [AUTOTEST_SERVER_TASK] 執行所選目標各模組的伺服器自動測試，也就是各模組的同名任務。它不需要 `mahjongcraftAutotest`。
 * - `autotest` 執行所選目標全部模組的自動測試，也就是各模組的 [AUTOTEST_TARGET_TASK]。只有設定了 `mahjongcraftAutotest`
 *   時才登記。
 * - 同時執行的遊戲數量受 `mahjongcraftAutotestParallel` 限制。
 * - 全部結束後由 [AutotestSummaryTask] 寫出總報告。[AUTOTEST_SERVER_TASK] 寫在
 *   `build/reports/autotest-server/<開始時間>/summary.md`，`autotest` 寫在 `build/reports/autotest/<開始時間>/summary.md`。
 *   各份報告與診斷資料收在同一個資料夾。
 * - 所選目標沒有對應的自動測試時，總任務不啟動任何遊戲並成功。
 */
fun Project.registerAutotestAggregate() {
    registerAutotestSummary(
        name = AUTOTEST_SERVER_TASK,
        summary = "Runs every selected target's automated server test and writes a summary.",
        moduleTask = AUTOTEST_SERVER_TASK,
        outputFolder = "reports/autotest-server",
    )
    if (autotestSystemProperties().isEmpty()) return
    registerAutotestSummary(
        name = "autotest",
        summary = "Runs every selected target's automated tests and writes a summary.",
        moduleTask = AUTOTEST_TARGET_TASK,
        outputFolder = "reports/autotest",
    )
}

/**
 * 記下開始時間的根專案任務，沒有時建立。各個總任務共用同一個。
 *
 * @return 任務。
 */
private fun Project.autotestStart(): TaskProvider<AutotestStartTask> = if (AUTOTEST_START_TASK in tasks.names) {
    tasks.named(AUTOTEST_START_TASK, AutotestStartTask::class.java)
} else {
    tasks.register(AUTOTEST_START_TASK, AutotestStartTask::class.java) {
        startFile.set(layout.buildDirectory.file("autotest/start.txt"))
    }
}

/**
 * 登記一個彙整各模組自動測試的總任務。
 *
 * - 總報告只保留最近幾份，份數由 `mahjongcraftAutotestKeep` 指定，預設 10。
 *
 * @param name 總任務名稱。
 * @param summary 總任務的說明。
 * @param moduleTask 各模組中要執行並彙整的 [AutotestTargetTask] 名稱。
 * @param outputFolder 總報告的根資料夾，相對於根專案的建置資料夾。
 */
private fun Project.registerAutotestSummary(
    name: String,
    summary: String,
    moduleTask: String,
    outputFolder: String,
) {
    val start = autotestStart()
    val targets = subprojects.map { project -> project.tasks.withType(AutotestTargetTask::class.java).matching { it.name == moduleTask } }
    val keep = providers.gradleProperty("mahjongcraftAutotestKeep").map(String::toInt).orElse(DEFAULT_SUMMARY_KEEP)
    tasks.register(name, AutotestSummaryTask::class.java) {
        group = "verification"
        description = summary
        dependsOn(start, targets)
        startFile.set(start.flatMap { it.startFile })
        reportRoots.putAll(provider { targets.flatMap { tasks -> tasks.flatMap { it.reportRoots.get().entries } }.associate { it.key to it.value } })
        diagnosticRoots.putAll(
            provider { targets.flatMap { tasks -> tasks.flatMap { it.diagnosticRoots.get().entries } }.associate { it.key to it.value } },
        )
        outputRoot.set(layout.buildDirectory.dir(outputFolder))
        this.keep.set(keep)
    }
}

/** 預設保留的總報告份數，與各目標的報告相同。 */
private const val DEFAULT_SUMMARY_KEEP = 10
