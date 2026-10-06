package com.doublemoon1119.mahjongcraft.flow.persistence.format.history.replay

import com.doublemoon1119.mahjongcraft.flow.common.game.history.HistoryFact
import com.doublemoon1119.mahjongcraft.flow.common.game.history.HistoryOutboxEvent
import com.doublemoon1119.mahjongcraft.flow.common.game.history.HistoryRecordingState
import com.doublemoon1119.mahjongcraft.flow.common.game.history.HistoryTableResult
import com.doublemoon1119.mahjongcraft.flow.common.game.model.GameConfig
import com.doublemoon1119.mahjongcraft.flow.persistence.format.config.GameFlowConfigPersistenceDto
import com.doublemoon1119.mahjongcraft.flow.persistence.format.config.toDomain
import com.doublemoon1119.mahjongcraft.flow.persistence.format.core.TypedPersistenceDto
import com.doublemoon1119.mahjongcraft.flow.persistence.format.game.toPersistenceDto
import com.doublemoon1119.mahjongcraft.flow.persistence.format.history.HistoryOutboxEventPersistenceDto
import com.doublemoon1119.mahjongcraft.flow.persistence.format.history.HistoryRecordingPersistenceMapper
import com.doublemoon1119.mahjongcraft.flow.persistence.format.registry.PersistenceRegistries
import com.doublemoon1119.mahjongcraft.logic.table.TableState
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.encodeToJsonElement
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlin.uuid.Uuid

/**
 * 將一整場已完成的權威歷史封裝成可查閱的精簡 Replay 文件。
 *
 * 此編碼器只在整場事件已完整保存後使用；對局進行中的 outbox 仍使用原本的
 * [HistoryOutboxEventPersistenceDto] 格式。文件不包含逐張牌的原 UUID，只保留事件
 * 所需的 UUID 與相對序號，並以陣列欄位避免重複儲存事件封套。
 */
object CompactReplayCodec {
    /** 此 Replay 文件格式的版本號。 */
    const val FORMAT_VERSION: Int = 1

    /**
     * 只解碼已完成 Replay 的 header 規則設定，不重建交易或完整牌局投影。
     *
     * @param document 已封存的精簡 Replay 文件。
     * @param registries 開局規則 persistence DTO 的註冊表。
     * @param expectedMatchId 呼叫端已授權的對局 ID；不一致時拒絕文件。
     * @param json Replay persistence DTO 的 JSON 設定。
     * @return Replay 開局時保存的完整遊戲設定。
     */
    fun decodeRuleSettings(
        document: JsonObject,
        registries: PersistenceRegistries,
        expectedMatchId: Uuid,
        json: Json = Json,
    ): GameConfig {
        require(document.keys == setOf(ReplayFormatKeys.FORMAT_VERSION, ReplayFormatKeys.PAYLOAD)) {
            "Replay document contains unexpected fields"
        }
        require(document[ReplayFormatKeys.FORMAT_VERSION]?.jsonPrimitive?.intOrNull == FORMAT_VERSION) {
            "Unsupported replay format version"
        }
        val payload = document[ReplayFormatKeys.PAYLOAD] as? JsonObject ?: error("Replay payload must be an object")
        val content = CompactReplayDictionary.decodeFields(
            payload,
            setOf(ReplayFormatKeys.VERSION, ReplayFormatKeys.HEADER),
        )
        require(content.keys == setOf(ReplayFormatKeys.VERSION, ReplayFormatKeys.HEADER)) {
            "Replay content lacks rule settings header"
        }
        require(content[ReplayFormatKeys.VERSION]?.jsonPrimitive?.intOrNull == FORMAT_VERSION) {
            "Unsupported replay content version"
        }
        val header = content[ReplayFormatKeys.HEADER] as? JsonObject ?: error("Replay header must be an object")
        val matchId = header[ReplayFormatKeys.MATCH]?.jsonPrimitive?.contentOrNull
            ?.let { runCatching { Uuid.parse(it) }.getOrNull() }
            ?: error("Replay header lacks a valid match ID")
        require(expectedMatchId == matchId) { "Replay match ID does not match the request" }
        val ruleDto = header[ReplayFormatKeys.RULE]?.let {
            json.decodeFromJsonElement(TypedPersistenceDto.serializer(), it)
        } ?: error("Replay header lacks rule configuration")
        val flowDto = header[ReplayFormatKeys.FLOW]?.let {
            json.decodeFromJsonElement(GameFlowConfigPersistenceDto.serializer(), it)
        } ?: error("Replay header lacks flow configuration")
        return GameConfig(
            ruleConfig = registries.ruleConfigs.decode(ruleDto, json),
            flowConfig = flowDto.toDomain(),
        )
    }

