package com.doublemoon1119.mahjongcraft.buildlogic

import org.gradle.api.GradleException
import org.gradle.api.Project
import org.gradle.api.provider.Provider
import org.gradle.api.services.BuildService
import org.gradle.api.services.BuildServiceParameters
import org.gradle.api.tasks.JavaExec
import org.gradle.api.tasks.TaskProvider

/** 限制同時執行的自動測試遊戲數量。本身不保存狀態，只讓 Gradle 排隊。 */
abstract class AutotestSlotsService : BuildService<BuildServiceParameters.None>

/**
 * 接上用戶端自動測試。
 *
 * - 開發用戶端啟動前，由 [PrepareAutotestClientTask] 寫好啟動時就要生效的選項。
 * - 遵守同時執行數的限制。
 * - 登記任務 [AUTOTEST_CLIENT_TASK]：執行開發用戶端但不判定結果，並記下報告與紀錄檔的位置，由根專案的同名總任務與
 *   總任務 `autotest` 彙整。
 * - 建置中有這個模組的範例模組時不登記 [AUTOTEST_CLIENT_TASK]，總任務只跑範例的用戶端：範例也載入本體、執行同樣的步驟，
 *   再多測擴充的內容。
 * - 這個模組的開發用戶端仍可單獨執行自動測試。
 * - 沒有設定 `mahjongcraftAutotest` 時不做任何事。
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
    if (rootProject.findProject(exampleProjectPath(name)) != null) return
    val label = "$name client"
    val runDirectory = layout.projectDirectory.dir(clientRunDirectory())
    val reportRoot = runDirectory.dir("autotest").asFile.absolutePath
    val logRoot = runDirectory.dir(RUN_LOG_FOLDER).asFile.absolutePath
    val client = tasks.register(AUTOTEST_CLIENT_TASK, AutotestTargetTask::class.java) {
        group = "verification"
        description = "Runs the automated client test: opens a world, goes through each step and writes a report."
        dependsOn(clientTask)
        reportRoots.put(label, reportRoot)
        diagnosticRoots.put(label, logRoot)
    }
    autotestTarget().configure {
        dependsOn(client)
        reportRoots.put(label, reportRoot)
        diagnosticRoots.put(label, logRoot)
    }
}

/**
 * 接上伺服器自動測試的任務 [AUTOTEST_SERVER_TASK]。
 *
 * - 伺服器自動測試不需要 `mahjongcraftAutotest`，兩段都使用專用的執行任務與執行目錄 [AUTOTEST_SERVER_RUN_DIRECTORY]，
 *   平常的開發伺服器不受影響。
 * - 先執行第一段，再執行讀回世界的第二段。第一段開機前，由 [PrepareAutotestServerTask] 準備執行目錄。
 * - 兩段都遵守同時執行數的限制。
 * - 兩段伺服器以非零代碼結束時 [AUTOTEST_SERVER_TASK] 仍然成功，通過與否由根專案的總任務依報告判定。
 * - 報告與紀錄檔的位置登記在 [AUTOTEST_SERVER_TASK]，由根專案的同名總任務彙整。設定了 `mahjongcraftAutotest` 時也登記給
 *   總任務 `autotest`。
 * - 連接埠：Gradle 屬性 `mahjongcraftAutotestServerPort` 指定起始值，預設 25599。每個載入器模組再加上所屬 target 在整份
 *   catalog 中的位置（[targetIndex]），一起執行的伺服器不會搶同一個連接埠。
 *
 * @param startTask 第一段伺服器的執行任務名稱。
 * @param resumeTask 第二段伺服器的執行任務名稱。
 * @throws GradleException `mahjongcraftAutotestServerPort` 不是有效的連接埠，或模組不只屬於一個 target 時拋出。
 */
