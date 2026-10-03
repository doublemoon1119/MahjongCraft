package com.doublemoon1119.mahjongcraft.platform.fabric.server.history

import com.doublemoon1119.mahjongcraft.flow.common.game.history.query.HistoryQueryAccess
import com.doublemoon1119.mahjongcraft.flow.common.game.history.query.HistoryQueryErrorCode
import com.doublemoon1119.mahjongcraft.flow.common.game.history.query.HistoryQueryPolicy
import com.doublemoon1119.mahjongcraft.flow.common.game.history.query.HistoryQueryResult
import com.doublemoon1119.mahjongcraft.flow.common.game.history.query.HistoryQueryScope
import com.doublemoon1119.mahjongcraft.flow.common.game.history.replay.HistoryReplayIdentity
import com.doublemoon1119.mahjongcraft.flow.common.game.history.replay.HistoryReplayPlayerIdentity
import com.doublemoon1119.mahjongcraft.flow.persistence.format.history.replay.ReplayReadDiagnostics
import com.doublemoon1119.mahjongcraft.flow.persistence.format.history.replay.ReplayReadError
import com.doublemoon1119.mahjongcraft.flow.persistence.format.history.replay.ReplayReadResult
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.JsonObject
import java.nio.file.Path
import java.sql.DriverManager
import kotlin.io.path.createTempDirectory
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue
import kotlin.uuid.Uuid

/** 驗證歷史單局讀取器的授權、終端證據與 Replay 身分一致性。 */
class HistoryRoundQueryReaderTest {
    /** 解析與投影取消均須傳遞，不能轉為資料不可用的成功回覆。 */
    @Test
    fun `parse and reader cancellation propagate`() = runTest {
        val database = openDatabase()
        val fixture = insertCompletedMatch(database.path)
        assertFailsWith<CancellationException> {
            read(database, fixture.matchId, parse = { throw CancellationException("Cancelled during replay parsing") })
        }
        assertFailsWith<CancellationException> {
            read(database, fixture.matchId, read = { throw CancellationException("Cancelled during replay projection") })
        }
    }

    /** 驗證已完成且參與者一致的 OWN 查詢會讀取指定內容。 */
    @Test
    fun `authorized own query reads a matching replay`() = runTest {
        val database = openDatabase()
        val fixture = insertCompletedMatch(database.path)
        var parsed = false
        val result = read(
            database = database,
            matchId = fixture.matchId,
            parse = {
                parsed = true
                successDocument()
            },
            read = { success(TestReplay(fixture.matchId, fixture.tableId, fixture.playerId)) },
            identity = TestReplay::identity,
        )

        assertTrue(parsed)
        assertIs<HistoryQueryResult.Success<TestReplay>>(result)
        assertEquals(fixture.matchId, result.value.matchId)
    }

    /** 驗證停用政策、ALL 非管理員、活動排除與 tombstone 都不會讀取 Replay。 */
    @Test
    fun `authorization and publication checks happen before payload parsing`() = runTest {
        val database = openDatabase()
        val fixture = insertCompletedMatch(database.path)
        var parseCount = 0

        assertFailure(
            read(database, fixture.matchId, policy = HistoryQueryPolicy(queryEnabled = false), parse = {
                parseCount++
                successDocument()
            }),
            HistoryQueryErrorCode.QUERY_DISABLED,
        )
        assertFailure(
            read(database, fixture.matchId, scope = HistoryQueryScope.ALL, access = access(fixture.playerId, false), parse = {
                parseCount++
                successDocument()
            }),
            HistoryQueryErrorCode.ACCESS_DENIED,
        )
        assertFailure(
            read(database, fixture.matchId, excluded = setOf(fixture.matchId.toString()), parse = {
                parseCount++
                successDocument()
            }),
            HistoryQueryErrorCode.NOT_AVAILABLE,
        )
        insertTombstone(database.path, fixture.matchId)
        assertFailure(
            read(database, fixture.matchId, parse = {
                parseCount++
                successDocument()
            }),
            HistoryQueryErrorCode.NOT_AVAILABLE,
        )
        assertEquals(0, parseCount)
    }

    /** 驗證非參與者不能以 OWN 範圍讀取已保存對局，管理員可在政策允許時讀取 ALL。 */
    @Test
    fun `participant and administrator scope checks are enforced`() = runTest {
        val database = openDatabase()
        val fixture = insertCompletedMatch(database.path)
        val otherPlayer = Uuid.parse("00000000-0000-0000-0000-000000000011")

        assertFailure(
            read(database, fixture.matchId, access = access(otherPlayer)),
            HistoryQueryErrorCode.NOT_AVAILABLE,
        )
        assertIs<HistoryQueryResult.Success<TestReplay>>(
            read(database, fixture.matchId, scope = HistoryQueryScope.ALL, access = access(otherPlayer, true)),
        )
        assertFailure(
            read(
                database,
                fixture.matchId,
                scope = HistoryQueryScope.ALL,
                access = access(otherPlayer, true),
                policy = HistoryQueryPolicy(allowAdministratorQuery = false),
            ),
            HistoryQueryErrorCode.ACCESS_DENIED,
        )
    }

