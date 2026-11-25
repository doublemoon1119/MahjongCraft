package com.doublemoon1119.mahjongcraft.buildlogic

import org.gradle.api.DefaultTask
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.tasks.Internal
import org.gradle.api.tasks.TaskAction
import org.gradle.work.DisableCachingByDefault

/**
 * 用戶端自動測試啟動前，在測試專用的執行目錄寫入啟動時就要生效的遊戲選項：已看過無障礙設定引導、關閉旁白。
 *
 * 原版第一次啟動時顯示無障礙設定引導，並以系統的語音合成朗讀介紹；這個聲音不經過遊戲的音訊輸出，自動測試的靜音攔不到。
 * 啟動前寫好選項，引導與朗讀都不會出現。其他選項保留上次的值，由自動測試在遊戲中設定；平常的 `run/client` 不受影響。
 *
 * @property runDirectory 用戶端自動測試的執行目錄。
 */
@DisableCachingByDefault(because = "Rewrites a local run directory's options")
abstract class PrepareAutotestClientTask : DefaultTask() {
    @get:Internal
    abstract val runDirectory: DirectoryProperty

    /** 寫入啟動時就要生效的選項，保留其他選項。 */
    @TaskAction
    fun prepare() {
        val directory = runDirectory.get().asFile
        directory.mkdirs()
        val options = directory.resolve(OPTIONS_FILE)
        val kept = if (options.isFile) options.readLines().filter { it.substringBefore(':') !in STARTUP_OPTIONS } else emptyList()
        options.writeText((kept + STARTUP_OPTIONS.map { (key, value) -> "$key:$value" }).joinToString(separator = "\n", postfix = "\n"))
    }

    /** 選項檔與固定的選項。 */
    companion object {
        /** 原版的選項檔名稱。 */
        const val OPTIONS_FILE: String = "options.txt"

        /** 啟動時就要生效的選項：已看過無障礙設定引導，旁白關閉。 */
        val STARTUP_OPTIONS: Map<String, String> = linkedMapOf(
            "onboardAccessibility" to "false",
            "narrator" to "0",
        )
    }
}