    /**
     * 將完整對局編碼為局內牌索引、玩家座位索引與逐交易投影差異。
     *
     * @param events 同一場對局的完整權威事件。
     * @param mapper 將權威事件轉為持久化 DTO 的映射器。
     * @param registries 將桌況與牌種轉為持久化資料所需的註冊表。
     * @param json Replay 內部使用的 JSON 設定。
     * @return 不包含逐張牌 UUID 的精簡 Replay 文件。
     */
    fun encodeCompact(
        events: List<HistoryOutboxEvent>,
        mapper: HistoryRecordingPersistenceMapper,
        registries: PersistenceRegistries,
        json: Json = Json,
    ): JsonObject {
        require(events.isNotEmpty()) { "Replay requires at least one history event" }
        validateDomainEvents(events)
        val encodedEvents = mapper.encode(HistoryRecordingState(pendingEvents = events)).pendingEvents
        require(encodedEvents.size == events.size) { "Replay contains an event that cannot be encoded" }
        val serialized = encodedEvents.map { json.encodeToJsonElement(HistoryOutboxEventPersistenceDto.serializer(), it).jsonObject }
        val first = events.first()
        val typeDictionary = linkedMapOf<String, JsonElement>()
        val factTypes = linkedMapOf<String, Int>()
        val actionTypes = linkedMapOf<String, Int>()
        val patchPaths = linkedMapOf<List<JsonElement>, Int>()
        val rounds = mutableListOf<MutableRound>()
        val players = mutableListOf<JsonElement>()
        var ruleConfig: JsonElement? = null
        var flowConfig: JsonElement? = null
        var current: TableState? = null
        var roundIndex: RoundIndex? = null
        var previousTime = first.occurredAtEpochMillis
        val transactions = events.indices.groupBy { events[it].transactionFirstSequence }.values
        for (indices in transactions) {
            val txEvents = indices.map { events[it] }
            val txJson = indices.map { serialized[it] }
            var parts = linkedMapOf<String, JsonElement>()
            val time = txEvents.first().occurredAtEpochMillis
            if (time != previousTime) parts[ReplayFormatKeys.TIME_DELTA] = JsonPrimitive(time - previousTime)
            previousTime = time
            var openedRound = false
            var tableChanged = false
            val facts = mutableListOf<Pair<JsonObject, String?>>()
            for ((event, encoded) in txEvents.zip(txJson)) {
                when (val fact = event.fact) {
                    is HistoryFact.MatchStarted, is HistoryFact.RoundStarted -> {
                        check(!openedRound && !tableChanged) { "A transaction cannot open multiple rounds" }
                        // 同一筆權威交易中先於開局的事實（例如上一局結束）屬於上一局，作為上一局的最後一筆交易保存。
                        if (facts.isNotEmpty()) {
                            putFacts(parts, facts, checkNotNull(roundIndex) { "Replay event precedes a round opening" }, factTypes, actionTypes)
                            rounds.last().transactions += JsonObject(parts)
                            parts = linkedMapOf()
                            facts.clear()
                        }
                        current = when (fact) {
                            is HistoryFact.MatchStarted -> fact.tableState
                            is HistoryFact.RoundStarted -> fact.tableState
                        }
                        val openingState = checkNotNull(current)
                        val stateJson = (encoded[ReplaySourceKeys.FACT] as JsonObject)[ReplaySourceKeys.STATE]?.jsonObject
                            ?: error("Replay opening fact lacks state")
                        if (ruleConfig == null) {
                            ruleConfig = stateJson[ReplaySourceKeys.CONFIG]
                            flowConfig = (encoded[ReplaySourceKeys.FACT] as JsonObject)[ReplaySourceKeys.FLOW_CONFIG]
                            val aiStrategyKeys = checkNotNull((fact as? HistoryFact.MatchStarted)?.aiPlayerStrategyKeys) {
                                "Replay must open with match start"
                            }
                            players += openingState.players.sortedBy { it.initialSeatIndex }.map { player ->
                                JsonObject(mapOf(ReplaySourceKeys.ID to JsonPrimitive(player.id.toString()), ReplayFormatKeys.PLAYER_AI to (aiStrategyKeys[player.id]?.let(::JsonPrimitive) ?: JsonNull)))
                            }
                        }
                        val openingIndex = createRoundIndex(openingState, json, typeDictionary)
                        roundIndex = openingIndex
                        rounds += MutableRound(rounds.size + 1, openingIndex.tileTypes.toList(), project(openingState, openingIndex, registries, json), mutableListOf())
                        parts[ReplayFormatKeys.ROUND_OPENING] = JsonPrimitive(true)
                        openedRound = true
                    }
                    is HistoryFact.TableChanged -> {
                        check(!tableChanged) { "A transaction must contain one table result" }
                        current = when (val result = fact.result) {
                            is HistoryTableResult.Change -> result.change.applyTo(checkNotNull(current))
                            is HistoryTableResult.Checkpoint -> result.tableState
                        }
                        tableChanged = true
                    }
                    is HistoryFact.ActionAccepted -> {
                        val actionFact = encoded[ReplaySourceKeys.FACT]?.jsonObject ?: error("Replay action fact must be an object")
                        val result = actionFact[ReplaySourceKeys.RESULT] as? JsonObject ?: error("Replay action result must be an object")
                        val directTiles = result[ReplaySourceKeys.AFFECTED_TILE_IDS] as? JsonArray ?: error("Replay affected tiles must be an array")
                        val revealedTiles = result[ReplaySourceKeys.NEWLY_REVEALED_TILE_IDS] as? JsonArray ?: error("Replay revealed tiles must be an array")
                        val reduced = actionFact.filterKeys { it != ReplaySourceKeys.RESULT }.toMutableMap()
                        if (directTiles.isNotEmpty()) reduced[ReplaySourceKeys.DIRECT_TILES] = directTiles
                        if (revealedTiles.isNotEmpty()) reduced[ReplaySourceKeys.REVEALED_TILES] = revealedTiles
                        facts += JsonObject(reduced) to event.actorPlayerId?.toString()
                    }
                    else -> facts += (encoded[ReplaySourceKeys.FACT]?.jsonObject ?: error("Replay fact must be an object")) to event.actorPlayerId?.toString()
                }
            }
            val index = checkNotNull(roundIndex) { "Replay event precedes a round opening" }
            val state = checkNotNull(current) { "Replay transaction has no table state" }
            val declarations = declareNewTiles(state, index, json, typeDictionary)
            if (declarations.isNotEmpty()) parts[ReplayFormatKeys.NEW_TILES] = JsonArray(declarations.map(::JsonPrimitive))
            if (facts.isNotEmpty()) putFacts(parts, facts, index, factTypes, actionTypes)
            val round = rounds.last()
            if (tableChanged) {
                val after = project(state, index, registries, json)
                val before = if (openedRound) round.initial else round.replayed
                val delta = JsonDelta.diff(before, after)
                val patch = delta?.let { FlatPatchCodec.encode(it, patchPaths) }
                parts[ReplayFormatKeys.PATCH] = patch ?: JsonNull
                round.replayed = patch?.let { FlatPatchCodec.apply(before, it, patchPaths.keys.toList()) } ?: before
            } else if (openedRound) {
                round.replayed = round.initial
            }
            round.transactions += JsonObject(parts)
        }
        val header = linkedMapOf<String, JsonElement>(
            ReplayFormatKeys.MATCH to JsonPrimitive(first.matchId.toString()), ReplayFormatKeys.VENUE to JsonPrimitive(first.venueId.toString()),
            ReplayFormatKeys.PLAYERS to JsonArray(players), ReplayFormatKeys.RULE to checkNotNull(ruleConfig), ReplayFormatKeys.FLOW to checkNotNull(flowConfig),
            ReplayFormatKeys.TYPES to JsonArray(typeDictionary.values.toList()), ReplayFormatKeys.TIME to JsonPrimitive(first.occurredAtEpochMillis),
            ReplayFormatKeys.PATCH_PATHS to JsonArray(patchPaths.keys.map(::JsonArray)), ReplayFormatKeys.FACT_TYPES to JsonArray(factTypes.keys.map(::JsonPrimitive)),
            ReplayFormatKeys.ACTION_TYPES to JsonArray(actionTypes.keys.map(::JsonPrimitive)),
        )
        val content = JsonObject(mapOf(ReplayFormatKeys.VERSION to JsonPrimitive(FORMAT_VERSION), ReplayFormatKeys.HEADER to JsonObject(header), ReplayFormatKeys.ROUNDS to JsonArray(rounds.map { it.toJson() })))
        return JsonObject(mapOf(ReplayFormatKeys.FORMAT_VERSION to JsonPrimitive(FORMAT_VERSION), ReplayFormatKeys.PAYLOAD to CompactReplayDictionary.encode(content)))
    }

