package com.doublemoon1119.mahjongcraft.platform.fabric.client.history

import com.doublemoon1119.mahjongcraft.flow.network.dto.message.HistoryQueryScopeDto
import com.doublemoon1119.mahjongcraft.flow.network.dto.message.HistoryReplayIdentityDto
import com.doublemoon1119.mahjongcraft.flow.network.dto.message.HistoryReplayPlayerIdentityDto
import com.doublemoon1119.mahjongcraft.flow.network.dto.message.HistoryReplayPlayerStateDto
import com.doublemoon1119.mahjongcraft.flow.network.dto.message.HistoryRoundEventsDto
import com.doublemoon1119.mahjongcraft.flow.network.dto.message.HistoryRoundPositionDto
import com.doublemoon1119.mahjongcraft.flow.network.dto.message.HistoryRoundStateDto
import com.doublemoon1119.mahjongcraft.flow.network.dto.model.SuitDto
import com.doublemoon1119.mahjongcraft.flow.network.dto.model.TileDto
import com.doublemoon1119.mahjongcraft.flow.network.dto.model.WindDto
import com.doublemoon1119.mahjongcraft.flow.network.dto.snapshot.MatchRoundPhaseDto
import com.doublemoon1119.mahjongcraft.flow.network.dto.snapshot.MatchRoundPositionDto
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

/** 測試用 canonical 對局 UUID。 */
private const val TEST_MATCH_ID = "00000000-0000-0000-0000-000000000001"

/** 測試用 canonical 牌桌 UUID。 */
private const val TEST_TABLE_ID = "00000000-0000-0000-0000-000000000002"

/** 驗證單局歷史成功快取的數量、容量、工作階段與期限限制。 */
class HistoryRoundCacheTest {
    /** 超過事件頁數量上限時移除最舊項目。 */
    @Test
    fun `test event entry cap evicts oldest`() {
        val cache = HistoryRoundCache(maxEventEntries = 1)
        val first = HistoryRoundCacheKey.Events(1, TEST_MATCH_ID, 1, HistoryQueryScopeDto.OWN, 0, 1)
        val second = first.copy(startTransactionIndex = 1)
        val value = HistoryRoundEventsDto(HistoryReplayIdentityDto(TEST_MATCH_ID, TEST_TABLE_ID, listOf(HistoryReplayPlayerIdentityDto(0, null, "test:ai"))), 1, emptyList(), null, emptyList())

        cache.putEvents(first, value, 1)
        cache.putEvents(second, value, 1)
        assertNull(cache.getEvents(first))
        assertEquals(value, cache.getEvents(second))
    }

    /** 讀取項目後會更新 LRU 順序，新增項目淘汰未讀取的舊項目。 */
    @Test
    fun `test cache access updates lru order`() {
        val cache = HistoryRoundCache(maxEventEntries = 2)
        val first = HistoryRoundCacheKey.Events(1, TEST_MATCH_ID, 1, HistoryQueryScopeDto.OWN, 0, 1)
        val second = first.copy(startTransactionIndex = 1)
        val third = first.copy(startTransactionIndex = 2)
        val value = HistoryRoundEventsDto(HistoryReplayIdentityDto(TEST_MATCH_ID, TEST_TABLE_ID, listOf(HistoryReplayPlayerIdentityDto(0, null, "test:ai"))), 1, emptyList(), null, emptyList())

        cache.putEvents(first, value, 1)
        cache.putEvents(second, value, 1)
        cache.getEvents(first)
        cache.putEvents(third, value, 1)
        assertEquals(value, cache.getEvents(first))
        assertNull(cache.getEvents(second))
    }

    /** 不同工作階段的結果不能被目前工作階段讀取。 */
    @Test
    fun `test session invalidation removes old entries`() {
        val cache = HistoryRoundCache()
        val key = HistoryRoundCacheKey.Events(1, TEST_MATCH_ID, 1, HistoryQueryScopeDto.OWN, 0, 1)
        val value = HistoryRoundEventsDto(HistoryReplayIdentityDto(TEST_MATCH_ID, TEST_TABLE_ID, listOf(HistoryReplayPlayerIdentityDto(0, null, "test:ai"))), 1, emptyList(), null, emptyList())

        cache.putEvents(key, value, 1)
        cache.invalidateOtherSessions(2)
        assertNull(cache.getEvents(key))
    }

    /** 超過總 UTF-8 容量的單一項目不加入快取。 */
    @Test
    fun `test oversized entry is rejected`() {
        val cache = HistoryRoundCache(maxBytes = 2)
        val key = HistoryRoundCacheKey.Events(1, TEST_MATCH_ID, 1, HistoryQueryScopeDto.OWN, 0, 1)
        val value = HistoryRoundEventsDto(HistoryReplayIdentityDto(TEST_MATCH_ID, TEST_TABLE_ID, listOf(HistoryReplayPlayerIdentityDto(0, null, "test:ai"))), 1, emptyList(), null, emptyList())

        assertEquals(false, cache.putEvents(key, value, 3))
        assertNull(cache.getEvents(key))
    }

