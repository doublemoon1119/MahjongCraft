package com.doublemoon1119.mahjongcraft.flow.server.game.orchestration

import com.doublemoon1119.mahjongcraft.flow.common.game.history.replay.HistoryRoundPosition
import com.doublemoon1119.mahjongcraft.flow.common.game.history.replay.HistoryRoundState
import com.doublemoon1119.mahjongcraft.flow.persistence.format.history.HistoryRecordingPersistenceMapper
import com.doublemoon1119.mahjongcraft.flow.persistence.format.history.replay.CompactReplayCodec
import com.doublemoon1119.mahjongcraft.flow.persistence.format.history.replay.CompactReplayRoundReader
import com.doublemoon1119.mahjongcraft.flow.persistence.format.history.replay.HistoryReplayProjectionRegistry
import com.doublemoon1119.mahjongcraft.flow.persistence.format.history.replay.ReplayReadResult
import com.doublemoon1119.mahjongcraft.flow.persistence.format.history.replay.registerBuiltInHistoryReplayProjections
import com.doublemoon1119.mahjongcraft.flow.persistence.format.registry.buildBuiltInPersistenceRegistries
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

/** 使用確定性東風及半莊完整歷史驗證局級重建與預設資源餘裕。 */
class CompactReplayRoundReaderCapacityTest {
    /** 每場首局及末局只重建選取局，且與完整解碼器的保存分數一致。 */
    @Test
    fun `fixed full matches fit the bounded round read budget`() = runTest {
        val registries = buildBuiltInPersistenceRegistries()
        val projections = HistoryReplayProjectionRegistry().apply {
            registerBuiltInHistoryReplayProjections(this)
            freeze()
        }
        val reader = CompactReplayRoundReader(projections)
        for (fixture in ReplayCapacityFixtures.all) {
            val document = CompactReplayCodec.encodeCompact(fixture.events, HistoryRecordingPersistenceMapper(registries), registries)
            val full = CompactReplayCodec.decodeCompact(document)
            val numbers = fixture.events.map { it.roundNumber }.distinct()
            assertEquals(full.size, numbers.size)
            for (roundIndex in listOf(0, full.lastIndex).distinct()) {
                val last = full[roundIndex].lastIndex
                val result = assertIs<ReplayReadResult.Success<HistoryRoundState>>(reader.readState(document, fixture.events.first().matchId, numbers[roundIndex], HistoryRoundPosition.AfterTransaction(last)))
                assertEquals(last + 1, result.diagnostics.transactionsRebuilt)
                assertTrue(result.diagnostics.workUnits < 8_000_000L)
                val expected = full[roundIndex].last().projection.jsonObject.getValue("players").jsonArray.associate { player ->
                    val fields = player.jsonObject
                    fields.getValue("initialSeatIndex").jsonPrimitive.int to fields.getValue("score").jsonPrimitive.int
                }
                assertEquals(expected, result.value.players.associate { it.initialSeatIndex to it.score })
                assertTrue(result.value.tileCatalog.tiles.isNotEmpty())
            }
        }
    }
}