    /** 驗證 gap 或未完成終端證據會阻止公開已保存 Replay。 */
    @Test
    fun `failed terminal evidence is not published`() = runTest {
        val database = openDatabase()
        val fixture = insertCompletedMatch(database.path)
        insertGap(database.path, fixture.matchId)

        val result = read(database, fixture.matchId, parse = { successDocument() })

        assertFailure(result, HistoryQueryErrorCode.NOT_AVAILABLE)
    }

    /** 驗證 Replay 超過 SQL 位元組上限時不會載入或解析完整文字。 */
    @Test
    fun `oversized replay is rejected before parsing`() = runTest {
        val database = openDatabase()
        val fixture = insertCompletedMatch(database.path, payload = "x".repeat(HISTORY_REPLAY_QUERY_BYTES + 1))
        var parsed = false

        val result = read(
            database = database,
            matchId = fixture.matchId,
            parse = {
                parsed = true
                successDocument()
            },
        )

        assertFailure(result, HistoryQueryErrorCode.CONTENT_TOO_LARGE)
        assertFalse(parsed)
    }

    /** 驗證 JSON 解碼失敗會轉為不洩漏內容的安全錯誤。 */
    @Test
    fun `invalid replay document maps to unavailable`() = runTest {
        val database = openDatabase()
        val fixture = insertCompletedMatch(database.path)

        val result = read(
            database = database,
            matchId = fixture.matchId,
            parse = { ReplayReadResult.Failure(ReplayReadError.INVALID_DOCUMENT) },
        )

        assertFailure(result, HistoryQueryErrorCode.NOT_AVAILABLE)
    }

    /** 驗證指定交易不存在時會回傳無效請求，而不是公開部分資料。 */
    @Test
    fun `missing selected transaction maps to invalid request`() = runTest {
        val database = openDatabase()
        val fixture = insertCompletedMatch(database.path)

        val result = read(
            database = database,
            matchId = fixture.matchId,
            read = { ReplayReadResult.Failure(ReplayReadError.SELECTION_NOT_FOUND) },
        )

        assertFailure(result, HistoryQueryErrorCode.INVALID_REQUEST)
    }

    /** 驗證 Replay 的牌桌或玩家身分與封存 metadata 不一致時不會公開內容。 */
    @Test
    fun `mismatched replay identity maps to unavailable`() = runTest {
        val database = openDatabase()
        val fixture = insertCompletedMatch(database.path)
        val wrongTable = TestReplay(fixture.matchId, Uuid.parse("00000000-0000-0000-0000-000000000099"), fixture.playerId)

        val result = read(
            database = database,
            matchId = fixture.matchId,
            read = { success(wrongTable) },
            identity = TestReplay::identity,
        )

        assertFailure(result, HistoryQueryErrorCode.NOT_AVAILABLE)

        val wrongPlayer = TestReplay(
            fixture.matchId,
            fixture.tableId,
            Uuid.parse("00000000-0000-0000-0000-000000000099"),
        )
        assertFailure(
            read(
                database = database,
                matchId = fixture.matchId,
                read = { success(wrongPlayer) },
                identity = TestReplay::identity,
            ),
            HistoryQueryErrorCode.NOT_AVAILABLE,
        )
    }

    /** 執行單局讀取測試的共用入口。
     *
     * @param database 測試用 SQLite 歷史資料庫。
     * @param matchId 欲查詢的對局識別碼。
     * @param access 測試查詢身分。
     * @param policy 測試查詢政策。
     * @param scope 測試查詢範圍。
     * @param excluded 模擬活動或轉移中的對局集合。
     * @param parse 測試 JSON 解析回呼。
     * @param read 測試 Replay 讀取回呼。
     * @param identity 測試讀模型身分回呼。
     * @return 歷史查詢結果。
     */
    private suspend fun read(
        database: SqliteHistoryDatabase,
        matchId: Uuid,
        access: HistoryQueryAccess = access(PLAYER_ID),
        policy: HistoryQueryPolicy = HistoryQueryPolicy(),
        scope: HistoryQueryScope = HistoryQueryScope.OWN,
        excluded: Set<String> = emptySet(),
        parse: suspend (String) -> ReplayReadResult<JsonObject> = { successDocument() },
        read: suspend (JsonObject) -> ReplayReadResult<TestReplay> = {
            success(TestReplay(matchId, TABLE_ID, PLAYER_ID))
        },
        identity: (TestReplay) -> HistoryReplayIdentity = TestReplay::identity,
    ): HistoryQueryResult<TestReplay> = readAuthorizedHistoryRound(
        database = database,
        access = access,
        policy = policy,
        matchId = matchId,
        scope = scope,
        excluded = excluded,
        parse = parse,
        read = read,
        identity = identity,
    )

