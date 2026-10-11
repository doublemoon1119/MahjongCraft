package com.doublemoon1119.mahjongcraft.platform.fabric.server.game.debug.stress

import java.io.BufferedWriter
import java.nio.file.Files
import java.nio.file.Path

/**
 * 寫進存檔資料夾的每秒時間序列 CSV；每列寫入後立即 flush，伺服器中途結束也保留已寫的部分。
 *
 * @property path 檔案位置。
 * @property scenarioId 對局情境識別碼，寫在每一列。
 * @property pace 推進節奏，寫在每一列。
 * @property historyMode 歷史處理方式，寫在每一列。
 */
class StressTimeSeriesFile private constructor(
    val path: Path,
    private val scenarioId: String,
    private val pace: StressTestPace,
    private val historyMode: StressHistoryMode,
    private val writer: BufferedWriter,
) : AutoCloseable {
    /** 寫入一列。 */
    fun append(row: StressTimeSeriesRow) {
        writer.write(stressTimeSeriesCsvLine(scenarioId, pace, historyMode, row))
        writer.newLine()
        writer.flush()
    }
    override fun close() = writer.close()
    companion object {
        /**
         * 建立新檔並寫入標題列。
         *
         * @param path 檔案位置；上層資料夾不存在時一併建立。
         * @param scenarioId 對局情境識別碼。
         * @param pace 推進節奏。
         * @param historyMode 歷史處理方式。
         */
        fun create(
            path: Path,
            scenarioId: String,
            pace: StressTestPace,
            historyMode: StressHistoryMode,
        ): StressTimeSeriesFile {
            Files.createDirectories(path.parent)
            val writer = Files.newBufferedWriter(path)
            writer.write(STRESS_TIME_SERIES_HEADER)
            writer.newLine()
            writer.flush()
            return StressTimeSeriesFile(path, scenarioId, pace, historyMode, writer)
        }
    }
}