    /**
     * 解碼精簡 Replay 的逐交易遊戲內容投影。
     *
     * 此方法只重建查閱所需的投影，不重新執行規則、不重建實體牌 UUID，亦不還原
     * 實體牌牆布局或逐幀呈現。
     *
     * @param document [encodeCompact] 產生的 Replay 文件。
     * @return 每局依交易順序排列的投影、語意事實與新牌宣告。
     */
    fun decodeCompact(document: JsonObject): List<List<DecodedReplayTransaction>> {
        require(document.keys == setOf(ReplayFormatKeys.FORMAT_VERSION, ReplayFormatKeys.PAYLOAD)) { "Replay document contains unexpected fields" }
        require(document[ReplayFormatKeys.FORMAT_VERSION]?.jsonPrimitive?.intOrNull == FORMAT_VERSION) { "Unsupported replay format version" }
        val payload = document[ReplayFormatKeys.PAYLOAD] as? JsonObject ?: error("Replay payload must be an object")
        val content = CompactReplayDictionary.decode(payload) as? JsonObject ?: error("Replay content must be an object")
        require(content.keys == setOf(ReplayFormatKeys.VERSION, ReplayFormatKeys.HEADER, ReplayFormatKeys.ROUNDS)) { "Replay content contains unexpected fields" }
        require(content[ReplayFormatKeys.VERSION]?.jsonPrimitive?.intOrNull == FORMAT_VERSION) { "Unsupported replay content version" }
        val header = content[ReplayFormatKeys.HEADER] as? JsonObject ?: error("Replay header must be an object")
        val players = header[ReplayFormatKeys.PLAYERS] as? JsonArray ?: error("Replay header lacks players")
        require(players.isNotEmpty()) { "Replay requires at least one player" }
        val tileTypes = header[ReplayFormatKeys.TYPES] as? JsonArray ?: error("Replay header lacks tile types")
        val factTypes = readDictionary(header[ReplayFormatKeys.FACT_TYPES], "fact types")
        val actionTypes = readDictionary(header[ReplayFormatKeys.ACTION_TYPES], "action types")
        val encodedPaths = header[ReplayFormatKeys.PATCH_PATHS] as? JsonArray ?: error("Replay header lacks patch paths")
        val paths = encodedPaths.map { path ->
            val segments = path as? JsonArray ?: error("Replay patch path must be an array")
            segments.toList()
        }
        val rounds = content[ReplayFormatKeys.ROUNDS] as? JsonArray ?: error("Replay rounds must be an array")
        return rounds.map { roundElement ->
            val round = roundElement as? JsonObject ?: error("Replay round must be an object")
            var projection = round[ReplayFormatKeys.INITIAL] ?: error("Replay round lacks initial projection")
            val openingTiles = round[ReplayFormatKeys.TILES] as? JsonArray ?: error("Replay round lacks tile types")
            openingTiles.forEach { requireTypeIndex(it, tileTypes.size) }
            val transactions = round[ReplayFormatKeys.TRANSACTIONS] as? JsonArray ?: error("Replay transactions must be an array")
            transactions.map { transactionElement ->
                val transaction = transactionElement as? JsonObject ?: error("Replay transaction must be an object")
                require(transaction.keys.all { it in setOf(ReplayFormatKeys.TIME_DELTA, ReplayFormatKeys.ROUND_OPENING, ReplayFormatKeys.NEW_TILES, ReplayFormatKeys.FACTS, ReplayFormatKeys.ACTORS, ReplayFormatKeys.PATCH) }) { "Replay transaction contains unexpected fields" }
                val newTileTypes = when (val declared = transaction[ReplayFormatKeys.NEW_TILES]) {
                    null -> emptyList()
                    is JsonArray -> declared.map { requireTypeIndex(it, tileTypes.size) }
                    else -> error("Replay new tile types must be an array")
                }
                val facts = decodeFacts(transaction[ReplayFormatKeys.FACTS], factTypes, actionTypes)
                val actorSeats = when (val encodedActors = transaction[ReplayFormatKeys.ACTORS]) {
                    null -> List(facts.size) { null }
                    is JsonArray -> {
                        require(encodedActors.size == facts.size) { "Replay actor count does not match fact count" }
                        encodedActors.map { actor ->
                            if (actor == JsonNull) {
                                null
                            } else {
                                val seat = (actor as? JsonPrimitive)?.intOrNull ?: error("Replay actor seat must be an integer")
                                require(seat in players.indices) { "Replay actor seat is out of range" }
                                seat
                            }
                        }
                    }
                    is JsonPrimitive -> {
                        val seat = encodedActors.intOrNull ?: error("Replay actor seat must be an integer")
                        require(seat in players.indices) { "Replay actor seat is out of range" }
                        List(facts.size) { seat }
                    }
                    else -> error("Replay actors must be an array or integer")
                }
                val patch = transaction[ReplayFormatKeys.PATCH]
                if (patch != null && patch !is JsonNull) {
                    projection = FlatPatchCodec.apply(projection, patch as? JsonArray ?: error("Replay patch must be an array"), paths)
                }
                DecodedReplayTransaction(projection, facts, actorSeats, newTileTypes)
            }
        }
    }

