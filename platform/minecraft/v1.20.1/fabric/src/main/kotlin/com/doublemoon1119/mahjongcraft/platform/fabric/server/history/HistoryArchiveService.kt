package com.doublemoon1119.mahjongcraft.platform.fabric.server.history

import com.doublemoon1119.mahjongcraft.flow.common.game.history.HistoryFact
import com.doublemoon1119.mahjongcraft.flow.common.game.history.HistoryOutboxEvent
import com.doublemoon1119.mahjongcraft.flow.common.game.history.HistoryRecordingState
import com.doublemoon1119.mahjongcraft.flow.persistence.format.history.HistoryOutboxEventPersistenceDto
import com.doublemoon1119.mahjongcraft.flow.persistence.format.history.HistoryRecordingPersistenceMapper
import com.doublemoon1119.mahjongcraft.flow.persistence.format.history.replay.CompactReplayCodec
import com.doublemoon1119.mahjongcraft.flow.persistence.format.registry.PersistenceRegistries
import com.doublemoon1119.mahjongcraft.flow.server.game.history.HistoryResultProjector
import com.doublemoon1119.mahjongcraft.flow.server.state.AuthoritativeStateSnapshot
import com.doublemoon1119.mahjongcraft.logic.module.MahjongModuleRegistry
import com.doublemoon1119.mahjongcraft.platform.fabric.logging.mahjongCraftLogger
import com.doublemoon1119.mahjongcraft.platform.minecraft.table.TableLocationRegistry
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlin.uuid.Uuid

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
    private val mapper: HistoryRecordingPersistenceMapper,
    private val registries: PersistenceRegistries,
    private val moduleRegistry: MahjongModuleRegistry,
    private val locations: TableLocationRegistry,
    private val json: Json,
) {
    private val logger = mahjongCraftLogger(HistoryArchiveService::class)

    /** 最近一次逐場封存失敗的安全摘要，不包含隱藏牌或 payload。 */
    @Volatile var lastArchiveError: String? = null
        private set

    /** 內容相衝或已損壞的場次不可再次寫入相同鍵。 */
    @Volatile var blockedMatchIds: Set<String> = emptySet()
        private set
    private var warnedOrphanMatchIds: Set<String> = emptySet()

    /** 上一次對帳已記錄過的待寫事件問題（場次與序號）；問題持續存在時不重複記錄。 */
    private var reportedEventProblems: Set<Pair<String, Long>> = emptySet()

    /**
     * 本次資料庫連線中，每場已解碼驗證且確認從 1 起連續的最高序號；只用來省略重複解碼，不影響完整性判定。
     *
     * 每次開啟資料庫時清空（[beginConnection]），因此每次連線的第一次對帳一定完整解碼。
     */
    private val validatedThrough = HashMap<String, Long>()

    /** 在開始另一個存檔 session 前清除錯誤與逐場對帳快取，不修改資料庫。 */
    fun resetSession() {
        lastArchiveError = null
        blockedMatchIds = emptySet()
        warnedOrphanMatchIds = emptySet()
        reportedEventProblems = emptySet()
        validatedThrough.clear()
    }

    /** 開啟資料庫（包含重新連線）時清空已驗證的序號，讓接下來的對帳完整解碼這次連線的所有待寫事件。 */
    fun beginConnection() {
        validatedThrough.clear()
    }

    /**
     * 對帳權威待寫佇列與 SQLite 的待寫事件，保留可證實的最早缺口；不從最高序號推導中間一定連續。
     *
     * 每次都完整判定四件事：payload 能否解碼、序號缺口、資料庫序號超過權威存檔的孤兒事件，以及待寫佇列與資料庫重疊的
     * 序號內容是否衝突。資料庫已有終局紀錄、且權威狀態已不再記錄其序號的場次是已結束的場次，不判為孤兒；權威狀態仍記錄
     * 序號時，即使資料庫有終局紀錄，序號超過權威存檔仍判為孤兒（存檔回溯）。序號與重疊判定只讀主鍵或重疊的那幾筆；payload 解碼則略過本次連線已驗證過、從 1 起連續的事件。
     *
     * @param database 目前連線的資料庫。
     * @param recording 權威狀態中的歷史記錄狀態。
     * @return 對帳後資料庫中各場的最早缺口。
     */
    fun reconcile(database: SqliteHistoryDatabase, recording: HistoryRecordingState): Map<String, Long> {
        val summaries = database.readPendingSequenceSummaries()
        val outbox = recording.pendingEvents.groupBy { it.matchId.toString() }
        val nextSequences = recording.nextSequenceByMatchId.mapKeys { it.key.toString() }
        val archived = database.readReplayIds() + database.readTombstones()
        val ended = database.readTerminalIds()
        val candidates = (summaries.keys + outbox.keys + nextSequences.keys + recording.firstMissingSequenceByMatchId.keys.map { it.toString() }) - archived
        validatedThrough.keys.retainAll(candidates)
        val gaps = recording.firstMissingSequenceByMatchId
            .filterKeys { it.toString() !in archived }
            .mapKeys { it.key.toString() }
            .toMutableMap()
        val blocked = mutableSetOf<String>()
        val orphans = mutableSetOf<String>()
        val eventProblems = mutableSetOf<Pair<String, Long>>()
        candidates.forEach { matchId ->
            val summary = summaries[matchId]
            val staged = outbox[matchId].orEmpty()
            if (summary != null) validatePayloads(database, matchId, summary, gaps, blocked)
            val persistedSequences = when {
                summary == null -> emptyList()
                summary.contiguousFromStart -> null
                else -> database.readPendingSequences(matchId)
            }
            val nextSequence = nextSequences[matchId] ?: 1L
            val forgottenAfterEnding = matchId !in nextSequences && matchId in ended
            if (summary != null && summary.last >= nextSequence && staged.isEmpty() && !forgottenAfterEnding) {
                lastArchiveError = "History database contains events beyond the authoritative save"
                orphans += matchId
                if (matchId !in warnedOrphanMatchIds) {
                    logger.warn("History database has events beyond the authoritative sequence for match {}", matchId)
                }
            }
            val missing = firstMissingSequence(summary, persistedSequences, staged.map { it.sequence }, nextSequence)
            if (missing != null) gaps[matchId] = minOf(gaps[matchId] ?: missing, missing)
            val overlapping = staged.filter { event ->
                summary != null && event.sequence in summary.first..summary.last && (persistedSequences == null || event.sequence in persistedSequences)
            }
            if (overlapping.isEmpty()) return@forEach
            val bySequence = database.readPendingAt(matchId, overlapping.map { it.sequence }).associateBy(PendingHistoryRecord::sequence)
            overlapping.forEach { event ->
                val onDisk = bySequence[event.sequence] ?: return@forEach
                val comparison = compareStagedHistoryEvent(onDisk.payload) {
                    json.encodeToString(HistoryOutboxEventPersistenceDto.serializer(), mapper.encodePendingEvent(event))
                }
                if (comparison == StagedHistoryEventComparison.Matches) return@forEach
                gaps[matchId] = minOf(gaps[matchId] ?: event.sequence, event.sequence)
                blocked += matchId
                validatedThrough.remove(matchId)
                val problem = matchId to event.sequence
                eventProblems += problem
                val firstReport = problem !in reportedEventProblems
                when (comparison) {
                    is StagedHistoryEventComparison.EncodingFailed -> {
                        lastArchiveError = "History event could not be encoded for comparison with SQLite content"
                        if (firstReport) {
                            logger.error(
                                "History event could not be encoded for comparison with SQLite content for match {} at sequence {}",
                                matchId,
                                event.sequence,
                                comparison.error,
                            )
                        }
                    }
                    StagedHistoryEventComparison.Conflicts -> {
                        lastArchiveError = "History event identity conflicts with SQLite content"
                        if (firstReport) {
                            logger.error("History event identity conflicts with SQLite content for match {} at sequence {}", matchId, event.sequence)
                        }
                    }
                    StagedHistoryEventComparison.Matches -> Unit
                }
            }
        }
        database.recordGaps(gaps)
        blockedMatchIds = blocked
        warnedOrphanMatchIds = orphans
        reportedEventProblems = eventProblems
        return database.readGaps()
    }

    /**
     * 解碼驗證一場尚未驗證過的待寫事件；無法解碼的事件記為缺口並禁止寫入該場。
     *
     * 只讀取高於已驗證序號的事件；全部成功且從 1 起連續時推進已驗證的序號，否則移除該場的已驗證紀錄，下次重新完整解碼。
     */
    private fun validatePayloads(
        database: SqliteHistoryDatabase,
        matchId: String,
        summary: PendingSequenceSummary,
        gaps: MutableMap<String, Long>,
        blocked: MutableSet<String>,
    ) {
        val validated = validatedThrough[matchId] ?: 0L
        if (summary.last <= validated) return
        var contiguousThrough = validated
        var failed = false
        database.readPendingAfter(matchId, validated).forEach { record ->
            try {
                decodeRecord(record)
                if (!failed && record.sequence == contiguousThrough + 1) contiguousThrough = record.sequence
            } catch (error: Exception) {
                failed = true
                gaps[matchId] = minOf(gaps[matchId] ?: record.sequence, record.sequence)
                blocked += matchId
                lastArchiveError = "History event payload validation failed"
                logger.error("History event payload validation failed for match {} at sequence {}", matchId, record.sequence, error)
            }
        }
        if (failed) validatedThrough.remove(matchId) else validatedThrough[matchId] = contiguousThrough
    }

    /**
     * 將已返回房間且無待寫事件的完整對局封存；單場失敗不阻止其他場次。
     *
     * @param database 目前連線的資料庫。
     * @param snapshot 權威狀態。
     * @param scanAllPending 為 true 時讀出資料庫所有待寫事件找出可封存的場次，用於開啟資料庫時；為 false 時只讀取已有
     *   終局紀錄（權威狀態或資料庫）的場次，用於連線期間。兩者的封存條件相同。
     * @param onArchived 接收每一場完成的封存。
     * @return 封存的場數。
     */
    fun archiveReady(
        database: SqliteHistoryDatabase,
        snapshot: AuthoritativeStateSnapshot,
        scanAllPending: Boolean,
        onArchived: (HistoryArchiveRecord) -> Unit = {},
    ): Int {
        val staged = snapshot.historyRecordingState.pendingEvents.mapTo(mutableSetOf()) { it.matchId.toString() }
        val active = snapshot.games.values.mapTo(mutableSetOf()) { it.matchId.toString() } +
            snapshot.historyRecordingState.transfersByMatchId.keys.map { it.toString() }
        val gaps = database.readGaps().keys
        val stopped = database.readRecordingStops().keys
        val archived = database.readReplayIds() + database.readTombstones()
        var completed = 0
        val pendingByMatch = if (scanAllPending) {
            database.readAllPending().groupBy(PendingHistoryRecord::matchId)
        } else {
            val ended = database.readTerminalIds() + snapshot.historyRecordingState.terminalByMatchId.keys.map { it.toString() }
            (ended - staged - active - gaps - stopped - archived).associateWith(database::readPending).filterValues { it.isNotEmpty() }
        }
        pendingByMatch.forEach { (matchId, records) ->
            if (matchId in staged || matchId in active || matchId in gaps || matchId in stopped || matchId in archived) return@forEach
            try {
                val events = records.map(::decodeRecord)
                if (events.lastOrNull()?.fact !is HistoryFact.ReturnedToRoom) return@forEach
                check(events.first().sequence == 1L && events.zipWithNext().all { (a, b) -> a.sequence + 1 == b.sequence }) {
                    "History event sequence is incomplete"
                }
                val replay = CompactReplayCodec.encodeCompact(events, mapper, registries, json)
                CompactReplayCodec.decodeCompact(replay)
                val archive = buildArchive(events, replay)
                if (database.archive(archive)) {
                    completed++
                    onArchived(archive)
                }
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

    /**
     * 從開局、換局、終局事實建立可查閱摘要，不猜測不存在的維度位置。
     *
     * @param events 已驗證完整且依序排列的單場歷史事實。
     * @param replay 已完成編碼與解碼驗證的 Replay。
     * @return 包含規則排名投影與局級時間的原子封存資料。
     */
    private fun buildArchive(events: List<HistoryOutboxEvent>, replay: JsonObject): HistoryArchiveRecord {
        val start = events.first()
        val opening = start.fact as HistoryFact.MatchStarted
        val completion = events.first { it.fact is HistoryFact.MatchCompleted }
        val completionFact = completion.fact as HistoryFact.MatchCompleted
        val rounds = mutableListOf<HistoryRoundRecord>()
        events.forEach { event ->
            when (event.fact) {
                is HistoryFact.MatchStarted, is HistoryFact.RoundStarted -> rounds += HistoryRoundRecord(rounds.size + 1, event.occurredAtEpochMillis, null)
                is HistoryFact.RoundCompleted -> {
                    val last = rounds.last()
                    rounds[rounds.lastIndex] = last.copy(endedAtEpochMillis = event.occurredAtEpochMillis)
                }
                else -> Unit
            }
        }
        val players = opening.tableState.players.sortedBy { it.initialSeatIndex }.map {
            HistoryParticipantRecord(it.initialSeatIndex, it.id.toString(), opening.aiPlayerStrategyKeys[it.id])
        }
        val projectedResults = HistoryResultProjector.project(events, moduleRegistry).associateBy { it.playerId.toString() }
        val participantResults = players.map { participant ->
            val projected = projectedResults[participant.playerId]
            HistoryParticipantResultRecord(
                seatIndex = participant.seatIndex,
                finalScore = projected?.finalScore ?: completionFact.finalScoresByPlayerId[Uuid.parse(participant.playerId)],
                finalRank = projected?.finalRank,
            )
        }
        return HistoryArchiveRecord(
            matchId = start.matchId.toString(),
            tableId = start.venueId.toString(),
            ruleId = moduleRegistry.getModule(opening.tableState.config).id,
            dimensionId = locations.get(start.venueId)?.location?.dimensionId,
            startedAtEpochMillis = start.occurredAtEpochMillis,
            endedAtEpochMillis = completion.occurredAtEpochMillis,
            participants = players,
            rounds = rounds,
            replayPayload = json.encodeToString(JsonObject.serializer(), replay),
            participantResults = participantResults,
        )
    }
}

/** 待寫佇列中的事件與資料庫中同一序號內容的比對結果。 */
internal sealed interface StagedHistoryEventComparison {
    /** 內容相同。 */
    data object Matches : StagedHistoryEventComparison

    /** 內容不同。 */
    data object Conflicts : StagedHistoryEventComparison

    /**
     * 待寫事件無法編碼，無法比對。
     *
     * @property error 編碼時的例外。
     */
    data class EncodingFailed(val error: Exception) : StagedHistoryEventComparison
}

/**
 * 把待寫事件編碼後與資料庫中的內容比對；編碼失敗與內容不同分開回報。
 *
 * @param persistedPayload 資料庫中同一序號的內容。
 * @param encode 編碼待寫事件。
 * @return 比對結果。
 */
internal fun compareStagedHistoryEvent(persistedPayload: String, encode: () -> String): StagedHistoryEventComparison {
    val encoded = try {
        encode()
    } catch (error: Exception) {
        return StagedHistoryEventComparison.EncodingFailed(error)
    }
    return if (encoded == persistedPayload) StagedHistoryEventComparison.Matches else StagedHistoryEventComparison.Conflicts
}

/**
 * 由資料庫事件與待寫佇列事件的序號聯集，推出一場的最早缺口；待寫事件可能剛好補足資料庫的缺口。
 *
 * 彙總已證明資料庫序號正好是 1..最大序號時，不需要逐一列出資料庫序號；否則由 [persistedSequences] 提供完整清單。缺口
 * 判定涵蓋到權威存檔已指派的最後一個序號為止。
 *
 * @param summary 資料庫序號彙總；資料庫沒有這場的事件時為 null。
 * @param persistedSequences 資料庫序號清單；[summary] 已證明從 1 起連續時為 null。
 * @param stagedSequences 待寫佇列中的序號。
 * @param nextSequence 權威存檔的下一個序號。
 * @return 最早缺少的序號；沒有缺口時為 null。
 */
internal fun firstMissingSequence(
    summary: PendingSequenceSummary?,
    persistedSequences: List<Long>?,
    stagedSequences: List<Long>,
    nextSequence: Long,
): Long? {
    val provenThrough = if (persistedSequences == null && summary != null) summary.last else 0L
    val listed = (persistedSequences.orEmpty() + stagedSequences).filter { it > provenThrough }.distinct().sorted()
    var expected = provenThrough + 1
    listed.forEach { sequence -> if (sequence == expected) expected++ }
    val highest = maxOf(summary?.last ?: 0L, stagedSequences.maxOrNull() ?: 0L, nextSequence - 1)
    return expected.takeIf { it <= highest }
}
