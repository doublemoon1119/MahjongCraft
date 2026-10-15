package com.doublemoon1119.mahjongcraft.platform.fabric.server.persistence

/**
 * 一份無法讀取的麻將資料。
 *
 * @property source 資料來源。
 * @property file 相對於世界存檔目錄的檔案路徑。
 * @property cause 讀取失敗的原因分類。
 */
data class MahjongDataProblem(
    val source: MahjongDataSource,
    val file: String,
    val cause: MahjongDataLoadCause,
)
