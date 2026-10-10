package com.doublemoon1119.mahjongcraft.flow.persistence.format.history.replay

import com.doublemoon1119.mahjongcraft.flow.common.game.history.HistoryFactTypeKeys
import com.doublemoon1119.mahjongcraft.flow.common.game.history.replay.HistoryActionTypeKeys
import com.doublemoon1119.mahjongcraft.flow.common.game.history.replay.HistoryReplayFact
import com.doublemoon1119.mahjongcraft.flow.common.game.history.replay.HistoryReplayTransaction
import com.doublemoon1119.mahjongcraft.flow.common.game.history.replay.HistoryRoundEvents
import com.doublemoon1119.mahjongcraft.flow.common.game.history.replay.HistoryRoundOutcome
import com.doublemoon1119.mahjongcraft.flow.common.game.history.replay.HistoryRoundPosition
import com.doublemoon1119.mahjongcraft.flow.common.game.history.replay.HistoryRoundState
import com.doublemoon1119.mahjongcraft.flow.common.game.history.replay.HistoryRoundTileCatalog
import com.doublemoon1119.mahjongcraft.logic.base.Tile
import com.doublemoon1119.mahjongcraft.logic.table.RoundCompletionClassification
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.yield
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.longOrNull
import kotlin.uuid.Uuid

/**
 * 在獨立工作預算內重建指定局的位置或有限交易序列，不保存全場桌況快照。
 *
 * 輸入須已由外部解析；原始位元組及 JSON parser 的限制由呼叫端負責。
 * 取消例外直接傳播，破損文件不產生部分成功結果。
 *
 * @property registry 呼叫端明確提供的歷史投影轉換器。
 * @property limits 每次讀取獨立套用的資源上限。
 */
