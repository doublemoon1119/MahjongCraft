package com.doublemoon1119.mahjongcraft.buildlogic

import org.gradle.api.DefaultTask
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.tasks.Internal
import org.gradle.api.tasks.TaskAction
import org.gradle.work.DisableCachingByDefault
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

/** 總任務開始時記下開始時間，總報告只採用這個時間之後開始的報告。 */
@DisableCachingByDefault(because = "Records the current time")
abstract class AutotestStartTask : DefaultTask() {
    /** 記下開始時間的檔案。 */
    @get:Internal
    abstract val startFile: RegularFileProperty

    /** 以報告資料夾名稱相同的格式記下目前時間。 */
    @TaskAction
    fun record() {
        val file = startFile.get().asFile
        file.parentFile.mkdirs()
        file.writeText(LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd_HH-mm-ss")))
    }
}