    /**
     * 驗證種類字典的內容與型別。
     *
     * @param value 待讀取的字典 JSON 值。
     * @param name 用於錯誤訊息的字典名稱。
     * @return 依索引排列的種類識別碼。
     */
    private fun readDictionary(value: JsonElement?, name: String): List<String> {
        val entries = value as? JsonArray ?: error("Replay header lacks $name")
        return entries.map { entry ->
            val primitive = entry as? JsonPrimitive ?: error("Replay $name entry must be a string")
            require(primitive.isString) { "Replay $name entry must be a string" }
            primitive.content
        }
    }

    /**
     * 驗證牌種索引並回傳其整數值。
     *
     * @param value 待驗證的 JSON 值。
     * @param typeCount 可引用的牌種數量。
     * @return 有效的牌種索引。
     */
    private fun requireTypeIndex(value: JsonElement, typeCount: Int): Int {
        val index = (value as? JsonPrimitive)?.intOrNull ?: error("Replay tile type index must be an integer")
        require(index in 0 until typeCount) { "Replay tile type index is out of range" }
        return index
    }

    /**
     * 解碼交易中的單筆或多筆語意事實。
     *
     * @param value 交易中的事實資料；缺少時代表沒有語意事實。
     * @param factTypes 依索引排列的語意事實種類。
     * @param actionTypes 依索引排列的動作種類。
     * @param budget 有界讀取預算；完整解碼時為 null。
     * @return 依原順序排列的語意事實。
     */
    internal fun decodeFacts(value: JsonElement?, factTypes: List<String>, actionTypes: List<String>, budget: ReplayReadBudget? = null): List<JsonObject> {
        if (value == null) return emptyList()
        val encoded = value as? JsonArray ?: error("Replay facts must be an array")
        if (budget != null && encoded.firstOrNull() is JsonArray && encoded.size > budget.limits.maxFactsPerTransaction) throw ReplayReadException(ReplayReadError.LIMIT_EXCEEDED)
        val facts = if (encoded.firstOrNull() is JsonArray) {
            encoded.map { it as? JsonArray ?: error("Replay fact must be an array") }
        } else {
            listOf(encoded)
        }
        if (budget != null && facts.size > budget.limits.maxFactsPerTransaction) throw ReplayReadException(ReplayReadError.LIMIT_EXCEEDED)
        return facts.map {
            budget?.charge(it.size.toLong() + 1)
            CompactFactCodec.decode(it, factTypes, actionTypes)
        }
    }

