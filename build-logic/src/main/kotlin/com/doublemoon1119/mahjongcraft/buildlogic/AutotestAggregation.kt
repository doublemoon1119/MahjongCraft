package com.doublemoon1119.mahjongcraft.buildlogic

import org.gradle.api.Project

/**
 * 在根專案登記總任務 `autotest`：在同一次建置中執行所選目標全部模組的自動測試（[AutotestTargetTask]），同時執行的遊戲數量受
 * `mahjongcraftAutotestParallel` 限制，全部結束後由 [AutotestSummaryTask] 寫出總報告。沒有設定 `mahjongcraftAutotest` 時不做
 * 任何事。
 *
 * 總報告寫在 `build/reports/autotest/<開始時間>/summary.md`，只保留最近幾份；保留份數與各目標的報告相同，由
 * `mahjongcraftAutotestKeep` 指定，沒有指定時為 10。
 */
fun Project.registerAutotestAggregate() {
    if (autotestSystemProperties().isEmpty()) return
    val start = tasks.register(AUTOTEST_START_TASK, AutotestStartTask::class.java) {
        startFile.set(layout.buildDirectory.file("autotest/start.txt"))
    }
    val targets = subprojects.map { project -> project.tasks.withType(AutotestTargetTask::class.java) }
    val keep = providers.gradleProperty("mahjongcraftAutotestKeep").map(String::toInt).orElse(DEFAULT_SUMMARY_KEEP)
    tasks.register("autotest", AutotestSummaryTask::class.java) {
        group = "verification"
        description = "Runs every selected target's automated tests and writes a summary."
        dependsOn(start, targets)
        startFile.set(start.flatMap { it.startFile })
        reportRoots.putAll(provider { targets.flatMap { tasks -> tasks.flatMap { it.reportRoots.get().entries } }.associate { it.key to it.value } })
        outputRoot.set(layout.buildDirectory.dir("reports/autotest"))
        this.keep.set(keep)
    }
}

/** 預設保留的總報告份數，與各目標的報告相同。 */
private const val DEFAULT_SUMMARY_KEEP = 10