class CompactReplayRoundReader(
    private val registry: HistoryReplayProjectionRegistry,
    private val limits: ReplayReadLimits = ReplayReadLimits(),
) {
    /**
     * 讀取指定局的一段交易；僅保留要求的交易，不還原其他局的桌況。
     * @param document 已解析的 Replay 文件。
     * @param expectedMatchId 欲讀取的對局識別碼。
     * @param roundNumber 保存的局序號。
     * @param startIndex 起始交易索引，等於交易總數時回傳空序列。
     * @param limit 本次最多回傳的交易數，範圍為 1 至 20。
     * @return 有界讀取結果。
     */
    suspend fun readEvents(document: JsonObject, expectedMatchId: Uuid, roundNumber: Int, startIndex: Int, limit: Int): ReplayReadResult<HistoryRoundEvents> = read { budget ->
        require(limit in 1..20) { "Replay event page size must be between 1 and 20" }
        val source = CompactReplayReadDocument.open(document, expectedMatchId, budget)
        val round = selectRound(source, roundNumber)
        if (startIndex !in 0..round.transactions.size) missing()
        val end = minOf(round.transactions.size.toLong(), startIndex.toLong() + limit).toInt()
        val cursor = cursor(source, round, budget)
        val mapper = HistoryReplayProjectionMapper(registry)
        val events = mutableListOf<HistoryReplayTransaction>()
        for (index in 0 until end) {
            yield()
            val beforeScores = scoreSnapshot(cursor.projection, source.identity.players.size, budget)
            val transaction = advance(source, round, cursor, index, budget)
            val afterScores = scoreSnapshot(cursor.projection, source.identity.players.size, budget)
            val exhaustiveDrawAction = transaction.facts.any(::isExhaustiveDrawAction)
            val facts = mapper.mapFacts(transaction.facts, transaction.actors, source.identity, round.number, catalog(cursor, budget), budget)
                .map { fact ->
                    val enriched = fact.withScoreChanges(
                        beforeScores,
                        afterScores,
                        cursor.hasWinSettlement,
                        cursor.exhaustiveDrawSettlement,
                    )
                    if (fact.typeKey == HistoryFactTypeKeys.WIN_SETTLED ||
                        (fact is HistoryReplayFact.RuleEffect && fact.outcome?.classification == RoundCompletionClassification.WIN)
                    ) {
                        cursor.hasWinSettlement = true
                    }
                    enriched
                }
            if (exhaustiveDrawAction && cursor.exhaustiveDrawSettlement == null) {
                cursor.exhaustiveDrawSettlement = ScoreSnapshot(beforeScores, afterScores)
            }
            if (index >= startIndex) events += HistoryReplayTransaction(index, cursor.time, transaction.opening, facts, cursor.tiles.size)
        }
        HistoryRoundEvents(source.identity, round.number, events.toList(), end.takeIf { it < round.transactions.size }, catalog(cursor, budget))
    }

    /**
     * 由本局初始投影重建指定交易後的唯一桌況。
     * @param document 已解析的 Replay 文件。
     * @param expectedMatchId 欲讀取的對局識別碼。
     * @param roundNumber 保存的局序號。
     * @param position 初始位置或指定交易之後。
     * @return 歷史專用桌況，不建立可操作的權威遊戲狀態。
     */
    suspend fun readState(document: JsonObject, expectedMatchId: Uuid, roundNumber: Int, position: HistoryRoundPosition): ReplayReadResult<HistoryRoundState> = read { budget ->
        val source = CompactReplayReadDocument.open(document, expectedMatchId, budget)
        val round = selectRound(source, roundNumber)
        val last = when (position) {
            HistoryRoundPosition.Initial -> -1
            is HistoryRoundPosition.AfterTransaction -> position.index.also { if (it !in round.transactions.indices) missing() }
        }
        val cursor = cursor(source, round, budget)
        val mapper = HistoryReplayProjectionMapper(registry)
        for (index in 0..last) {
            yield()
            val beforeScores = scoreSnapshot(cursor.projection, source.identity.players.size, budget)
            val transaction = advance(source, round, cursor, index, budget)
            val afterScores = scoreSnapshot(cursor.projection, source.identity.players.size, budget)
            val exhaustiveDrawAction = transaction.facts.any(::isExhaustiveDrawAction)
            val facts = mapper.mapFacts(transaction.facts, transaction.actors, source.identity, round.number, catalog(cursor, budget), budget)
                .map { fact ->
                    val enriched = fact.withScoreChanges(
                        beforeScores,
                        afterScores,
                        cursor.hasWinSettlement,
                        cursor.exhaustiveDrawSettlement,
                    )
                    if (fact.typeKey == HistoryFactTypeKeys.WIN_SETTLED ||
                        (fact is HistoryReplayFact.RuleEffect && fact.outcome?.classification == RoundCompletionClassification.WIN)
                    ) {
                        cursor.hasWinSettlement = true
                    }
                    enriched
                }
            if (exhaustiveDrawAction && cursor.exhaustiveDrawSettlement == null) {
                cursor.exhaustiveDrawSettlement = ScoreSnapshot(beforeScores, afterScores)
            }
            facts.mapNotNull { fact ->
                when (fact) {
                    is HistoryReplayFact.Completion -> fact.outcome.takeUnless {
                        fact.typeKey == HistoryFactTypeKeys.MATCH_COMPLETED || fact.typeKey == HistoryFactTypeKeys.MATCH_ABORTED
                    }
                    is HistoryReplayFact.RuleEffect -> fact.outcome
                    else -> null
                }
            }.lastOrNull()?.let { cursor.outcome = it }
        }
        mapper.mapState(cursor.projection, source.identity, round.number, position, catalog(cursor, budget), budget).copy(outcome = cursor.outcome)
    }

    /** 在複製局內牌目錄前記帳，不讓交易數繞過集合複製預算。
     * @param cursor 目前局游標。
     * @param budget 本次工作預算。
     * @return 不受後續新牌宣告修改的牌目錄。
     */
    private fun catalog(cursor: Cursor, budget: ReplayReadBudget): HistoryRoundTileCatalog {
        budget.charge(cursor.tiles.size.toLong())
        return HistoryRoundTileCatalog(cursor.tiles.toList())
    }

    /**
     * 建立隔離預算並轉換格式錯誤，保留協程取消語意。
     * @param T 讀取結果型別。
     * @param operation 本次有界讀取作業。
     * @return 完整成功值或分類錯誤。
     */
    private suspend fun <T> read(operation: suspend (ReplayReadBudget) -> T): ReplayReadResult<T> {
        val context = currentCoroutineContext()
        val budget = ReplayReadBudget(limits) { context.ensureActive() }
        return try {
            val value = operation(budget)
            context.ensureActive()
            ReplayReadResult.Success(value, budget.diagnostics())
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (failure: ReplayReadException) {
            ReplayReadResult.Failure(failure.error)
        } catch (_: IllegalArgumentException) {
            ReplayReadResult.Failure(ReplayReadError.INVALID_DOCUMENT)
        } catch (_: IllegalStateException) {
            ReplayReadResult.Failure(ReplayReadError.INVALID_DOCUMENT)
        } catch (_: NoSuchElementException) {
            ReplayReadResult.Failure(ReplayReadError.INVALID_DOCUMENT)
        } catch (_: ArithmeticException) {
            ReplayReadResult.Failure(ReplayReadError.INVALID_DOCUMENT)
        }
    }

    /** 以交易前後的完整投影計算本次結算分數變化。
     * @param beforeScores 交易套用前的座位分數。
     * @param afterScores 交易套用後的座位分數。
     * @param hasPriorWinSettlement 是否已有同局胡牌結算事實。
     * @param exhaustiveDrawSettlement 已保存的流局動作交易前後分數快照。
     * @return 帶有本次結算分數變化的事實。
     */
    private fun HistoryReplayFact.withScoreChanges(
        beforeScores: Map<Int, Int>,
        afterScores: Map<Int, Int>,
        hasPriorWinSettlement: Boolean,
        exhaustiveDrawSettlement: ScoreSnapshot?,
    ): HistoryReplayFact {
        if (typeKey == HistoryFactTypeKeys.MATCH_COMPLETED || typeKey == HistoryFactTypeKeys.MATCH_ABORTED) return this
        val outcome = when (this) {
            is HistoryReplayFact.Completion -> outcome
            is HistoryReplayFact.RuleEffect -> outcome
            else -> null
        } ?: return this
        // 和牌分數在較早的結算交易改變；單憑局完成摘要不能把流程推進的零變化視為和牌分差。
        if (typeKey == HistoryFactTypeKeys.ROUND_COMPLETED && outcome.classification == RoundCompletionClassification.WIN) {
            return when (this) {
                is HistoryReplayFact.Completion -> copy(outcome = outcome.copy(hasEarlierWinSettlement = hasPriorWinSettlement))
                is HistoryReplayFact.RuleEffect -> this
                else -> this
            }
        }
        val scoreBaseline = if (
            typeKey == HistoryFactTypeKeys.ROUND_COMPLETED && outcome.classification == RoundCompletionClassification.EXHAUSTIVE_DRAW
        ) {
            exhaustiveDrawSettlement
        } else {
            null
        }
        val effectiveBeforeScores = scoreBaseline?.before ?: beforeScores
        val effectiveAfterScores = scoreBaseline?.after ?: afterScores
        require(effectiveBeforeScores.keys == effectiveAfterScores.keys)
        val changes = effectiveAfterScores.mapValues { (seat, score) ->
            val difference = score.toLong() - effectiveBeforeScores.getValue(seat).toLong()
            require(difference in Int.MIN_VALUE.toLong()..Int.MAX_VALUE.toLong()) { "Replay score change is out of range" }
            difference.toInt()
        }
        val settledScores = if (typeKey == HistoryFactTypeKeys.WIN_SETTLED && outcome.scoresBySeat.isEmpty()) effectiveAfterScores else outcome.scoresBySeat
        val updated = outcome.copy(scoresBySeat = settledScores, scoreChangesBySeat = changes)
        return when (this) {
            is HistoryReplayFact.Completion -> copy(outcome = updated)
            is HistoryReplayFact.RuleEffect -> copy(outcome = updated)
            else -> this
        }
    }

    /** 讀取投影中每個座位的分數，避免為每筆交易複製完整桌況。
     * @param projection 已套用至目前交易的投影。
     * @param seatCount 對局玩家數量。
     * @param budget 本次讀取工作預算。
     * @return 依座位索引排列的分數。
     */
    private fun scoreSnapshot(projection: JsonElement, seatCount: Int, budget: ReplayReadBudget): Map<Int, Int> {
        val players = (projection as? JsonObject)?.get(ReplayFormatKeys.PLAYERS) as? JsonArray ?: error("Replay players projection is invalid")
        require(players.size == seatCount)
        budget.charge(players.size.toLong())
        val scores = players.associate { value ->
            val player = value as? JsonObject ?: error("Replay player projection is invalid")
            val seat = player[ReplaySourceKeys.INITIAL_SEAT_INDEX].asInt()
            require(seat in 0 until seatCount)
            seat to player[ReplaySourceKeys.SCORE].asInt()
        }
        require(scores.size == seatCount)
        return scores
    }

    /** 將 JSON 整數讀為分數使用的 Int。
     * @return 驗證後的整數。
     */
    private fun JsonElement?.asInt(): Int {
        val value = (this as? JsonPrimitive)?.longOrNull ?: error("Replay score must be an integer")
        require(value in Int.MIN_VALUE.toLong()..Int.MAX_VALUE.toLong())
        return value.toInt()
    }

    /**
     * 尋找唯一局資料。
     * @param source 已驗證文件。
     * @param number 要求局序號。
     * @return 選取的編碼局資料。
     */
    private fun selectRound(source: CompactReplayReadDocument, number: Int): EncodedReplayRound = source.rounds.singleOrNull { it.number == number } ?: missing()

    /**
     * 初始化局游標；前面各局只掃描時間差，不重建投影或 patch。
     * @param source 文件檢視。
     * @param round 目標局。
     * @param budget 工作預算。
     * @return 只持有一份投影的局游標。
     */
    private suspend fun cursor(source: CompactReplayReadDocument, round: EncodedReplayRound, budget: ReplayReadBudget): Cursor {
        var time = source.startedAtEpochMillis
        for (previous in source.rounds.takeWhile { it !== round }) {
            for (encoded in previous.transactions) {
                yield()
                val transaction = source.dictionary.fields(encoded)
                time = addTime(time, transaction[ReplayFormatKeys.TIME_DELTA]?.let(source.dictionary::expand))
            }
        }
        val tiles = mutableListOf<Tile>()
        appendTiles(source.dictionary.expand(round.tileTypes), source, tiles, budget)
        val projection = source.dictionary.expand(round.initial)
        budget.inspectProjection(projection)
        return Cursor(projection, tiles, time)
    }

    /**
     * 套用一筆交易的新牌與差異，沿用完整解碼器的事實演算法。
     * @param source 文件檢視。
     * @param round 目標局。
     * @param cursor 唯一目前桌況。
     * @param index 交易索引。
     * @param budget 工作預算。
     * @return 已還原的交易事實與行為者。
     */
    private fun advance(source: CompactReplayReadDocument, round: EncodedReplayRound, cursor: Cursor, index: Int, budget: ReplayReadBudget): Transaction {
        val transaction = source.dictionary.fields(round.transactions[index])
        require(transaction.keys.all { it in TRANSACTION_KEYS }) { "Replay transaction contains unexpected fields" }
        fun expanded(key: String): JsonElement? = transaction[key]?.let(source.dictionary::expand)
        cursor.time = addTime(cursor.time, expanded(ReplayFormatKeys.TIME_DELTA))
        expanded(ReplayFormatKeys.NEW_TILES)?.let { appendTiles(it, source, cursor.tiles, budget) }
        val facts = CompactReplayCodec.decodeFacts(expanded(ReplayFormatKeys.FACTS), source.factTypes, source.actionTypes, budget)
        val actors = actors(expanded(ReplayFormatKeys.ACTORS), facts.size, source.identity.players.size, budget)
        val opening = expanded(ReplayFormatKeys.ROUND_OPENING)?.let {
            (it as? JsonPrimitive)?.takeUnless { flag -> flag.isString }?.booleanOrNull ?: error("Replay opening flag must be a boolean")
        } ?: false
        expanded(ReplayFormatKeys.PATCH)?.takeUnless { it == JsonNull }?.let {
            cursor.projection = FlatPatchCodec.applyBounded(cursor.projection, it as? JsonArray ?: error("Replay patch must be an array"), source.paths, budget)
        }
        budget.transactionRebuilt()
        return Transaction(facts, actors, opening)
    }

    /**
     * 追加本交易之前已宣告的牌，不引入未來交易的牌目錄。
     * @param value 牌種索引陣列。
     * @param source 文件牌種字典。
     * @param tiles 可識別的實體牌目錄。
     * @param budget 工作預算。
     */
    private fun appendTiles(value: JsonElement, source: CompactReplayReadDocument, tiles: MutableList<Tile>, budget: ReplayReadBudget) {
        val declared = value as? JsonArray ?: error("Replay tile declarations must be an array")
        if (tiles.size.toLong() + declared.size > limits.maxTiles) throw ReplayReadException(ReplayReadError.LIMIT_EXCEEDED)
        declared.forEach { tiles += source.tileTypes[budget.tile(CompactReplayReadDocument.integer(it), source.tileTypes.size)] }
    }

    /**
     * 驗證壓縮行為者欄位與事實數量一致。
     * @param value 單一座位、逐筆座位或缺省。
     * @param count 事實數量。
     * @param seats 玩家數。
     * @param budget 工作預算。
     * @return 與事實一對一的座位。
     */
    private fun actors(value: JsonElement?, count: Int, seats: Int, budget: ReplayReadBudget): List<Int?> = when (value) {
        null -> List(count) { null }
        is JsonArray -> {
            require(value.size == count) { "Replay actor count does not match fact count" }
            value.map { if (it == JsonNull) null else budget.seat(CompactReplayReadDocument.integer(it), seats) }
        }
        else -> {
            val seat = budget.seat(CompactReplayReadDocument.integer(value), seats)
            List(count) { seat }
        }
    }

    /** 判斷交易是否接受了會完成流局結算的動作。
     * @param fact 已解碼的單一交易事實。
     * @return 若事實是接受流局動作則為 true。
     */
    private fun isExhaustiveDrawAction(fact: JsonObject): Boolean {
        if ((fact[ReplaySourceKeys.TYPE] as? JsonPrimitive)?.content != HistoryFactTypeKeys.ACTION_ACCEPTED) return false
        val action = fact[ReplaySourceKeys.ACTION] as? JsonObject ?: return false
        return (action[ReplaySourceKeys.TYPE] as? JsonPrimitive)?.content == HistoryActionTypeKeys.EXHAUSTIVE_DRAW
    }

    /**
     * 套用可為負值的時間差並拒絕溢位。
     * @param time 前一筆時間。
     * @param delta 本筆時間差，缺省代表零。
     * @return 更新時間。
     */
    private fun addTime(time: Long, delta: JsonElement?): Long {
        val change = if (delta == null) 0L else (delta as? JsonPrimitive)?.takeUnless { it.isString }?.longOrNull ?: error("Replay time delta must be an integer")
        require((change <= 0 || time <= Long.MAX_VALUE - change) && (change >= 0 || time >= Long.MIN_VALUE - change)) { "Replay timestamp overflow" }
        return time + change
    }

    /** 回報不存在的局或交易位置。 */
    private fun missing(): Nothing = throw ReplayReadException(ReplayReadError.SELECTION_NOT_FOUND)

    /**
     * 單局重建時的唯一可變游標。
     * @property projection 目前桌況投影。
     * @property tiles 截至目前位置可識別的牌。
     * @property time 全場相對時間累加結果。
     * @property outcome 截至目前交易已保存的結算結果。
     * @property hasWinSettlement 是否已遇到較早的胡牌結算交易。
     * @property exhaustiveDrawSettlement 流局動作交易前後的分數快照。
     */
    private data class Cursor(
        var projection: JsonElement,
        val tiles: MutableList<Tile>,
        var time: Long,
        var outcome: HistoryRoundOutcome? = null,
        var hasWinSettlement: Boolean = false,
        var exhaustiveDrawSettlement: ScoreSnapshot? = null,
    )

    /** 流局結算交易前後的權威分數快照。
     * @property before 流局結算交易套用前的各座位分數。
     * @property after 流局結算交易套用後的各座位分數。
     */
    private data class ScoreSnapshot(
        val before: Map<Int, Int>,
        val after: Map<Int, Int>,
    )

    /**
     * 已還原的一筆交易資料。
     * @property facts 語意事實。
     * @property actors 對應行為者座位。
     * @property opening 開局標記。
     */
    private data class Transaction(
        val facts: List<JsonObject>,
        val actors: List<Int?>,
        val opening: Boolean,
    )

    /** 共用交易欄位契約。 */
    private companion object {
        /** 唯一可接受的交易欄位。 */
        val TRANSACTION_KEYS = setOf(ReplayFormatKeys.TIME_DELTA, ReplayFormatKeys.ROUND_OPENING, ReplayFormatKeys.NEW_TILES, ReplayFormatKeys.FACTS, ReplayFormatKeys.ACTORS, ReplayFormatKeys.PATCH)
    }
}