    /** 成功項目超過存活時間後不得再次命中。 */
    @Test
    fun `test expired entry is not returned`() {
        var elapsed = Duration.ZERO
        val cache = HistoryRoundCache(now = { elapsed }, ttl = 30.seconds)
        val key = HistoryRoundCacheKey.Events(1, TEST_MATCH_ID, 1, HistoryQueryScopeDto.OWN, 0, 1)
        val second = key.copy(startTransactionIndex = 1)
        val value = HistoryRoundEventsDto(HistoryReplayIdentityDto(TEST_MATCH_ID, TEST_TABLE_ID, listOf(HistoryReplayPlayerIdentityDto(0, null, "test:ai"))), 1, emptyList(), null, emptyList())

        cache.putEvents(key, value, 1)
        cache.putEvents(second, value, 1)
        elapsed += 31.seconds
        assertNull(cache.getEvents(key))
        assertNull(cache.getEvents(second))
    }

    /** 桌況項目使用獨立數量上限。 */
    @Test
    fun `test state entry cap evicts oldest`() {
        val cache = HistoryRoundCache(maxStateEntries = 1)
        val first = HistoryRoundCacheKey.State(1, TEST_MATCH_ID, 1, HistoryQueryScopeDto.OWN, HistoryRoundPositionDto.Initial)
        val second = first.copy(position = HistoryRoundPositionDto.AfterTransaction(1))
        val value = cacheState()

        cache.putState(first, value, 1)
        cache.putState(second, value, 1)
        assertNull(cache.getState(first))
        assertEquals(value, cache.getState(second))
    }

    /** 事件頁與桌況共用總容量上限，超額時淘汰最舊項目。 */
    @Test
    fun `test combined cache byte budget evicts oldest`() {
        val cache = HistoryRoundCache(maxBytes = 3)
        val eventKey = HistoryRoundCacheKey.Events(1, TEST_MATCH_ID, 1, HistoryQueryScopeDto.OWN, 0, 1)
        val stateKey = HistoryRoundCacheKey.State(1, TEST_MATCH_ID, 1, HistoryQueryScopeDto.OWN, HistoryRoundPositionDto.Initial)
        val event = HistoryRoundEventsDto(HistoryReplayIdentityDto(TEST_MATCH_ID, TEST_TABLE_ID, listOf(HistoryReplayPlayerIdentityDto(0, null, "test:ai"))), 1, emptyList(), null, emptyList())

        cache.putEvents(eventKey, event, 2)
        cache.putState(stateKey, cacheState(), 2)
        assertNull(cache.getEvents(eventKey))
        assertEquals(cacheState(), cache.getState(stateKey))
    }

    /** 快取鍵的範圍、局序號與位置均會隔離結果。 */
    @Test
    fun `test cache key context is isolated`() {
        val cache = HistoryRoundCache()
        val key = HistoryRoundCacheKey.State(1, TEST_MATCH_ID, 1, HistoryQueryScopeDto.OWN, HistoryRoundPositionDto.Initial)
        val other = key.copy(roundNumber = 2, scope = HistoryQueryScopeDto.ALL, position = HistoryRoundPositionDto.AfterTransaction(1))
        cache.putState(key, cacheState(), 1)

        assertEquals(cacheState(), cache.getState(key))
        assertNull(cache.getState(other))
    }

    /** 移除一場對局時不影響其他對局。 */
    @Test
    fun `test evict match preserves another match`() {
        val cache = HistoryRoundCache()
        val removed = HistoryRoundCacheKey.State(1, TEST_MATCH_ID, 1, HistoryQueryScopeDto.OWN, HistoryRoundPositionDto.Initial)
        val retained = removed.copy(matchId = "00000000-0000-0000-0000-000000000003")
        cache.putState(removed, cacheState(), 1)
        cache.putState(retained, cacheState(), 1)

        cache.evictMatch(TEST_MATCH_ID)
        assertNull(cache.getState(removed))
        assertEquals(cacheState(), cache.getState(retained))
    }

    /**
     * 建立快取測試用最小合法桌況。
     *
     * @return 包含單張牌目錄與一名玩家的初始牌面。
     */
    private fun cacheState(): HistoryRoundStateDto = HistoryRoundStateDto(
        identity = HistoryReplayIdentityDto(TEST_MATCH_ID, TEST_TABLE_ID, listOf(HistoryReplayPlayerIdentityDto(0, null, "test:ai"))),
        roundNumber = 1,
        position = HistoryRoundPositionDto.Initial,
        tileCatalog = listOf(TileDto.Numeric(SuitDto.CHARACTER, 1)),
        players = listOf(HistoryReplayPlayerStateDto(0, emptyList(), emptyList(), null, emptyList(), 25000, WindDto.EAST, emptyList())),
        wallTiles = emptyList(),
        reservedTiles = emptyList(),
        currentPlayerSeat = 0,
        dealerSeat = 0,
        prevalentWind = WindDto.EAST,
        roundPosition = MatchRoundPositionDto(0, WindDto.EAST, 1, MatchRoundPhaseDto.REGULAR),
        comboCount = 0,
        finishedPlayerSeats = emptySet(),
        hasPendingReaction = false,
        hasPendingRobbingReaction = false,
        outcome = null,
    )
}