    /** 建立新的 SQLite 歷史資料庫。 */
    private fun openDatabase(): SqliteHistoryDatabase = SqliteHistoryDatabase.open(createTempDirectory("mahjongcraft-round-reader-").resolve("history.sqlite"))

    /** 寫入可供查詢的完整對局摘要與 Replay。
     *
     * @param path 資料庫檔案位置。
     * @param payload Replay payload。
     * @return 封存身分資料。
     */
    private fun insertCompletedMatch(
        path: Path,
        payload: String = "{}",
    ): MatchFixture {
        val matchId = Uuid.parse("00000000-0000-0000-0000-000000000001")
        sql(
            path,
            "INSERT INTO history_match(match_id, table_id, rule_id, status, started_at_epoch_millis, ended_at_epoch_millis) VALUES (?, ?, ?, 'COMPLETED', 1, 2)",
            matchId,
            TABLE_ID,
            "mahjongcraft:riichi",
        )
        sql(path, "INSERT INTO history_replay(match_id, format_version, created_at_epoch_millis, payload) VALUES (?, 1, 1, ?)", matchId, payload)
        sql(path, "INSERT INTO history_participant(match_id, seat_index, player_id, ai_strategy_id) VALUES (?, 0, ?, NULL)", matchId, PLAYER_ID)
        return MatchFixture(matchId, TABLE_ID, PLAYER_ID)
    }

    /** 寫入清理墓碑。
     *
     * @param path 資料庫檔案位置。
     * @param matchId 對局識別碼。
     */
    private fun insertTombstone(path: Path, matchId: Uuid) = sql(path, "INSERT INTO history_tombstone(match_id, pruned_at_epoch_millis, reason) VALUES (?, 1, 'test')", matchId)

    /** 寫入事件序號缺口。
     *
     * @param path 資料庫檔案位置。
     * @param matchId 對局識別碼。
     */
    private fun insertGap(path: Path, matchId: Uuid) = sql(path, "INSERT INTO history_gap(match_id, first_missing_sequence) VALUES (?, 2)", matchId)

    /** 建立測試查詢身分。
     *
     * @param playerId 玩家識別碼。
     * @param administrator 是否具有管理員權限。
     * @return 查詢身分。
     */
    private fun access(playerId: Uuid, administrator: Boolean = false) = HistoryQueryAccess(playerId, administrator)

    /** 建立空的成功 JSON 文件。 */
    private fun successDocument(): ReplayReadResult<JsonObject> = success(JsonObject(emptyMap()))

    /** 建立帶有明確資源統計的成功 Replay 結果。
     *
     * @param value Replay 讀取值。
     * @return 成功結果。
     */
    private fun <T> success(value: T) = ReplayReadResult.Success(
        value = value,
        diagnostics = ReplayReadDiagnostics(0, 0, 0, 0),
    )

    /** 驗證結果為指定錯誤。
     *
     * @param result 查詢結果。
     * @param code 預期錯誤碼。
     */
    private fun <T> assertFailure(result: HistoryQueryResult<T>, code: HistoryQueryErrorCode) {
        val failure = assertIs<HistoryQueryResult.Failure>(result)
        assertEquals(code, failure.error.code)
    }

    /** 執行帶參數 SQL。
     *
     * @param path 資料庫檔案位置。
     * @param statement SQL 敘述。
     * @param parameters SQL 參數。
     */
    private fun sql(path: Path, statement: String, vararg parameters: Any?) {
        DriverManager.getConnection("jdbc:sqlite:$path").use { connection ->
            connection.prepareStatement(statement).use { prepared ->
                parameters.forEachIndexed { index, value -> prepared.setObject(index + 1, value.toString()) }
                prepared.executeUpdate()
            }
        }
    }

    /** 測試用封存身分資料。
     *
     * @property matchId 對局識別碼。
     * @property tableId 牌桌識別碼。
     * @property playerId 參與者識別碼。
     */
    private data class MatchFixture(
        val matchId: Uuid,
        val tableId: Uuid,
        val playerId: Uuid,
    )

    /** 測試用最小 Replay 讀模型。
     *
     * @property matchId Replay 對局識別碼。
     * @property tableId Replay 牌桌識別碼。
     * @property playerId Replay 參與者識別碼。
     */
    private data class TestReplay(
        val matchId: Uuid,
        val tableId: Uuid,
        val playerId: Uuid,
    ) {
        /** 取得讀模型公開身分。 */
        fun identity() = HistoryReplayIdentity(
            matchId = matchId,
            tableId = tableId,
            players = listOf(HistoryReplayPlayerIdentity(0, playerId, null)),
        )
    }

    /** 測試用識別碼常數。 */
    private companion object {
        val PLAYER_ID: Uuid = Uuid.parse("00000000-0000-0000-0000-000000000010")
        val TABLE_ID: Uuid = Uuid.parse("00000000-0000-0000-0000-000000000020")
    }
}
