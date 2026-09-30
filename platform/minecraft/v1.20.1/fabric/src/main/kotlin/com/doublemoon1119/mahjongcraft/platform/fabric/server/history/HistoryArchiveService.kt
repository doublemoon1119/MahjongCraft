package com.doublemoon1119.mahjongcraft.platform.fabric.server.history

import com.doublemoon1119.mahjongcraft.flow.common.game.history.HistoryCaptureState
import com.doublemoon1119.mahjongcraft.flow.common.game.history.HistoryFact
import com.doublemoon1119.mahjongcraft.flow.common.game.history.HistoryOutboxEvent
import com.doublemoon1119.mahjongcraft.flow.persistence.dto.history.HistoryCapturePersistenceMapper
import com.doublemoon1119.mahjongcraft.flow.persistence.dto.history.HistoryOutboxEventPersistenceDto
import com.doublemoon1119.mahjongcraft.flow.persistence.dto.history.replay.CompactReplayCodec
import com.doublemoon1119.mahjongcraft.flow.persistence.dto.registry.PersistenceRegistries
import com.doublemoon1119.mahjongcraft.flow.server.state.AuthoritativeStateSnapshot
import com.doublemoon1119.mahjongcraft.logic.module.MahjongModuleRegistry
import com.doublemoon1119.mahjongcraft.platform.minecraft.table.TableLocationRegistry
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import org.slf4j.LoggerFactory

/**
 * 對帳權威 outbox 與 SQLite，並只在完整返回房間後封存同場的連續事件。
 *
 * @property mapper 權威事件與版本化 DTO 的轉換器。
 * @property registries 規則擴充的持久化登記表。
 * @property moduleRegistry 從開局規則設定解析規則 ID。
 * @property locations 可讀取不可變位置快照的牌桌位置登記表。
 * @property json 原始事件與 Replay 的序列化設定。
 */
