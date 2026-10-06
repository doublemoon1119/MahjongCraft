package com.doublemoon1119.mahjongcraft.flow.server.game.orchestration

import com.doublemoon1119.mahjongcraft.flow.common.game.history.HistoryFact
import com.doublemoon1119.mahjongcraft.flow.common.game.history.HistoryOutboxEvent
import com.doublemoon1119.mahjongcraft.flow.common.game.history.HistoryTableResult
import com.doublemoon1119.mahjongcraft.flow.common.game.history.HistoryWinDetails
import com.doublemoon1119.mahjongcraft.flow.common.game.history.HistoryWinningHand
import com.doublemoon1119.mahjongcraft.flow.common.game.model.WinSettlementDetailEntry
import com.doublemoon1119.mahjongcraft.flow.common.game.model.WinSettlementDetailField
import com.doublemoon1119.mahjongcraft.flow.common.game.model.WinSettlementDetailValue
import com.doublemoon1119.mahjongcraft.flow.common.game.model.WinSettlementQuantity
import com.doublemoon1119.mahjongcraft.flow.persistence.format.history.HistoryRecordingPersistenceMapper
import com.doublemoon1119.mahjongcraft.flow.persistence.format.history.replay.CompactReplayCodec
import com.doublemoon1119.mahjongcraft.logic.table.TableState
import com.doublemoon1119.mahjongcraft.testing.flow.bundled.bundledPersistenceRegistries
import java.io.ByteArrayOutputStream
import java.util.logging.Logger
import java.util.zip.GZIPOutputStream
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * 以固定對局及代表性的擴充明細量測新增資料成本，不依賴隨機洗牌。
 *
 * 明細是容量用負載，不宣稱是樣本對局實際成立的役種；實際結算正確性由權威流程測試驗證。
 */
class HistoryWinDetailsCapacityTest {
    /** 固定樣本加入兩次含三個條目與計分摘要的代表性結算後仍符合既有門檻。 */
    @Test
    fun `representative winner details remain within fixed replay capacity limits`() {
        val registries = bundledPersistenceRegistries()
        val mapper = HistoryRecordingPersistenceMapper(registries)
        for ((index, fixture) in ReplayCapacityFixtures.all.withIndex()) {
            val expanded = withDetails(fixture.events)
            val original = CompactReplayCodec.encodeCompact(fixture.events, mapper, registries).toString().toByteArray(Charsets.UTF_8)
            val bytes = CompactReplayCodec.encodeCompact(expanded, mapper, registries).toString().toByteArray(Charsets.UTF_8)
            val compressed = gzip(bytes)
            assertTrue(expanded.any { it.fact is HistoryFact.WinSettled }, "Capacity fixture must include winner details")
            assertTrue(bytes.size > original.size, "Winner detail capacity fixture must measure a nonzero additional payload")
            assertTrue(bytes.size <= 220_000, "Winner details exceeded the fixed JSON capacity limit: ${bytes.size}")
            assertTrue(compressed.size <= 35_000, "Winner details exceeded the fixed gzip capacity limit: ${compressed.size}")
            Logger.getLogger(javaClass.name).info(
                "Winner detail capacity fixture ${index + 1}: JSON=${bytes.size} bytes, gzip=${compressed.size} bytes, additional JSON=${bytes.size - original.size} bytes",
            )
            val dense = CompactReplayCodec.encodeCompact(withDetails(fixture.events, Int.MAX_VALUE, 12), mapper, registries).toString().toByteArray(Charsets.UTF_8)
            val denseGzip = gzip(dense)
            assertTrue(dense.size - original.size <= 8_000, "Dense winner detail payload exceeded its additional JSON budget")
            assertTrue(denseGzip.size <= 35_000, "Dense winner detail payload exceeded the gzip limit")
            Logger.getLogger(javaClass.name).info("Dense winner detail capacity fixture ${index + 1}: JSON=${dense.size} bytes, gzip=${denseGzip.size} bytes, additional JSON=${dense.size - original.size} bytes")
        }
    }

