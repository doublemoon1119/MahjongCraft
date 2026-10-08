package com.doublemoon1119.mahjongcraft.platform.fabric.server.history

import com.doublemoon1119.mahjongcraft.flow.common.game.history.replay.HistoryReplayFact
import com.doublemoon1119.mahjongcraft.flow.common.game.history.replay.HistoryReplayIdentity
import com.doublemoon1119.mahjongcraft.flow.common.game.history.replay.HistoryReplayTransaction
import com.doublemoon1119.mahjongcraft.flow.common.game.history.replay.HistoryRoundEvents
import com.doublemoon1119.mahjongcraft.flow.common.game.history.replay.HistoryRoundPosition
import com.doublemoon1119.mahjongcraft.flow.common.game.history.replay.HistoryRoundState
import com.doublemoon1119.mahjongcraft.flow.common.game.history.replay.HistoryRoundTileCatalog
import com.doublemoon1119.mahjongcraft.flow.network.dto.message.HistoryQueryErrorCodeDto
import com.doublemoon1119.mahjongcraft.flow.network.dto.message.HistoryQueryScopeDto
import com.doublemoon1119.mahjongcraft.flow.network.dto.message.HistoryRoundEventsRequestDto
import com.doublemoon1119.mahjongcraft.flow.network.dto.message.HistoryRoundEventsResponseDto
import com.doublemoon1119.mahjongcraft.flow.network.dto.message.HistoryRoundPositionDto
import com.doublemoon1119.mahjongcraft.flow.network.dto.message.HistoryRoundStateRequestDto
import com.doublemoon1119.mahjongcraft.logic.base.Tile
import com.doublemoon1119.mahjongcraft.logic.table.MatchRoundPosition
import com.doublemoon1119.mahjongcraft.logic.table.Wind
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.uuid.Uuid

/** 驗證單局線路預算、截止字典與不跳筆的續查索引。 */
class HistoryRoundResponseBudgetTest {
    /** 縮頁時移除後續新宣告牌，下一次從第一筆未送交易開始。 */
    @Test
    fun `shrinking events cuts future declarations and preserves next index`() {
        val identity = HistoryReplayIdentity(Uuid.random(), Uuid.random(), emptyList())
        val request = HistoryRoundEventsRequestDto("events", identity.matchId.toString(), roundNumber = 1, scope = HistoryQueryScopeDto.OWN, startTransactionIndex = 0, limit = 20)
        val transactions = listOf(
            HistoryReplayTransaction(0, 0L, true, emptyList(), 1),
            HistoryReplayTransaction(1, 1L, false, listOf(HistoryReplayFact.Opaque("測".repeat(1000))), 2),
        )
        val page = HistoryRoundEvents(identity, 1, transactions, null, HistoryRoundTileCatalog(listOf(Tile.Honor.East, Tile.Honor.South)))
        val single = boundedHistoryRoundEvents(request, page.copy(transactions = transactions.take(1), nextTransactionIndex = 1, tileCatalog = HistoryRoundTileCatalog(listOf(Tile.Honor.East))), Json)
        val maximum = Json.encodeToString(HistoryRoundEventsResponseDto.serializer(), single).encodeToByteArray().size
        val response = boundedHistoryRoundEvents(request, page, Json, maximum)
        val events = assertNotNull(response.events)
        assertEquals(listOf(0), events.transactions.map { it.index })
        assertEquals(1, events.tileCatalog.size)
        assertEquals(1, events.nextTransactionIndex)
        assertNull(response.errorCode)
    }

    /** 單筆超限不回傳空白成功頁或不前進的游標。 */
    @Test
    fun `one oversized transaction returns failure without partial catalog`() {
        val identity = HistoryReplayIdentity(Uuid.random(), Uuid.random(), emptyList())
        val request = HistoryRoundEventsRequestDto("events", identity.matchId.toString(), roundNumber = 1, scope = HistoryQueryScopeDto.OWN, startTransactionIndex = 0, limit = 20)
        val page = HistoryRoundEvents(identity, 1, listOf(HistoryReplayTransaction(0, 0L, false, emptyList(), 1)), 1, HistoryRoundTileCatalog(listOf(Tile.Honor.East)))
        val response = boundedHistoryRoundEvents(request, page, Json, 1)
        assertEquals(HistoryQueryErrorCodeDto.CONTENT_TOO_LARGE, response.errorCode)
        assertNull(response.events)
    }

    /** 經驗證的末端空頁可正常回覆，無後續游標。 */
    @Test
    fun `empty terminal page remains complete`() {
        val identity = HistoryReplayIdentity(Uuid.random(), Uuid.random(), emptyList())
        val page = HistoryRoundEvents(identity, 1, emptyList(), null, HistoryRoundTileCatalog(emptyList()))
        val response = boundedHistoryRoundEvents(HistoryRoundEventsRequestDto("events", identity.matchId.toString(), roundNumber = 1, scope = HistoryQueryScopeDto.OWN, startTransactionIndex = 0, limit = 20), page, Json)
        assertEquals(emptyList(), assertNotNull(response.events).transactions)
        assertNull(response.events?.nextTransactionIndex)
    }

    /** 桌況超限時拒絕整份資料，不刪除任何牌區後假裝成功。 */
    @Test
    fun `state response is atomic under byte budget`() {
        val identity = HistoryReplayIdentity(Uuid.random(), Uuid.random(), emptyList())
        val state = HistoryRoundState(
            identity, 1, HistoryRoundPosition.Initial, HistoryRoundTileCatalog(listOf(Tile.Honor.East)),
            emptyList(), emptyList(), emptyList(), 0, 0, Wind.EAST, MatchRoundPosition.initial(),
            0, emptySet(), null, false, false, null,
        )
        val request = HistoryRoundStateRequestDto("state", identity.matchId.toString(), roundNumber = 1, scope = HistoryQueryScopeDto.OWN, position = HistoryRoundPositionDto.Initial)
        assertNotNull(boundedHistoryRoundState(request, state, Json).state)
        val rejected = boundedHistoryRoundState(request, state, Json, 1)
        assertEquals(HistoryQueryErrorCodeDto.CONTENT_TOO_LARGE, rejected.errorCode)
        assertNull(rejected.state)
    }
}