internal class HistoryArchiveService(
    private val mapper: HistoryCapturePersistenceMapper,
    private val registries: PersistenceRegistries,
    private val moduleRegistry: MahjongModuleRegistry,
    private val locations: TableLocationRegistry,
    private val json: Json,
) {
    private val logger = LoggerFactory.getLogger(HistoryArchiveService::class.java)

    /** 最近一次逐場封存失敗的安全摘要，不包含隱藏牌或 payload。 */
    @Volatile var lastArchiveError: String? = null
        private set

    /** 內容相衝或已損壞的場次不可再次寫入相同鍵。 */
    @Volatile var blockedMatchIds: Set<String> = emptySet()
        private set
    private var warnedOrphanMatchIds: Set<String> = emptySet()

    /** 保留可證實的最早缺口；不從最高序號推斷中間一定連續。 */
    fun reconcile(database: SqliteHistoryDatabase, capture: HistoryCaptureState): Map<String, Long> {
        val existing = database.readAllPending().groupBy(PendingHistoryRecord::matchId)
        val outbox = capture.pendingEvents.groupBy { it.matchId.toString() }
        val archived = database.readReplayIds()
        val candidates = existing.keys + outbox.keys + capture.nextSequenceByMatchId.keys.map { it.toString() } +
            capture.firstMissingSequenceByMatchId.keys.map { it.toString() }
        val gaps = capture.firstMissingSequenceByMatchId
            .filterKeys { it.toString() !in archived }
            .mapKeys { it.key.toString() }
            .toMutableMap()
        val blocked = mutableSetOf<String>()
        val orphans = mutableSetOf<String>()
        candidates.filterNot { it in archived }.forEach { matchId ->
            val persisted = existing[matchId].orEmpty()
            val staged = outbox[matchId].orEmpty()
            persisted.forEach { record ->
                try {
                    decodeRecord(record)
                } catch (error: Exception) {
                    gaps[matchId] = minOf(gaps[matchId] ?: record.sequence, record.sequence)
                    blocked += matchId
                    lastArchiveError = "History event payload validation failed"
                    logger.error("History event payload validation failed for match {} at sequence {}", matchId, record.sequence, error)
                }
            }
            val available = (persisted.map { it.sequence } + staged.map { it.sequence }).distinct().sorted()
            val nextSequence = capture.nextSequenceByMatchId.entries.firstOrNull { it.key.toString() == matchId }?.value ?: 1L
            if (persisted.isNotEmpty() && persisted.last().sequence >= nextSequence && staged.isEmpty()) {
                lastArchiveError = "History database contains events beyond the authoritative save"
                orphans += matchId
                if (matchId !in warnedOrphanMatchIds) {
                    logger.warn("History database has events beyond the authoritative sequence for match {}", matchId)
                }
            }
            val highest = maxOf(available.lastOrNull() ?: 0L, nextSequence - 1L)
            var expected = 1L
            available.forEach { sequence ->
                if (sequence == expected) expected++
            }
            if (expected <= highest) gaps[matchId] = minOf(gaps[matchId] ?: expected, expected)
            val bySequence = persisted.associateBy(PendingHistoryRecord::sequence)
            staged.forEach { event ->
                val onDisk = bySequence[event.sequence] ?: return@forEach
                val encoded = runCatching {
                    json.encodeToString(HistoryOutboxEventPersistenceDto.serializer(), mapper.encodePendingEvent(event))
                }.getOrNull()
                if (onDisk.payload != encoded) {
                    gaps[matchId] = minOf(gaps[matchId] ?: event.sequence, event.sequence)
                    blocked += matchId
                    lastArchiveError = "History event identity conflicts with SQLite content"
                    logger.error("History event identity conflicts with SQLite content for match {} at sequence {}", matchId, event.sequence)
                }
            }
        }
        database.recordGaps(gaps)
        blockedMatchIds = blocked
        warnedOrphanMatchIds = orphans
        return database.readGaps()
    }

    /** 將已返回房間且無待寫事件的完整對局封存；單場失敗不阻止其他場次。 */
    fun archiveReady(database: SqliteHistoryDatabase, snapshot: AuthoritativeStateSnapshot): Int {
        val staged = snapshot.historyCaptureState.pendingEvents.mapTo(mutableSetOf()) { it.matchId.toString() }
        val active = snapshot.games.values.mapTo(mutableSetOf()) { it.matchId.toString() }
        val gaps = database.readGaps().keys
        val archived = database.readReplayIds()
        var completed = 0
        database.readAllPending().groupBy(PendingHistoryRecord::matchId).forEach { (matchId, records) ->
            if (matchId in staged || matchId in active || matchId in gaps || matchId in archived) return@forEach
            try {
                val events = records.map(::decodeRecord)
                if (events.lastOrNull()?.fact !is HistoryFact.ReturnedToRoom) return@forEach
                check(events.first().sequence == 1L && events.zipWithNext().all { (a, b) -> a.sequence + 1 == b.sequence }) {
                    "History event sequence is incomplete"
                }
                val replay = CompactReplayCodec.encodeCompact(events, mapper, registries, json)
                CompactReplayCodec.decodeCompact(replay)
                val archive = buildArchive(events, replay)
                if (database.archive(archive)) completed++
            } catch (error: Exception) {
                lastArchiveError = error.message ?: "History archive validation failed"
                logger.error("History match {} could not be archived; original events were retained", matchId, error)
            }
        }
        return completed
    }

    /** 拒絕版本、欄位或 DTO 不一致的暫存列。 */
    private fun decodeRecord(record: PendingHistoryRecord): HistoryOutboxEvent {
        require(record.payloadVersion == 1) { "Unsupported history event payload version" }
        val dto = json.decodeFromString(HistoryOutboxEventPersistenceDto.serializer(), record.payload)
        require(
            dto.matchId == record.matchId &&
                dto.sequence == record.sequence &&
                dto.roundNumber == record.roundNumber &&
                dto.occurredAtEpochMillis == record.occurredAtEpochMillis,
        ) { "History event payload does not match its SQLite envelope" }
        return mapper.decodePendingEvent(dto)
    }

    /** 從開局、換局、終局事實建立可查閱摘要，不猜測不存在的維度位置。 */
    private fun buildArchive(events: List<HistoryOutboxEvent>, replay: JsonObject): HistoryArchiveRecord {
        val start = events.first()
        val opening = start.fact as HistoryFact.MatchStarted
        val completion = events.first { it.fact is HistoryFact.MatchCompleted }
        val rounds = mutableListOf<HistoryRoundRecord>()
        events.forEach { event ->
            when (event.fact) {
                is HistoryFact.MatchStarted, is HistoryFact.RoundStarted -> rounds += HistoryRoundRecord(event.roundNumber, event.occurredAtEpochMillis, null)
                is HistoryFact.RoundCompleted -> {
                    val last = rounds.last()
                    rounds[rounds.lastIndex] = last.copy(endedAtEpochMillis = event.occurredAtEpochMillis)
                }
                else -> Unit
            }
        }
        val players = opening.tableState.players.sortedBy { it.initialSeatIndex }.map {
            HistoryParticipantRecord(it.initialSeatIndex, it.id.toString(), it.aiStrategyKey)
        }
        return HistoryArchiveRecord(
            matchId = start.matchId.toString(),
            tableId = start.tableId.toString(),
            ruleId = moduleRegistry.getModule(opening.tableState.config).id,
            dimensionId = locations.get(start.tableId)?.location?.dimensionId,
            startedAtEpochMillis = start.occurredAtEpochMillis,
            endedAtEpochMillis = completion.occurredAtEpochMillis,
            participants = players,
            rounds = rounds,
            replayPayload = json.encodeToString(JsonObject.serializer(), replay),
        )
    }
}
