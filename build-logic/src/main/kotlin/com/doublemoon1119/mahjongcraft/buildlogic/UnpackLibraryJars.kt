package com.doublemoon1119.mahjongcraft.buildlogic

import org.gradle.api.Transformer
import org.gradle.api.file.ArchiveOperations
import org.gradle.api.file.FileSystemLocation
import org.gradle.api.file.FileTree
import javax.inject.Inject

/**
 * 將程式庫 JAR 轉成可合併的檔案樹，不在任務執行時存取 Gradle script 或 Project。
 *
 * @property archives Gradle 提供的壓縮檔讀取服務。
 */
abstract class UnpackLibraryJars @Inject constructor(
    private val archives: ArchiveOperations,
) : Transformer<List<FileTree>, Set<FileSystemLocation>> {
    /** 將 [locations] 中每個 JAR 的內容交給打包任務，實際讀取仍由檔案樹延後處理。 */
    override fun transform(locations: Set<FileSystemLocation>): List<FileTree> = locations.map { archives.zipTree(it.asFile) }
}