fun Project.registerAutotestServerTasks(startTask: String, resumeTask: String) {
    val basePort = providers.gradleProperty("mahjongcraftAutotestServerPort").orNull?.let { value ->
        value.toIntOrNull()?.takeIf { it in 1..MAX_PORT } ?: throw GradleException("mahjongcraftAutotestServerPort must be a port number: $value")
    } ?: DEFAULT_AUTOTEST_SERVER_PORT
    val port = basePort + targetIndex()
    if (port > MAX_PORT) throw GradleException("The automated server test port $port is above $MAX_PORT")
    val runDirectory = layout.projectDirectory.dir(AUTOTEST_SERVER_RUN_DIRECTORY)
    val prepare = tasks.register("prepareAutotestServer", PrepareAutotestServerTask::class.java) {
        group = "verification"
        description = "Prepares the automated server test's run directory."
        this.runDirectory.set(runDirectory)
        this.port.set(port)
    }
    tasks.named(startTask) { dependsOn(prepare) }
    tasks.named(resumeTask) { mustRunAfter(startTask) }
    limitAutotestRun(startTask)
    limitAutotestRun(resumeTask)
    val label = "$name server"
    val reportRoot = runDirectory.dir("autotest").asFile.absolutePath
    val logRoot = runDirectory.dir(RUN_LOG_FOLDER).asFile.absolutePath
    val server = tasks.register(AUTOTEST_SERVER_TASK, AutotestTargetTask::class.java) {
        group = "verification"
        description = "Runs the automated server test: plays at tables, restarts the server and checks what was restored."
        dependsOn(startTask, resumeTask)
        reportRoots.put(label, reportRoot)
        diagnosticRoots.put(label, logRoot)
    }
    if (autotestSystemProperties().isEmpty()) return
    autotestTarget().configure {
        dependsOn(server)
        reportRoots.put(label, reportRoot)
        diagnosticRoots.put(label, logRoot)
    }
}

/**
 * 讓一個遊戲執行任務排進自動測試。
 *
 * - 遵守同時執行數的限制，並在總任務開始之後才執行。
 * - 遊戲以非零代碼結束時不中斷建置：總報告記為沒有報告，其他目標照常執行。
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
 * 這個模組彙整全部自動測試的任務 [AUTOTEST_TARGET_TASK]，沒有時建立。
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
 * 整個建置共用的同時執行數限制。
 *
 * - 數量由 Gradle 屬性 `mahjongcraftAutotestParallel` 指定，預設 2。
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

/**
 * 載入器模組對應的範例模組專案路徑，與 target catalog 對範例模組的命名相同，例如 `minecraft_v1.21.1_fabric` 對應
 * `:example-minecraft_v1.21.1_fabric`。
 *
 * @param projectName 載入器模組的專案名稱。
 * @return 範例模組的專案路徑。
 */
internal fun exampleProjectPath(projectName: String): String = ":example-$projectName"

/** 每個模組彙整全部自動測試用的任務名稱。 */
internal const val AUTOTEST_TARGET_TASK: String = "autotestTarget"

/** 用戶端自動測試的任務名稱。模組的任務執行開發用戶端但不判定結果，根專案的同名總任務依報告判定。 */
internal const val AUTOTEST_CLIENT_TASK: String = "autotestClient"

/** 伺服器自動測試的任務名稱。載入器模組的任務執行兩段伺服器但不判定結果，根專案的同名總任務依報告判定。 */
internal const val AUTOTEST_SERVER_TASK: String = "autotestServer"

/** 總任務開始時記下開始時間的根專案任務名稱。 */
internal const val AUTOTEST_START_TASK: String = "autotestStart"

/** 遊戲在執行目錄中寫紀錄檔的資料夾名稱。 */
private const val RUN_LOG_FOLDER = "logs"

/** 伺服器自動測試預設的起始連接埠，避開平常開發伺服器使用的 25565。 */
private const val DEFAULT_AUTOTEST_SERVER_PORT = 25599

/** 最大的連接埠號碼。 */
private const val MAX_PORT = 65535

/** 預設同時執行的遊戲數量。 */
private const val DEFAULT_AUTOTEST_PARALLEL = 2