    /**
     * 為固定樣本的完成交易加入容量用和牌詳情，重新編排序號但保留交易分組。
     * 流局樣本同樣注入負載，確保無和牌樣本不會讓量測漏掉新增資料成本。
     * @param events 固定樣本原始權威歷史。
     * @param maxSettlements 最多注入的結算數；密集量測可涵蓋每局。
     * @param entryCount 每次注入的明細條目數。
     * @return 含容量用和牌詳情的事件序列。
     */
    private fun withDetails(events: List<HistoryOutboxEvent>, maxSettlements: Int = 2, entryCount: Int = 3): List<HistoryOutboxEvent> {
        var sequence = 1L
        var settlements = 0
        var tableState = events.asSequence().map { it.fact }.filterIsInstance<HistoryFact.MatchStarted>().firstOrNull()?.tableState
        return events.groupBy { it.transactionFirstSequence }.values.flatMap { transaction ->
            val first = sequence
            val advance = { state: TableState?, event: HistoryOutboxEvent ->
                when (val fact = event.fact) {
                    is HistoryFact.RoundStarted -> fact.tableState
                    is HistoryFact.TableChanged -> when (val result = fact.result) {
                        is HistoryTableResult.Change -> state?.let { result.change.applyTo(it) }
                        is HistoryTableResult.Checkpoint -> result.tableState
                    }
                    else -> state
                }
            }
            // 和牌明細屬於結束的那一局；同一筆交易若接著開下一局，手牌取自開局前的桌況。
            val settlementState = transaction.takeWhile { it.fact !is HistoryFact.RoundStarted }.fold(tableState, advance)
            tableState = transaction.fold(tableState, advance)
            val summary = transaction.mapNotNull { (it.fact as? HistoryFact.RoundCompleted)?.summary }.firstOrNull()
            val winners = if (summary != null && settlements < maxSettlements) {
                settlements++
                val winnerIds = summary.beneficiaryPlayerIds.toList().ifEmpty { listOf(summary.settledScoresByPlayerId.keys.first()) }
                winnerIds.mapNotNull { playerId ->
                    val player = settlementState?.players?.firstOrNull { it.id == playerId } ?: return@mapNotNull null
                    val winningTileId = player.hand.lastDrawn?.id
                    HistoryWinDetails(
                        playerId = playerId,
                        detailFields = fields(entryCount),
                        hand = HistoryWinningHand(
                            standingTileIds = player.hand.standingTiles.map { it.id }.filter { it != winningTileId },
                            winningTileId = winningTileId,
                        ),
                    )
                }
            } else {
                emptyList()
            }
            buildList {
                var inserted = false
                transaction.forEach { event ->
                    if (!inserted &&
                        summary != null &&
                        winners.isNotEmpty() &&
                        (event.fact is HistoryFact.TableChanged || event.fact is HistoryFact.RoundCompleted)
                    ) {
                        add(
                            event.copy(
                                sequence = sequence++,
                                transactionFirstSequence = first,
                                fact = HistoryFact.WinSettled(summary.outcomeId, winners, summary.responsiblePlayerIds.toList()),
                            ),
                        )
                        inserted = true
                    }
                    add(event.copy(sequence = sequence++, transactionFirstSequence = first))
                }
            }
        }
    }

    /**
     * 建立規則中立的固定負載，涵蓋多條目與有單位數值。
     * @param entryCount 明細條目數。
     * @return 每位贏家的代表性詳情欄位。
     */
    private fun fields(entryCount: Int): List<WinSettlementDetailField> = listOf(
        WinSettlementDetailField(
            "capacity:patterns",
            WinSettlementDetailValue.Entries(
                (1..entryCount).map { index ->
                    WinSettlementDetailEntry("capacity:pattern/$index", WinSettlementQuantity("capacity:unit", index))
                },
            ),
        ),
        WinSettlementDetailField(
            "capacity:summary",
            WinSettlementDetailValue.Quantities(listOf(WinSettlementQuantity("capacity:han", 12), WinSettlementQuantity("capacity:fu", 40))),
        ),
    )

    /**
     * 量測整份精簡 Replay 壓縮後的實際位元組數。
     * @param bytes 原始 UTF-8 JSON。
     * @return gzip 內容。
     */
    private fun gzip(bytes: ByteArray): ByteArray = ByteArrayOutputStream().use { output ->
        GZIPOutputStream(output).use { it.write(bytes) }
        output.toByteArray()
    }
}
