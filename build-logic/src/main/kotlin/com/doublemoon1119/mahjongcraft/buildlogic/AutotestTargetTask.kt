package com.doublemoon1119.mahjongcraft.buildlogic

import org.gradle.api.DefaultTask
import org.gradle.api.provider.MapProperty
import org.gradle.api.tasks.Internal
import org.gradle.work.DisableCachingByDefault

/**
 * 一個模組的自動測試：依賴這個模組要執行的用戶端與伺服器自動測試，並記下各自報告的位置給總任務 `autotest` 彙整。
 *
 * @property reportRoots 報告名稱對報告根資料夾（`autotest/`，裡面有 `latest.txt`）的絕對路徑。
 */
@DisableCachingByDefault(because = "Only groups the module's automated test runs")
abstract class AutotestTargetTask : DefaultTask() {
    @get:Internal
    abstract val reportRoots: MapProperty<String, String>
}
