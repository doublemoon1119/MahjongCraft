package com.doublemoon1119.mahjongcraft.flow.server.game.orchestration

import com.doublemoon1119.mahjongcraft.flow.common.game.history.HistoryFact
import com.doublemoon1119.mahjongcraft.flow.common.game.history.replay.HistoryRoundEvents
import com.doublemoon1119.mahjongcraft.flow.common.game.history.replay.HistoryRoundPosition
import com.doublemoon1119.mahjongcraft.flow.common.game.history.replay.HistoryRoundState
import com.doublemoon1119.mahjongcraft.flow.persistence.format.history.HistoryRecordingPersistenceMapper
import com.doublemoon1119.mahjongcraft.flow.persistence.format.history.replay.CompactReplayCodec
import com.doublemoon1119.mahjongcraft.flow.persistence.format.history.replay.CompactReplayRoundReader
import com.doublemoon1119.mahjongcraft.flow.persistence.format.history.replay.HistoryReplayProjectionRegistry
import com.doublemoon1119.mahjongcraft.flow.persistence.format.history.replay.ReplayJsonParseLimits
import com.doublemoon1119.mahjongcraft.flow.persistence.format.history.replay.ReplayReadResult
import com.doublemoon1119.mahjongcraft.flow.persistence.format.history.replay.parseBoundedReplayJson
import com.doublemoon1119.mahjongcraft.testing.flow.bundled.bundledPersistenceRegistries
import com.doublemoon1119.mahjongcraft.testing.flow.bundled.registerBundledHistoryReplayProjections
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** 使用確定性東風及半莊完整歷史驗證局級重建與預設資源餘裕。 */
class CompactReplayRoundReaderCapacityTest {
    /** 每場首局及末局只重建選取局，且與完整解碼器的保存分數一致。 */
    @Test
    fun `fixed full matches fit the bounded round read budget`() = runTest {
        val registries = bundledPersistenceRegistries()
        val projections = HistoryReplayProjectionRegistry().apply {
            this.registerBundledHistoryReplayProjections()
            freeze()
        }
        val reader = CompactReplayRoundReader(projections)
        for (fixture in ReplayCapacityFixtures.all) {
            val document = CompactReplayCodec.encodeCompact(fixture.events, HistoryRecordingPersistenceMapper(registries), registries)
            val parsed = assertIs<ReplayReadResult.Success<JsonObject>>(
                parseBoundedReplayJson(document.toString(), ReplayJsonParseLimits(maximumUtf8Bytes = 8 * 1024 * 1024)),
            ).value
            val full = CompactReplayCodec.decodeCompact(document)
            val openings = fixture.events.count { it.fact is HistoryFact.MatchStarted || it.fact is HistoryFact.RoundStarted }
            assertEquals(openings, full.size)
            val numbers = full.indices.map { it + 1 }
            for (roundIndex in listOf(0, full.lastIndex).distinct()) {
                val last = full[roundIndex].lastIndex
                val result = assertIs<ReplayReadResult.Success<HistoryRoundState>>(reader.readState(parsed, fixture.events.first().matchId, numbers[roundIndex], HistoryRoundPosition.AfterTransaction(last)))
                assertEquals(last + 1, result.diagnostics.transactionsRebuilt)
                assertTrue(result.diagnostics.workUnits < 8_000_000L)
                val expected = full[roundIndex].last().projection.jsonObject.getValue("players").jsonArray.associate { player ->
                    val fields = player.jsonObject
                    fields.getValue("initialSeatIndex").jsonPrimitive.int to fields.getValue("score").jsonPrimitive.int
                }
                assertEquals(expected, result.value.players.associate { it.initialSeatIndex to it.score })
                assertTrue(result.value.tileCatalog.tiles.isNotEmpty())
                val firstPage = assertIs<ReplayReadResult.Success<HistoryRoundEvents>>(reader.readEvents(parsed, fixture.events.first().matchId, numbers[roundIndex], 0, 20))
                val repeatedPage = assertIs<ReplayReadResult.Success<HistoryRoundEvents>>(reader.readEvents(parsed, fixture.events.first().matchId, numbers[roundIndex], 0, 20))
                assertEquals(firstPage.value, repeatedPage.value)
                val finalPage = assertIs<ReplayReadResult.Success<HistoryRoundEvents>>(reader.readEvents(parsed, fixture.events.first().matchId, numbers[roundIndex], last, 1))
                assertEquals(last, finalPage.value.transactions.single().index)
                assertNull(finalPage.value.nextTransactionIndex)
                for (page in listOf(firstPage, repeatedPage, finalPage)) {
                    assertTrue(page.diagnostics.workUnits < 8_000_000L)
                }
            }
        }
    }
}
