package com.doublemoon1119.mahjongcraft.platform.fabric.server.history

import java.nio.file.Path
import java.sql.DriverManager

/**
 * 以正式存放格式（[HistoryReplayPayloadCodec] 壓縮）直接寫入一筆 `history_replay` 列。
 *
 * @param path 歷史資料庫檔案位置。
 * @param matchId 對局 ID。
 * @param json Replay JSON 文字。
 * @param createdAtEpochMillis 封存時間。
 */
internal fun insertStoredReplay(path: Path, matchId: String, json: String, createdAtEpochMillis: Long = 1L) {
    DriverManager.getConnection("jdbc:sqlite:$path").use { connection ->
        connection.prepareStatement(
            "INSERT INTO history_replay(match_id, format_version, created_at_epoch_millis, payload) VALUES (?, 1, ?, ?)",
        ).use { statement ->
            statement.setString(1, matchId)
            statement.setLong(2, createdAtEpochMillis)
            statement.setBytes(3, HistoryReplayPayloadCodec.compress(json))
            statement.executeUpdate()
        }
    }
}

/**
 * 直接讀出一筆 `history_replay` 列並解壓成 JSON 文字。
 *
 * @param path 歷史資料庫檔案位置。
 * @param matchId 對局 ID。
 * @return 解壓後的 Replay JSON 文字。
 */
internal fun readStoredReplay(path: Path, matchId: String): String {
    val stored = DriverManager.getConnection("jdbc:sqlite:$path").use { connection ->
        connection.prepareStatement("SELECT payload FROM history_replay WHERE match_id = ?").use { statement ->
            statement.setString(1, matchId)
            statement.executeQuery().use { rows ->
                check(rows.next()) { "History replay row is missing" }
                rows.getBytes(1)
            }
        }
    }
    val read = HistoryReplayPayloadCodec.decompress(stored, HISTORY_REPLAY_QUERY_BYTES)
    return checkNotNull(read as? HistoryReplayPayloadRead.Found) { "History replay row cannot be restored: $read" }.payload
}
