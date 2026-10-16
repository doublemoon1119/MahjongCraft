package com.doublemoon1119.mahjongcraft.platform.fabric.server.game

import com.doublemoon1119.mahjongcraft.flow.common.error.ExtensionFailurePolicy
import com.doublemoon1119.mahjongcraft.flow.common.game.model.PlayerAutomaticControlSnapshot
import com.doublemoon1119.mahjongcraft.flow.network.dto.message.AutomaticControlUpdateRequestDto
import com.doublemoon1119.mahjongcraft.flow.network.dto.message.AutomaticControlUpdateResultKindDto
import com.doublemoon1119.mahjongcraft.flow.server.game.usecase.AutomaticControlUpdateRejection
import com.doublemoon1119.mahjongcraft.flow.server.game.usecase.AutomaticControlUpdateUnavailableReason
import com.doublemoon1119.mahjongcraft.flow.server.game.usecase.UpdatePlayerAutomaticControlsResult
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.uuid.Uuid

/** [AutomaticControlUpdateService] 使用的 Flow result 至線路 DTO 映射，與套用失敗時的處理測試。 */
class AutomaticControlUpdateServiceTest {
    /** 請求與回覆共用的測試對局識別碼。 */
    private val gameId = Uuid.random()

    /** 預期修訂為 3、要求開啟自動和牌的測試請求。 */
    private val request = AutomaticControlUpdateRequestDto(
        requestId = "request-1",
        gameId = gameId.toString(),
        expectedRevision = 3L,
        enabledControlIds = setOf("mahjongcraft:auto_win"),
    )

    /** 修訂為 4、已開啟自動和牌的權威快照。 */
    private val snapshot = PlayerAutomaticControlSnapshot(
        gameId = gameId,
        revision = 4L,
        supportedControlIds = setOf("mahjongcraft:auto_win"),
        enabledControlIds = setOf("mahjongcraft:auto_win"),
    )

    /** 接受結果應保留配對欄位並攜帶更新後快照。 */
    @Test
    fun `test accepted result preserves request identity and snapshot`() {
        val dto = UpdatePlayerAutomaticControlsResult.Accepted(snapshot, changed = true).toDto(request)

        assertEquals("request-1", dto.requestId)
        assertEquals(gameId.toString(), dto.gameId)
        assertEquals(AutomaticControlUpdateResultKindDto.ACCEPTED, dto.result)
        assertEquals(4L, dto.snapshot?.revision)
    }

    /** 過期結果應帶回目前權威快照。 */
    @Test
    fun `test stale result carries authoritative snapshot`() {
        val dto = UpdatePlayerAutomaticControlsResult.Stale(snapshot).toDto(request)

        assertEquals(AutomaticControlUpdateResultKindDto.STALE, dto.result)
        assertEquals(snapshot.enabledControlIds, dto.snapshot?.enabledControlIds)
    }

    /** 無法確認成員的拒絕不得帶回個人快照。 */
    @Test
    fun `test rejected non member result does not expose snapshot`() {
        val dto = UpdatePlayerAutomaticControlsResult.Rejected(
            AutomaticControlUpdateRejection.PLAYER_NOT_IN_GAME,
        ).toDto(request)

        assertEquals(AutomaticControlUpdateResultKindDto.REJECTED, dto.result)
        assertNull(dto.snapshot)
    }

    /** 不可用結果不得製造不存在的快照。 */
    @Test
    fun `test unavailable result does not contain snapshot`() {
        val dto = UpdatePlayerAutomaticControlsResult.Unavailable(
            AutomaticControlUpdateUnavailableReason.GAME_NOT_FOUND,
        ).toDto(request)

        assertEquals(AutomaticControlUpdateResultKindDto.UNAVAILABLE, dto.result)
        assertNull(dto.snapshot)
    }

    /** 套用時發生可以隔離的失敗時回報並回傳 null，讓呼叫端仍能回覆無法使用。 */
    @Test
    fun `test isolatable update failure is reported as missing result`() = runTest {
        var reported: Throwable? = null

        val result = updateIsolatingFailures(failurePolicy = ExtensionFailurePolicy.Default, onFailure = { reported = it }) {
            error("test failure")
        }

        assertNull(result)
        assertEquals("test failure", reported?.message)
    }

    /** 正常結果原樣回傳；協程取消與無法隔離的失敗原樣往外傳。 */
    @Test
    fun `test normal results pass through and fatal failures are rethrown`() = runTest {
        val accepted = UpdatePlayerAutomaticControlsResult.Accepted(snapshot, changed = false)

        assertEquals(accepted, updateIsolatingFailures(failurePolicy = ExtensionFailurePolicy.Default, onFailure = {}) { accepted })
        assertFailsWith<CancellationException> {
            updateIsolatingFailures(failurePolicy = ExtensionFailurePolicy.Default, onFailure = {}) {
                throw CancellationException("test cancellation")
            }
        }
        assertFailsWith<OutOfMemoryError> {
            updateIsolatingFailures(failurePolicy = ExtensionFailurePolicy.Default, onFailure = {}) {
                throw OutOfMemoryError("test fatal error")
            }
        }
    }
}
