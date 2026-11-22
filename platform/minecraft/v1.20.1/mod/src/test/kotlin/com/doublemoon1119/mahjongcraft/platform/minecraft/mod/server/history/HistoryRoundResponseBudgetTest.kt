package com.doublemoon1119.mahjongcraft.platform.minecraft.mod.server.history

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
import com.doublemoon1119.mahjongcraft.flow.network.dto.message.HistoryRoundStateResponseDto
import com.doublemoon1119.mahjongcraft.flow.network.dto.rule.DefaultNetworkDtoRegistries
import com.doublemoon1119.mahjongcraft.logic.base.Tile
import com.doublemoon1119.mahjongcraft.logic.base.TileFaceView
import com.doublemoon1119.mahjongcraft.logic.table.MatchRoundPosition
import com.doublemoon1119.mahjongcraft.logic.table.Wind
import com.doublemoon1119.mahjongcraft.platform.minecraft.mod.server.history.encodedHistoryResponseUtf8Size
import com.doublemoon1119.mahjongcraft.testing.flow.bundled.bundledNetworkJson
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
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
        val page = HistoryRoundEvents(identity, 1, transactions, null, transactions.size, HistoryRoundTileCatalog((listOf(Tile.Honor.East, Tile.Honor.South)).map(::TileFaceView)))
        val single = boundedHistoryRoundEvents(request, page.copy(transactions = transactions.take(1), nextTransactionIndex = 1, tileCatalog = HistoryRoundTileCatalog((listOf(Tile.Honor.East)).map(::TileFaceView))), bundledNetworkJson(), DefaultNetworkDtoRegistries())
        val maximum = Json.encodeToString(HistoryRoundEventsResponseDto.serializer(), single).encodeToByteArray().size
        val response = boundedHistoryRoundEvents(request, page, bundledNetworkJson(), DefaultNetworkDtoRegistries(), maximum)
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
        val page = HistoryRoundEvents(identity, 1, listOf(HistoryReplayTransaction(0, 0L, false, emptyList(), 1)), 1, 2, HistoryRoundTileCatalog((listOf(Tile.Honor.East)).map(::TileFaceView)))
        val response = boundedHistoryRoundEvents(request, page, bundledNetworkJson(), DefaultNetworkDtoRegistries(), 1)
        assertEquals(HistoryQueryErrorCodeDto.CONTENT_TOO_LARGE, response.errorCode)
        assertNull(response.events)
    }

    /** 經驗證的末端空頁可正常回覆，無後續游標。 */
    @Test
    fun `empty terminal page remains complete`() {
        val identity = HistoryReplayIdentity(Uuid.random(), Uuid.random(), emptyList())
        val page = HistoryRoundEvents(identity, 1, emptyList(), null, 0, HistoryRoundTileCatalog(emptyList()))
        val response = boundedHistoryRoundEvents(HistoryRoundEventsRequestDto("events", identity.matchId.toString(), roundNumber = 1, scope = HistoryQueryScopeDto.OWN, startTransactionIndex = 0, limit = 20), page, bundledNetworkJson(), DefaultNetworkDtoRegistries())
        assertEquals(emptyList(), assertNotNull(response.events).transactions)
        assertNull(response.events?.nextTransactionIndex)
    }

    /** 桌況超限時拒絕整份資料，不刪除任何牌區後假裝成功。 */
    @Test
    fun `state response is atomic under byte budget`() {
        val identity = HistoryReplayIdentity(Uuid.random(), Uuid.random(), emptyList())
        val state = HistoryRoundState(
            identity = identity, roundNumber = 1, position = HistoryRoundPosition.Initial, tileCatalog = HistoryRoundTileCatalog((listOf(Tile.Honor.East)).map(::TileFaceView)),
            players = emptyList(), wallTiles = emptyList(), reservedTiles = emptyList(), currentPlayerSeat = 0, dealerSeat = 0, prevalentWind = Wind.EAST, roundPosition = MatchRoundPosition.initial(),
            comboCount = 0, finishedPlayerSeats = emptySet(), hasPendingReaction = false, hasPendingRobbingReaction = false, outcome = null,
            wallPlacements = null, discardOrder = emptyList(), tilePoses = emptyMap(), presentationNotes = null,
        )
        val request = HistoryRoundStateRequestDto("state", identity.matchId.toString(), roundNumber = 1, scope = HistoryQueryScopeDto.OWN, position = HistoryRoundPositionDto.Initial, count = 1)
        assertEquals(1, boundedHistoryRoundStates(request, listOf(state), bundledNetworkJson(), DefaultNetworkDtoRegistries()).states.size)
        val rejected = boundedHistoryRoundStates(request, listOf(state), bundledNetworkJson(), DefaultNetworkDtoRegistries(), 1)
        assertEquals(HistoryQueryErrorCodeDto.CONTENT_TOO_LARGE, rejected.errorCode)
        assertTrue(rejected.states.isEmpty())
    }

    /** 放不下全部時，從第一份起回傳放得下的連續桌況，每份都完整。 */
    @Test
    fun `state responses keep the leading states that fit`() {
        val identity = HistoryReplayIdentity(Uuid.random(), Uuid.random(), emptyList())
        val states = (0 until 3).map { index ->
            HistoryRoundState(
                identity = identity, roundNumber = 1, position = HistoryRoundPosition.AfterTransaction(index), tileCatalog = HistoryRoundTileCatalog((listOf(Tile.Honor.East)).map(::TileFaceView)),
                players = emptyList(), wallTiles = emptyList(), reservedTiles = emptyList(), currentPlayerSeat = 0, dealerSeat = 0, prevalentWind = Wind.EAST, roundPosition = MatchRoundPosition.initial(),
                comboCount = 0, finishedPlayerSeats = emptySet(), hasPendingReaction = false, hasPendingRobbingReaction = false, outcome = null,
                wallPlacements = null, discardOrder = emptyList(), tilePoses = emptyMap(), presentationNotes = null,
            )
        }
        val request = HistoryRoundStateRequestDto("state", identity.matchId.toString(), roundNumber = 1, scope = HistoryQueryScopeDto.OWN, position = HistoryRoundPositionDto.AfterTransaction(0), count = 3)
        val all = boundedHistoryRoundStates(request, states, bundledNetworkJson(), DefaultNetworkDtoRegistries())
        val one = boundedHistoryRoundStates(request.copy(count = 1), states.take(1), bundledNetworkJson(), DefaultNetworkDtoRegistries())
        val oneSize = encodedHistoryResponseUtf8Size(bundledNetworkJson(), HistoryRoundStateResponseDto.serializer(), one)

        val trimmed = boundedHistoryRoundStates(request, states, bundledNetworkJson(), DefaultNetworkDtoRegistries(), oneSize + 1)

        assertEquals(3, all.states.size)
        assertEquals(listOf(HistoryRoundPositionDto.AfterTransaction(0)), trimmed.states.map { it.position })
        assertNull(trimmed.errorCode)
    }

    /** 各份桌況分開量出的大小加總，與整個回覆實際編碼的大小一致，剛好放得下時不多省略。 */
    @Test
    fun `state responses fit exactly at the byte budget`() {
        val identity = HistoryReplayIdentity(Uuid.random(), Uuid.random(), emptyList())
        val states = (0 until 3).map { index ->
            HistoryRoundState(
                identity = identity, roundNumber = 1, position = HistoryRoundPosition.AfterTransaction(index), tileCatalog = HistoryRoundTileCatalog((listOf(Tile.Honor.East)).map(::TileFaceView)),
                players = emptyList(), wallTiles = emptyList(), reservedTiles = emptyList(), currentPlayerSeat = 0, dealerSeat = 0, prevalentWind = Wind.EAST, roundPosition = MatchRoundPosition.initial(),
                comboCount = 0, finishedPlayerSeats = emptySet(), hasPendingReaction = false, hasPendingRobbingReaction = false, outcome = null,
                wallPlacements = null, discardOrder = emptyList(), tilePoses = emptyMap(), presentationNotes = null,
            )
        }
        val request = HistoryRoundStateRequestDto("state", identity.matchId.toString(), roundNumber = 1, scope = HistoryQueryScopeDto.OWN, position = HistoryRoundPositionDto.AfterTransaction(0), count = 3)
        val two = boundedHistoryRoundStates(request, states.take(2), bundledNetworkJson(), DefaultNetworkDtoRegistries())
        val twoSize = encodedHistoryResponseUtf8Size(bundledNetworkJson(), HistoryRoundStateResponseDto.serializer(), two)

        val exact = boundedHistoryRoundStates(request, states, bundledNetworkJson(), DefaultNetworkDtoRegistries(), twoSize)
        val short = boundedHistoryRoundStates(request, states, bundledNetworkJson(), DefaultNetworkDtoRegistries(), twoSize - 1)

        assertEquals(2, exact.states.size)
        assertEquals(twoSize, encodedHistoryResponseUtf8Size(bundledNetworkJson(), HistoryRoundStateResponseDto.serializer(), exact))
        assertEquals(1, short.states.size)
    }
}