    /**
     * 驗證權威事件的場次、序號、交易邊界與終局標記。
     *
     * @param events 同一場對局的完整有序事件。
     */
    private fun validateDomainEvents(events: List<HistoryOutboxEvent>) {
        require(events.first().sequence == 1L && events.first().fact is HistoryFact.MatchStarted) {
            "Replay must begin with match start at sequence one"
        }
        require(events.zipWithNext().all { (before, after) -> before.sequence + 1L == after.sequence }) {
            "Replay event sequence is not contiguous"
        }
        require(events.all { it.matchId == events.first().matchId && it.venueId == events.first().venueId }) {
            "Replay events must belong to one match and venue"
        }
        var transactionStart = 0L
        var transactionTime = Long.MIN_VALUE
        events.forEach { event ->
            if (event.transactionFirstSequence == event.sequence) {
                require(event.occurredAtEpochMillis >= transactionTime) { "Replay event time is not monotonic" }
                transactionStart = event.sequence
                transactionTime = event.occurredAtEpochMillis
            } else {
                require(event.transactionFirstSequence == transactionStart) { "Replay transaction boundary is invalid" }
                require(event.occurredAtEpochMillis == transactionTime) { "Replay transaction contains different event times" }
            }
        }
        require(events.count { it.fact is HistoryFact.MatchStarted } == 1) { "Replay must contain exactly one match start" }
        require(events.count { it.fact is HistoryFact.MatchCompleted } == 1) { "Replay must contain exactly one match completion" }
        val completionIndex = events.indexOfFirst { it.fact is HistoryFact.MatchCompleted }
        val completion = events[completionIndex]
        var tailIndex = completionIndex + 1
        // 權威交易在語意事實之後附加唯一桌況結果；終局收取供託的分數變更也屬於同一交易。
        if (events.getOrNull(tailIndex)?.fact is HistoryFact.TableChanged) {
            require(events[tailIndex].transactionFirstSequence == completion.transactionFirstSequence) {
                "Replay table result after match completion must belong to the completion transaction"
            }
            tailIndex++
            require(events.getOrNull(tailIndex)?.transactionFirstSequence != completion.transactionFirstSequence) {
                "Replay completion table result must be the last event of its transaction"
            }
        }
        require(events.drop(tailIndex).all { it.fact is HistoryFact.ReturnedToRoom }) {
            "Replay permits only return-to-room events after the completion transaction"
        }
    }

