package com.doublemoon1119.mahjongcraft.buildlogic

import org.gradle.api.GradleException
import org.gradle.api.Project

/**
 * 接上宣傳截圖模式。
 *
 * - 開發用戶端啟動前，以 [PrepareAutotestClientTask] 寫好啟動時就要生效的選項，與自動測試相同。
 * - 宣傳截圖與自動測試使用不同的世界與執行目錄，不能在同一次建置中一起開啟。
 * - 沒有設定 `mahjongcraftShowcase` 時不做任何事。
 *
 * @param clientTask 開發用戶端的執行任務名稱。
 * @throws GradleException 同時設定了 `mahjongcraftAutotest` 時拋出。
 */
fun Project.registerShowcaseClientTasks(clientTask: String) {
    if (!showcaseEnabled()) return
    if (autotestSystemProperties().isNotEmpty()) {
        throw GradleException("mahjongcraftShowcase and mahjongcraftAutotest cannot be used in the same build")
    }
    val prepare = tasks.register("prepareShowcaseClient", PrepareAutotestClientTask::class.java) {
        group = "development"
        description = "Writes the options the showcase client needs from startup."
        runDirectory.set(layout.projectDirectory.dir(clientRunDirectory()))
    }
    tasks.named(clientTask) { dependsOn(prepare) }
}
