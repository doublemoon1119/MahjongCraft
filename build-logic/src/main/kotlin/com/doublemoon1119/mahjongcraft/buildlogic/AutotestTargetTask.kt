package com.doublemoon1119.mahjongcraft.buildlogic

import org.gradle.api.DefaultTask
import org.gradle.api.provider.MapProperty
import org.gradle.api.tasks.Internal
import org.gradle.work.DisableCachingByDefault

/**
 * 一個模組的一組自動測試：依賴要執行的遊戲，並記下各自報告的位置給根專案的總任務彙整。
 *
 * - [AUTOTEST_TARGET_TASK]：這個模組全部的自動測試。
 * - [AUTOTEST_SERVER_TASK]：只有伺服器自動測試。
 *
 * @property reportRoots 報告名稱對報告根資料夾（`autotest/`，裡面有 `latest.txt`）的絕對路徑。
 * @property diagnosticRoots 報告名稱對診斷資料夾的絕對路徑，例如遊戲的紀錄檔資料夾。總任務把它和報告收在一起。
 */
@DisableCachingByDefault(because = "Only groups the module's automated test runs")
abstract class AutotestTargetTask : DefaultTask() {
    @get:Internal
    abstract val reportRoots: MapProperty<String, String>

    @get:Internal
    abstract val diagnosticRoots: MapProperty<String, String>
}