    /**
     * 將桌況建成局內牌與玩家索引。
     *
     * @param state 開局時的權威桌況。
     * @param json 牌種 DTO 的 JSON 編碼設定。
     * @param types 跨局牌種內容到字典值的可變對照。
     * @return 本局牌與玩家的索引。
     */
    private fun createRoundIndex(state: TableState, json: Json, types: MutableMap<String, JsonElement>): RoundIndex {
        val tiles = state.tileWall.getAllTiles() + state.reservedWallTiles + state.players.flatMap { it.hand.allTiles + it.discardPile.entries.map { entry -> entry.tile } }
        val tileIds = tiles.map { it.id.toString() }
        require(tileIds.size == tileIds.distinct().size) { "Replay opening contains duplicate tile identifiers" }
        val encoded = tiles.associate { it.id.toString() to json.encodeToJsonElement(it.tile.toPersistenceDto()) }
        val codes = tiles.map { tile -> types.getOrPut(encoded.getValue(tile.id.toString()).toString()) { encoded.getValue(tile.id.toString()) }.let { types.keys.indexOf(encoded.getValue(tile.id.toString()).toString()) } }
        return RoundIndex(tileIds.withIndex().associateTo(linkedMapOf()) { it.value to it.index }, codes.toMutableList(), encoded.toMutableMap(), state.players.associate { it.id.toString() to it.initialSeatIndex }, types)
    }

    /**
     * 將交易期間新出現的牌加入局內牌索引。
     *
     * @param state 交易後的權威桌況。
     * @param index 本局牌與玩家的索引。
     * @param json 牌種 DTO 的 JSON 編碼設定。
     * @param types 跨局牌種內容到字典值的可變對照。
     * @return 本次交易新宣告牌的牌種索引。
     */
    private fun declareNewTiles(state: TableState, index: RoundIndex, json: Json, types: MutableMap<String, JsonElement>): List<Int> {
        val visible = state.tileWall.getAllTiles() + state.reservedWallTiles + state.players.flatMap { it.hand.allTiles + it.discardPile.entries.map { entry -> entry.tile } }
        val result = mutableListOf<Int>()
        visible.forEach { identified ->
            val id = identified.id.toString()
            val tileJson = json.encodeToJsonElement(identified.tile.toPersistenceDto())
            if (id in index.tileIds) {
                require(index.encodedTiles[id] == tileJson) { "Replay tile type changed for an existing tile" }
            } else {
                val typeCode = types.getOrPut(tileJson.toString()) { tileJson }.let { types.keys.indexOf(tileJson.toString()) }
                index.tileIds[id] = index.tileTypes.size
                index.tileTypes += typeCode
                index.encodedTiles[id] = tileJson
                result += typeCode
            }
        }
        return result
    }

    /**
     * 將桌況只保留可查閱的遊戲內容，排除實體布局與動作歷史。
     *
     * @param state 待投影的權威桌況。
     * @param index 本局牌與玩家的索引。
     * @param registries 桌況 DTO 編碼所需的擴充 registry。
     * @param json 桌況 DTO 的 JSON 編碼設定。
     * @return 已將牌與玩家參照轉為索引的內容投影。
     */
    private fun project(state: TableState, index: RoundIndex, registries: PersistenceRegistries, json: Json): JsonElement {
        val encoded = json.encodeToJsonElement(state.toPersistenceDto(registries.ruleConfigs, registries.discardPiles, registries.playerRuleStates, registries.dynamicRuleStates, registries.exhaustiveDrawReasons, registries.extensionGameActions, json)).jsonObject
        val gameplay = encoded.filterKeys { it !in setOf(ReplaySourceKeys.ID, ReplaySourceKeys.CONFIG, ReplaySourceKeys.PHYSICAL_WALL_LAYOUT) }.toMutableMap()
        gameplay[ReplayFormatKeys.PLAYERS] = JsonArray(
            (encoded[ReplayFormatKeys.PLAYERS] as JsonArray).map { player ->
                JsonObject(
                    (player as JsonObject).filterKeys {
                        it !in setOf(ReplaySourceKeys.ID, ReplaySourceKeys.AI_STRATEGY_KEY, ReplaySourceKeys.ACTION_HISTORY)
                    },
                )
            },
        )
        return translate(JsonObject(gameplay), index)
    }

