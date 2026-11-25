package com.doublemoon1119.mahjongcraft.buildlogic

import org.gradle.api.GradleException
import org.gradle.api.Project
import org.gradle.api.provider.Provider
import org.gradle.api.services.BuildService
import org.gradle.api.services.BuildServiceParameters
import org.gradle.api.tasks.JavaExec
import org.gradle.api.tasks.TaskProvider

/** 限制同時執行的自動測試遊戲數量；本身不保存任何狀態，只用來讓 Gradle 排隊。 */
abstract class AutotestSlotsService : BuildService<BuildServiceParameters.None>

/**
 * 接上用戶端自動測試：開發用戶端啟動前由 [PrepareAutotestClientTask] 寫好啟動時就要生效的選項，遵守同時執行數的限制，並
 * 登記報告的位置給總任務 `autotest` 彙整。沒有設定 `mahjongcraftAutotest` 時不做任何事。
 *
 * @param clientTask 開發用戶端的執行任務名稱。
 */
fun Project.registerAutotestClientTasks(clientTask: String) {
    if (autotestSystemProperties().isEmpty()) return
    val prepare = tasks.register("prepareAutotestClient", PrepareAutotestClientTask::class.java) {
        group = "verification"
        description = "Writes the options the automated client test needs from startup."
        runDirectory.set(layout.projectDirectory.dir(clientRunDirectory()))
    }
    tasks.named(clientTask) { dependsOn(prepare) }
    limitAutotestRun(clientTask)
    val module = name
    autotestTarget().configure {
        dependsOn(clientTask)
        reportRoots.put("$module client", layout.projectDirectory.dir(clientRunDirectory()).dir("autotest").asFile.absolutePath)
    }
}

/**
 * 接上伺服器自動測試的任務：`autotestServer` 先執行一般的開發伺服器（第一段），再執行讀回世界的第二段伺服器；第一段開機前由
 * [PrepareAutotestServerTask] 準備測試專用的執行目錄。兩段都遵守同時執行數的限制，報告的位置登記給總任務 `autotest` 彙整。
 * 沒有設定 `mahjongcraftAutotest` 時不做任何事。
 *
 * Gradle 屬性 `mahjongcraftAutotestServerPort` 指定起始連接埠，沒有指定時為 25599；每個載入器模組使用起始連接埠加上所屬
 * target 在整份 catalog 中的位置（[targetIndex]），一起執行的伺服器不會搶同一個連接埠。
 *
 * @param startTask 第一段，也就是一般開發伺服器的執行任務名稱。
 * @param resumeTask 第二段伺服器的執行任務名稱。
 * @throws GradleException `mahjongcraftAutotestServerPort` 不是有效的連接埠，或模組不只屬於一個 target 時拋出。
 */
fun Project.registerAutotestServerTasks(startTask: String, resumeTask: String) {
    if (autotestSystemProperties().isEmpty()) return
    val basePort = providers.gradleProperty("mahjongcraftAutotestServerPort").orNull?.let { value ->
        value.toIntOrNull()?.takeIf { it in 1..MAX_PORT } ?: throw GradleException("mahjongcraftAutotestServerPort must be a port number: $value")
    } ?: DEFAULT_AUTOTEST_SERVER_PORT
    val port = basePort + targetIndex()
    if (port > MAX_PORT) throw GradleException("The automated server test port $port is above $MAX_PORT")
    val prepare = tasks.register("prepareAutotestServer", PrepareAutotestServerTask::class.java) {
        group = "verification"
        description = "Prepares the automated server test's run directory."
        runDirectory.set(layout.projectDirectory.dir(serverRunDirectory()))
        this.port.set(port)
    }
    tasks.named(startTask) { dependsOn(prepare) }
    tasks.named(resumeTask) { mustRunAfter(startTask) }
    limitAutotestRun(startTask)
    limitAutotestRun(resumeTask)
    val server = tasks.register("autotestServer") {
        group = "verification"
        description = "Runs the automated server test: plays at tables, restarts the server and checks what was restored."
        dependsOn(startTask, resumeTask)
    }
    val module = name
    autotestTarget().configure {
        dependsOn(server)
        reportRoots.put("$module server", layout.projectDirectory.dir(serverRunDirectory()).dir("autotest").asFile.absolutePath)
    }
}

/**
 * 讓一個遊戲執行任務遵守同時執行數的限制，並在總任務開始之後才執行；遊戲以非零代碼結束時不中斷建置，由總報告記為沒有報告，
 * 其他目標照常執行。
 *
 * @param taskName 遊戲執行任務名稱。
 */
private fun Project.limitAutotestRun(taskName: String) {
    val slots = autotestSlots()
    tasks.named(taskName) {
        usesService(slots)
        mustRunAfter(":$AUTOTEST_START_TASK")
        (this as? JavaExec)?.isIgnoreExitValue = true
    }
}

/**
 * 這個模組彙整用的自動測試任務，沒有時建立。
 *
 * @return 任務。
 */
private fun Project.autotestTarget(): TaskProvider<AutotestTargetTask> = if (AUTOTEST_TARGET_TASK in tasks.names) {
    tasks.named(AUTOTEST_TARGET_TASK, AutotestTargetTask::class.java)
} else {
    tasks.register(AUTOTEST_TARGET_TASK, AutotestTargetTask::class.java) {
        group = "verification"
        description = "Runs this module's automated tests."
    }
}

/**
 * 整個建置共用的同時執行數限制。Gradle 屬性 `mahjongcraftAutotestParallel` 指定同時執行的遊戲數量，沒有指定時為 2。
 *
 * @return 共用的限制服務。
 * @throws GradleException `mahjongcraftAutotestParallel` 不是正整數時拋出。
 */
private fun Project.autotestSlots(): Provider<AutotestSlotsService> {
    val parallel = providers.gradleProperty("mahjongcraftAutotestParallel").orNull?.let { value ->
        value.toIntOrNull()?.takeIf { it >= 1 } ?: throw GradleException("mahjongcraftAutotestParallel must be a positive integer: $value")
    } ?: DEFAULT_AUTOTEST_PARALLEL
    return gradle.sharedServices.registerIfAbsent("mahjongcraftAutotestSlots", AutotestSlotsService::class.java) {
        maxParallelUsages.set(parallel)
    }
}

/** 每個模組彙整自動測試用的任務名稱。 */
internal const val AUTOTEST_TARGET_TASK: String = "autotestTarget"

/** 總任務開始時記下開始時間的根專案任務名稱。 */
internal const val AUTOTEST_START_TASK: String = "autotestStart"

/** 伺服器自動測試預設的起始連接埠，避開平常開發伺服器使用的 25565。 */
private const val DEFAULT_AUTOTEST_SERVER_PORT = 25599

/** 最大的連接埠號碼。 */
private const val MAX_PORT = 65535

/** 預設同時執行的遊戲數量。 */
private const val DEFAULT_AUTOTEST_PARALLEL = 2
