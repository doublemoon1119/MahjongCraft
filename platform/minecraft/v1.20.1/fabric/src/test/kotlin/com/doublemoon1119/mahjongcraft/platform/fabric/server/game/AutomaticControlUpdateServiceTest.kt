package com.doublemoon1119.mahjongcraft.platform.fabric.server.game

import com.doublemoon1119.mahjongcraft.flow.common.game.model.PlayerAutomaticControlSnapshot
import com.doublemoon1119.mahjongcraft.flow.network.dto.message.AutomaticControlUpdateRequestDto
import com.doublemoon1119.mahjongcraft.flow.network.dto.message.AutomaticControlUpdateResultKindDto
import com.doublemoon1119.mahjongcraft.flow.server.game.usecase.AutomaticControlUpdateRejection
import com.doublemoon1119.mahjongcraft.flow.server.game.usecase.AutomaticControlUpdateUnavailableReason
import com.doublemoon1119.mahjongcraft.flow.server.game.usecase.UpdatePlayerAutomaticControlsResult
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.uuid.Uuid

/** [AutomaticControlUpdateService] 使用的 Flow result 至線路 DTO 映射測試。 */
class AutomaticControlUpdateServiceTest {
    private val gameId = Uuid.random()
    private val request = AutomaticControlUpdateRequestDto(
        requestId = "request-1",
        gameId = gameId.toString(),
        expectedRevision = 3L,
        enabledControlIds = setOf("mahjongcraft:auto_win"),
    )
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
}