    /**
     * 將事實中的牌與玩家 UUID 翻譯成局內整數索引。
     *
     * @param fact 待轉換的序列化語意事實。
     * @param index 本局牌與玩家的索引。
     * @return 使用局內索引的語意事實。
     */
    private fun translateFact(fact: JsonObject, index: RoundIndex): JsonObject = translate(fact, index) as JsonObject

    /**
     * 將一筆交易的語意事實與行為者寫入 [parts]。
     *
     * @param parts 正在建立的交易欄位。
     * @param facts 依序排列的事實與行為者玩家 ID。
     * @param index 事實所屬局的牌與玩家索引。
     * @param factTypes 事實種類字典。
     * @param actionTypes 動作種類字典。
     */
    private fun putFacts(
        parts: MutableMap<String, JsonElement>,
        facts: List<Pair<JsonObject, String?>>,
        index: RoundIndex,
        factTypes: MutableMap<String, Int>,
        actionTypes: MutableMap<String, Int>,
    ) {
        val compactFacts = facts.map { (fact, _) -> translateFact(fact, index) }
        parts[ReplayFormatKeys.FACTS] = if (compactFacts.size == 1) CompactFactCodec.encode(compactFacts.single(), factTypes, actionTypes) else JsonArray(compactFacts.map { CompactFactCodec.encode(it, factTypes, actionTypes) })
        val actors = facts.map { (_, actorId) ->
            actorId?.let { JsonPrimitive(index.playerIds[it] ?: error("Replay actor is not a match player: $it")) } ?: JsonNull
        }
        if (actors.any { it != JsonNull }) {
            parts[ReplayFormatKeys.ACTORS] = if (actors.distinct().size == 1) actors.first() else JsonArray(actors)
        }
    }

    /**
     * 遞迴翻譯牌、玩家及牌種參照。
     *
     * @param element 待轉換的 JSON 節點。
     * @param index 本局牌與玩家的索引。
     * @return 使用局內索引的 JSON 節點。
     */
    private fun translate(element: JsonElement, index: RoundIndex): JsonElement = when (element) {
        is JsonObject -> {
            val typeCode = index.typeDictionary.keys.indexOf(element.toString())
            val tileId = (element[ReplaySourceKeys.ID] as? JsonPrimitive)?.content
            if (typeCode >= 0) {
                JsonPrimitive(typeCode)
            } else if (tileId != null && element.size == 2 && ReplaySourceKeys.TILE in element && tileId in index.tileIds) {
                JsonPrimitive(index.tileIds.getValue(tileId))
            } else {
                JsonObject(
                    element.map { (key, value) ->
                        val typeKey = (element[ReplaySourceKeys.TYPE_KEY] as? JsonPrimitive)?.content
                        val opaque = key == ReplayFormatKeys.PAYLOAD && typeKey != null && !typeKey.startsWith(ReplaySourceKeys.BUILTIN_TYPE_PREFIX)
                        (if (opaque) key else translateKey(key, index)) to (if (opaque) value else translate(value, index))
                    }.toMap(),
                )
            }
        }
        is JsonArray -> JsonArray(element.map { translate(it, index) })
        is JsonPrimitive -> if (!element.isString || !UUID_PATTERN.matches(element.content)) {
            element
        } else {
            when {
                element.content in index.tileIds -> JsonPrimitive(index.tileIds.getValue(element.content))
                element.content in index.playerIds -> JsonPrimitive(index.playerIds.getValue(element.content))
                else -> error("Undeclared UUID in replay payload: ${element.content}")
            }
        }
    }

    /**
     * 翻譯物件鍵中的牌與玩家 UUID。
     *
     * @param key 待轉換的 JSON 欄位名稱。
     * @param index 本局牌與玩家的索引。
     * @return 對應的索引字串，或原本不是 UUID 的欄位名稱。
     */
    private fun translateKey(key: String, index: RoundIndex): String = when {
        !UUID_PATTERN.matches(key) -> key
        key in index.tileIds -> index.tileIds.getValue(key).toString()
        key in index.playerIds -> index.playerIds.getValue(key).toString()
        else -> error("Undeclared UUID key in replay payload: $key")
    }

    /**
     * 單局牌身分、牌種與座位的編碼索引。
     *
     * @property tileIds 實體牌 UUID 到局內索引的對照。
     * @property tileTypes 依局內索引排列的牌種代碼。
     * @property encodedTiles 實體牌 UUID 對應的牌種資料，用於驗證牌種不變。
     * @property playerIds 玩家 UUID 到初始座位索引的對照。
     * @property typeDictionary 跨局共用的牌種資料字典。
     */
    private data class RoundIndex(
        val tileIds: MutableMap<String, Int>,
        val tileTypes: MutableList<Int>,
        val encodedTiles: MutableMap<String, JsonElement>,
        val playerIds: Map<String, Int>,
        val typeDictionary: MutableMap<String, JsonElement>,
    )

    /**
     * 編碼期間累積的單局內容與交易。
     *
     * @property number 這一局在整場對局中的順序號，從 1 開始；連莊或續局也會遞增，與封存時的局列表一致。賽程局位保存在開局投影中。
     * @property tileTypes 開局時依局內牌索引排列的牌種代碼。
     * @property initial 開局時可查閱的遊戲內容投影。
     * @property transactions 已編碼的有序交易。
     * @property replayed 最近一筆交易後的遊戲內容投影。
     */
    private data class MutableRound(
        val number: Int,
        val tileTypes: List<Int>,
        val initial: JsonElement,
        val transactions: MutableList<JsonElement>,
        var replayed: JsonElement = initial,
    ) {
        /** 將累積內容轉為單局 JSON 文件。 */
        fun toJson(): JsonObject = JsonObject(mapOf(ReplayFormatKeys.ROUND_NUMBER to JsonPrimitive(number), ReplayFormatKeys.TILES to JsonArray(tileTypes.map(::JsonPrimitive)), ReplayFormatKeys.INITIAL to initial, ReplayFormatKeys.TRANSACTIONS to JsonArray(transactions)))
    }

    /** 產生可逆的物件及列表差異，供路徑字典編碼。 */
    private object JsonDelta {
        /**
         * 比較兩份 JSON 值並回傳可套用的結構差異。
         *
         * @param before 交易前的 JSON 投影。
         * @param after 交易後的 JSON 投影。
         * @return 可逆的結構差異；內容相同時為 null。
         */
        fun diff(before: JsonElement, after: JsonElement): JsonElement? {
            if (before == after) return null
            if (before is JsonObject && after is JsonObject) {
                val fields = linkedMapOf<String, JsonElement>()
                (before.keys + after.keys).distinct().forEach { key ->
                    when {
                        key !in after -> fields[key] = JsonObject(mapOf(ReplayPatchKeys.DELETE to JsonPrimitive(true)))
                        key !in before -> fields[key] = JsonObject(mapOf(ReplayPatchKeys.VALUE to after.getValue(key)))
                        else -> diff(before.getValue(key), after.getValue(key))?.let { fields[key] = it }
                    }
                }
                return if (fields.isEmpty()) null else JsonObject(mapOf(ReplayPatchKeys.OBJECT to JsonObject(fields)))
            }
            if (before is JsonArray && after is JsonArray) {
                val prefix = before.zip(after).takeWhile { (old, new) -> old == new }.size
                val commonLimit = minOf(before.size, after.size) - prefix
                val suffix = (0 until commonLimit).takeWhile { offset ->
                    before[before.lastIndex - offset] == after[after.lastIndex - offset]
                }.count()
                val splice = JsonObject(
                    mapOf(
                        ReplayPatchKeys.SPLICE to JsonArray(
                            listOf(
                                JsonPrimitive(prefix),
                                JsonPrimitive(before.size - prefix - suffix),
                                JsonArray(after.subList(prefix, after.size - suffix)),
                                JsonPrimitive(suffix),
                            ),
                        ),
                    ),
                )
                if (before.size == after.size) {
                    val changes = JsonObject(
                        before.indices.mapNotNull { index ->
                            diff(before[index], after[index])?.let { index.toString() to it }
                        }.toMap(),
                    )
                    val indexed = JsonObject(mapOf(ReplayPatchKeys.LIST to changes))
                    if (indexed.toString().length < splice.toString().length) return indexed
                }
                return splice
            }
            return JsonObject(mapOf(ReplayPatchKeys.VALUE to after))
        }
    }

    /** 常用 UUID 文字格式。 */
    private val UUID_PATTERN = Regex("[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}")
}
