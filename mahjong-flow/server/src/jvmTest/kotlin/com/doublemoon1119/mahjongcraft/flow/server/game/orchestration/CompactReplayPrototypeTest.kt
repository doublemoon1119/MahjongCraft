package com.doublemoon1119.mahjongcraft.flow.server.game.orchestration

import com.doublemoon1119.mahjongcraft.flow.common.game.history.HistoryFact
import com.doublemoon1119.mahjongcraft.flow.common.game.history.HistoryOutboxEvent
import com.doublemoon1119.mahjongcraft.flow.common.game.history.HistoryRecordingState
import com.doublemoon1119.mahjongcraft.flow.common.game.history.HistoryTableResult
import com.doublemoon1119.mahjongcraft.flow.persistence.format.game.toPersistenceDto
import com.doublemoon1119.mahjongcraft.flow.persistence.format.history.HistoryRecordingPersistenceMapper
import com.doublemoon1119.mahjongcraft.flow.persistence.format.history.replay.CompactReplayCodec
import com.doublemoon1119.mahjongcraft.flow.persistence.format.registry.buildBuiltInPersistenceRegistries
import com.doublemoon1119.mahjongcraft.logic.base.IdentifiedTile
import com.doublemoon1119.mahjongcraft.logic.base.Tile
import com.doublemoon1119.mahjongcraft.logic.base.TileTypeId
import com.doublemoon1119.mahjongcraft.logic.rules.riichi.RiichiGameLength
import com.doublemoon1119.mahjongcraft.logic.table.TableState
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.encodeToJsonElement
import kotlinx.serialization.json.jsonPrimitive
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.uuid.Uuid

/**
 * 測試用無牌 UUID、按交易分組的對局查閱原型；不接入正式保存。
 *
 * 逐交易驗證的是遊戲內容投影，不包含實體牌牆配置、動畫狀態與獨立的動作紀錄欄位。
 */
class CompactReplayPrototypeTest {
    /** 測試原型共用的 JSON 編解碼設定。 */
    private val json = Json

    /** 用於轉換內建規則資料的 persistence registries。 */
    private val registries = buildBuiltInPersistenceRegistries()

    /** 提供前一輪量測使用的通用差異工具。 */
    private val priorPrototype = CompactHistoryPrototypeTest()

    /** 將權威歷史事實轉為 persistence DTO 的 mapper。 */
    private val mapper = HistoryRecordingPersistenceMapper(registries)

    /** 驗證測試用 CBOR 編解碼器可往返基本 JSON 值。 */
    @Test
    fun `cbor comparison codec preserves supported JSON values`() {
        val value = json.parseToJsonElement("""{"a":[null,true,false,0,-1,1000,1.5,"牌"],"b":{}}""")
        assertEquals(value, JsonTreeCborCodec.decode(JsonTreeCborCodec.encode(value)))
        assertEquals(
            "a1616101",
            JsonTreeCborCodec.encode(json.parseToJsonElement("""{"a":1}""")).joinToString("") { "%02x".format(it) },
        )
    }

    /** 驗證未知欄位、擴充事件及字典跳脫仍可完整還原。 */
    @Test
    fun `dictionary and flat patch preserve unknown extension data`() {
        val before = json.parseToJsonElement("""{"custom":{"~tag":[-1,2],"tiles":[1]}}""")
        val after = json.parseToJsonElement("""{"custom":{"~tag":[-1,2],"tiles":[1,3],"payload":"~0"}}""")
        val paths = linkedMapOf<List<JsonElement>, Int>()
        val patch = FlatPatchCodec.encode(checkNotNull(priorPrototype.jsonDelta(before, after)), paths)
        assertEquals(after, FlatPatchCodec.apply(before, patch, paths.keys.toList()))
        val dictionary = CompactReplayDictionary.encode(after)
        assertEquals(after, CompactReplayDictionary.decode(dictionary))

        val factTypes = linkedMapOf<String, Int>()
        val actionTypes = linkedMapOf<String, Int>()
        val custom = json.parseToJsonElement("""{"type":"example:magic","payload":{"mana":3}}""") as JsonObject
        val action = json.parseToJsonElement(
            """{"type":"action_accepted","action":{"type":"example:cast","mana":3},"directTiles":[1]}""",
        ) as JsonObject
        for (fact in listOf(custom, action)) {
            val encoded = CompactFactCodec.encode(fact, factTypes, actionTypes)
            assertEquals(fact, CompactFactCodec.decode(encoded, factTypes.keys.toList(), actionTypes.keys.toList()))
        }
    }

    /** 驗證未宣告的牌 UUID 不會被誤認為牌種引用。 */
    @Test
    fun `undeclared tile reference fails instead of falling back to tile type`() {
        val unknown = Uuid.random().toString()
        val index = RoundIndex(mutableMapOf(), mutableListOf(), mutableMapOf(), emptyMap(), linkedMapOf())
        assertFailsWith<IllegalStateException> { translate(JsonPrimitive(unknown), index) }
    }

    /** 驗證局中新增的擴充牌必須先宣告索引才能引用。 */
    @Test
    fun `new extension tile must be declared before it can be referenced`() = runBlocking {
        val opening = RoomToRoomFullLifecycleIntegrationTest().measureFullLifecycle(RiichiGameLength.East)
            .first().first { it.fact is HistoryFact.MatchStarted }.fact as HistoryFact.MatchStarted
        val types = linkedMapOf<String, JsonElement>()
        val index = createRoundIndex(opening.tableState, types)
        val added = IdentifiedTile(Uuid.random(), Tile.Extension(TileTypeId.parse("example:flower")))
        val updated = opening.tableState.copy(
            players = opening.tableState.players.mapIndexed { position, player ->
                if (position == 0) player.copy(hand = player.hand.copy(tiles = player.hand.tiles + added)) else player
            },
        )
        assertFailsWith<IllegalStateException> { translate(JsonPrimitive(added.id.toString()), index) }
        val declarations = declareNewTiles(updated, index, types)
        assertEquals(1, declarations.size)
        assertEquals(JsonPrimitive(index.tileIds.getValue(added.id.toString())), translate(JsonPrimitive(added.id.toString()), index))
        assertTrue(types.values.any { "example:flower" in it.toString() })
    }

    /** 以兩種長度的完整對局驗證交易重建並量測格式大小。 */
    @Test
    fun `compact replay reconstructs gameplay projection for four full matches`() = runBlocking {
        for (length in listOf(RiichiGameLength.East, RiichiGameLength.TwoWinds)) {
            val matches = RoomToRoomFullLifecycleIntegrationTest().measureFullLifecycle(length)
            for (events in matches) {
                val result = encodeMatch(events)
                val flat = encodeMatch(events, flatPatches = true)
                val flatRaw = flat.document.toString()
                val flatDictionary = CompactReplayDictionary.encode(flat.document)
                assertEquals(flat.document, CompactReplayDictionary.decode(flatDictionary))
                assertReplayedProjections(CompactReplayDictionary.decode(flatDictionary) as JsonObject, flat.expectedProjections)
                val flatDictionaryRaw = flatDictionary.toString()
                val raw = result.document.toString()
                val cborBytes = JsonTreeCborCodec.encode(result.document)
                assertEquals(result.document, JsonTreeCborCodec.decode(cborBytes))
                val roundDocuments = result.document.getValue("rounds") as JsonArray
                val headerBytes = result.document.getValue("header").toString().toByteArray().size
                val initialBytes = roundDocuments.sumOf { (it as JsonObject).getValue("initial").toString().toByteArray().size }
                val transactions = roundDocuments.flatMap { (it as JsonObject).getValue("transactions") as JsonArray }
                val patchBytes = transactions.sumOf { (it as JsonObject)["p"]?.toString()?.toByteArray()?.size ?: 0 }
                val factBytes = transactions.sumOf { (it as JsonObject)["e"]?.toString()?.toByteArray()?.size ?: 0 }
                val patchFields = mutableMapOf<String, Int>()
                for (transaction in transactions) {
                    val patch = (transaction as JsonObject)["p"] as? JsonObject ?: continue
                    val fields = patch["_o"] as? JsonObject ?: continue
                    for ((field, value) in fields) patchFields.merge(field, value.toString().toByteArray().size, Int::plus)
                }
                val keyDictionary = linkedMapOf<String, Int>()
                val unusedUuidDictionary = linkedMapOf<String, Int>()
                val compressedTree = priorPrototype.compact(result.document, keyDictionary, unusedUuidDictionary)
                val roundText = result.document.getValue("rounds").toString()
                assertEquals(6, UUID_PATTERN.findAll(result.document.getValue("header").toString()).count())
                assertEquals(6, unusedUuidDictionary.size)
                val keyedDocument = JsonObject(
                    mapOf(
                        "k" to JsonArray(keyDictionary.keys.map(::JsonPrimitive)),
                        "u" to JsonArray(unusedUuidDictionary.keys.map(::JsonPrimitive)),
                        "d" to compressedTree,
                    ),
                ).toString()
                val keyedCborBytes = JsonTreeCborCodec.encode(json.parseToJsonElement(keyedDocument))
                assertEquals(json.parseToJsonElement(keyedDocument), JsonTreeCborCodec.decode(keyedCborBytes))
                assertEquals(events.size, result.reconstructedEventCount)
                assertEquals(
                    events.size,
                    transactions.sumOf { transactionElement ->
                        val transaction = transactionElement as JsonObject
                        (if ("h" in transaction) 1 else 0) +
                            ((transaction["e"] as? JsonArray)?.size ?: 0) +
                            (if ("p" in transaction) 1 else 0)
                    },
                )
                assertFalse(UUID_PATTERN.containsMatchIn(roundText))
                val production = CompactReplayCodec.encodeCompact(events, mapper, registries, json)
                val productionText = production.toString()
                val decoded = CompactReplayCodec.decodeCompact(production)
                assertEquals(result.expectedProjections, decoded.map { round -> round.map { it.projection } })
                assertTrue(
                    productionText.toByteArray().size <= 220_000,
                    "Compact Replay JSON exceeds 220 KB: ${productionText.toByteArray().size} bytes",
                )
                assertTrue(priorPrototype.gzipSize(productionText) <= 35_000, "Compact Replay gzip exceeds 35 KB")
                assertTrue(headerBytes > 0 && initialBytes > 0 && patchBytes > 0 && factBytes > 0)
                assertTrue(patchFields.isNotEmpty() && result.factCounts.isNotEmpty() && result.tileTypeCount > 0)
                assertTrue(raw.isNotEmpty() && flatRaw.isNotEmpty() && flatDictionaryRaw.isNotEmpty())
                assertTrue(keyedDocument.isNotEmpty() && keyedCborBytes.isNotEmpty() && flatDictionaryRaw.isNotEmpty())
            }
        }
    }

    /**
     * 將同一場權威歷史轉為含局內牌索引及交易封套的資料樹。
     *
     * @param events 依事件序號排序的權威歷史。
     * @param flatPatches 是否使用路徑字典、可逆事實陣列與精簡差異。
     * @return 編碼文件與逐交易驗證所需的量測資料。
     */
    private fun encodeMatch(
        events: List<HistoryOutboxEvent>,
        flatPatches: Boolean = false,
    ): Result {
        require(events.isNotEmpty())
        val encoded = mapper.encode(HistoryRecordingState(pendingEvents = events)).pendingEvents
        assertEquals(events.size, encoded.size, "Persistence mapper omitted a recorded fact")
        val encodedEvents = priorPrototype.slimActionResults(json.parseToJsonElement(json.encodeToString(encoded)) as JsonArray)
        val pairs = events.zip(encodedEvents.map { it as JsonObject })
        val first = events.first()
        val typeDictionary = linkedMapOf<String, JsonElement>()
        val rounds = mutableListOf<MutableRound>()
        val players = mutableListOf<JsonElement>()
        var ruleConfig: JsonElement? = null
        var flowConfig: JsonElement? = null
        var current: TableState? = null
        var roundIndex: RoundIndex? = null
        var expectedSequence = 1L
        var previousTransactionTime = first.occurredAtEpochMillis
        var reconstructedEvents = 0
        val factCounts = mutableMapOf<String, Int>()
        val patchPaths = linkedMapOf<List<JsonElement>, Int>()
        val factTypes = linkedMapOf<String, Int>()
        val actionTypes = linkedMapOf<String, Int>()
        for (transaction in pairs.groupBy { it.first.transactionFirstSequence }.values) {
            assertEquals(expectedSequence, transaction.first().first.transactionFirstSequence)
            val time = transaction.first().first.occurredAtEpochMillis
            assertTrue(transaction.all { it.first.occurredAtEpochMillis == time })
            val parts = linkedMapOf<String, JsonElement>()
            val timeDelta = time - previousTransactionTime
            if (timeDelta != 0L) parts["dt"] = JsonPrimitive(timeDelta)
            previousTransactionTime = time
            val facts = mutableListOf<JsonElement>()
            var tableChanged = false
            var openedRound = false
            val before = current
            for ((event, encodedEvent) in transaction) {
                assertEquals(expectedSequence++, event.sequence, "Event gap must be explicit, not silently collapsed")
                reconstructedEvents++
                assertEquals(first.matchId, event.matchId)
                assertEquals(first.tableId, event.tableId)
                val fact = event.fact
                val factJson = encodedEvent.getValue("fact") as JsonObject
                when (fact) {
                    is HistoryFact.MatchStarted, is HistoryFact.RoundStarted -> {
                        check(!openedRound && !tableChanged)
                        val state = when (fact) {
                            is HistoryFact.MatchStarted -> fact.tableState
                            is HistoryFact.RoundStarted -> fact.tableState
                        }
                        if (ruleConfig == null) {
                            val openingState = factJson.getValue("state") as JsonObject
                            ruleConfig = openingState.getValue("config")
                            flowConfig = (factJson["flowConfig"] ?: error("Match lacks flow configuration"))
                            players += state.players.sortedBy { it.initialSeatIndex }.map { player ->
                                JsonObject(
                                    mapOf(
                                        "id" to JsonPrimitive(player.id.toString()),
                                        "ai" to (player.aiStrategyKey?.let(::JsonPrimitive) ?: JsonNull),
                                    ),
                                )
                            }
                        } else {
                            assertEquals(ruleConfig, (factJson.getValue("state") as JsonObject).getValue("config"))
                        }
                        current = state
                        roundIndex = createRoundIndex(state, typeDictionary)
                        val projection = project(state, checkNotNull(roundIndex))
                        rounds += MutableRound(state.roundNumber, checkNotNull(roundIndex).tileTypes.toList(), projection)
                        parts["h"] = JsonPrimitive(true)
                        openedRound = true
                    }
                    is HistoryFact.TableChanged -> {
                        check(!tableChanged) { "A transaction must have one table result" }
                        current = when (val result = fact.result) {
                            is HistoryTableResult.Change -> result.change.applyTo(checkNotNull(current))
                            is HistoryTableResult.Checkpoint -> result.tableState
                        }
                        tableChanged = true
                    }
                    else -> {
                        facts += factJson
                        factCounts.merge(factJson.getValue("type").jsonPrimitive.content, 1, Int::plus)
                    }
                }
            }
            val declarations = declareNewTiles(checkNotNull(current), checkNotNull(roundIndex), typeDictionary)
            if (declarations.isNotEmpty()) parts["new"] = JsonArray(declarations.map(::JsonPrimitive))
            if (facts.isNotEmpty()) {
                val encodedFacts = facts.map { fact ->
                    val translated = translate(fact, checkNotNull(roundIndex)) as JsonObject
                    if (flatPatches) {
                        CompactFactCodec.encode(translated, factTypes, actionTypes).also { encodedFact ->
                            assertEquals(
                                translated,
                                CompactFactCodec.decode(encodedFact, factTypes.keys.toList(), actionTypes.keys.toList()),
                            )
                        }
                    } else {
                        translated
                    }
                }
                parts["e"] = if (flatPatches && encodedFacts.size == 1) encodedFacts.single() else JsonArray(encodedFacts)
            }
            if (tableChanged) {
                val index = checkNotNull(roundIndex)
                val afterProjection = project(checkNotNull(current), index)
                val previousProjection = if (openedRound) rounds.last().initial else rounds.last().replayed
                val delta = priorPrototype.jsonDelta(previousProjection, afterProjection)
                val encodedPatch = when {
                    flatPatches && delta != null -> FlatPatchCodec.encode(delta, patchPaths)
                    else -> delta
                }
                parts["p"] = encodedPatch ?: JsonNull
                rounds.last().replayed = when {
                    encodedPatch == null -> previousProjection
                    flatPatches -> FlatPatchCodec.apply(
                        previousProjection,
                        encodedPatch as JsonArray,
                        patchPaths.keys.toList(),
                    )
                    else -> priorPrototype.applyJsonDelta(previousProjection, encodedPatch)
                }
                assertEquals(afterProjection, rounds.last().replayed, "Replay mismatch at transaction ${transaction.first().first.sequence}")
            } else if (openedRound) {
                rounds.last().replayed = rounds.last().initial
            } else if (before != null) {
                assertEquals(project(before, checkNotNull(roundIndex)), rounds.last().replayed)
            }
            rounds.last().transactions += JsonObject(parts)
            rounds.last().expected += rounds.last().replayed
        }
        val headerFields = mutableMapOf<String, JsonElement>(
            "match" to JsonPrimitive(first.matchId.toString()),
            "table" to JsonPrimitive(first.tableId.toString()),
            "players" to JsonArray(players),
            "rule" to checkNotNull(ruleConfig),
            "flow" to checkNotNull(flowConfig),
            "types" to JsonArray(typeDictionary.values.toList()),
            "time" to JsonPrimitive(first.occurredAtEpochMillis),
        )
        if (flatPatches) headerFields["patchPaths"] = JsonArray(patchPaths.keys.map(::JsonArray))
        if (flatPatches) headerFields["factTypes"] = JsonArray(factTypes.keys.map(::JsonPrimitive))
        if (flatPatches) headerFields["actionTypes"] = JsonArray(actionTypes.keys.map(::JsonPrimitive))
        val matchHeader = JsonObject(headerFields)
        val roundPayload = JsonArray(
            rounds.map { round ->
                JsonObject(
                    mapOf(
                        "n" to JsonPrimitive(round.number),
                        "tiles" to JsonArray(round.tileTypes.map(::JsonPrimitive)),
                        "initial" to round.initial,
                        "transactions" to JsonArray(round.transactions),
                    ),
                )
            },
        )
        return Result(
            document = JsonObject(mapOf("version" to JsonPrimitive(1), "header" to matchHeader, "rounds" to roundPayload)),
            roundCount = rounds.size,
            transactionCount = rounds.sumOf { it.transactions.size },
            reconstructedEventCount = reconstructedEvents,
            tileTypeCount = typeDictionary.size,
            factCounts = factCounts.toMap(),
            expectedProjections = rounds.map { it.expected.toList() },
        )
    }

    /**
     * 從解碼後文件重播所有交易，與權威投影逐筆比對。
     *
     * @param document 待驗證的原型 Replay 文件。
     * @param expected 各局每筆交易後的權威投影。
     */
    private fun assertReplayedProjections(document: JsonObject, expected: List<List<JsonElement>>) {
        val header = document.getValue("header") as JsonObject
        val paths = (header.getValue("patchPaths") as JsonArray).map { path -> (path as JsonArray).toList() }
        val rounds = document.getValue("rounds") as JsonArray
        assertEquals(expected.size, rounds.size)
        for ((roundIndex, element) in rounds.withIndex()) {
            val round = element as JsonObject
            var projection = round.getValue("initial")
            val transactions = round.getValue("transactions") as JsonArray
            assertEquals(expected[roundIndex].size, transactions.size)
            for ((transactionIndex, transactionElement) in transactions.withIndex()) {
                val transaction = transactionElement as JsonObject
                val patch = transaction["p"]
                if (patch != null && patch != JsonNull) projection = FlatPatchCodec.apply(projection, patch as JsonArray, paths)
                assertEquals(expected[roundIndex][transactionIndex], projection)
            }
        }
    }

    /**
     * 為本局已存在的實體牌建立穩定索引，並登錄牌種。
     *
     * @param state 開局時的權威桌況。
     * @param typeDictionary 跨局共用的牌種字典。
     * @return 本局牌與玩家的索引。
     */
    private fun createRoundIndex(state: TableState, typeDictionary: MutableMap<String, JsonElement>): RoundIndex {
        val tiles = state.tileWall.getAllTiles() + state.reservedWallTiles + state.players.flatMap { player ->
            player.hand.allTiles + player.discardPile.entries.map { it.tile }
        }
        val ids = tiles.map { it.id.toString() }
        assertTrue(tiles.isNotEmpty(), "A round must contain tiles")
        assertEquals(ids.size, ids.distinct().size, "Opening tile must belong to exactly one zone")
        val tileTypes = tiles.map { identified ->
            val encoded = json.encodeToJsonElement(identified.tile.toPersistenceDto())
            typeDictionary.getOrPut(encoded.toString()) { encoded }
            typeDictionary.keys.indexOf(encoded.toString())
        }
        return RoundIndex(
            tileIds = ids.withIndex().associateTo(linkedMapOf()) { (index, id) -> id to index },
            tileTypes = tileTypes.toMutableList(),
            encodedTiles = tiles.associateTo(mutableMapOf()) { it.id.toString() to json.encodeToJsonElement(it.tile.toPersistenceDto()) },
            playerIds = state.players.associate { it.id.toString() to it.initialSeatIndex },
            typeDictionary = typeDictionary,
        )
    }

    /**
     * 將局中新增牌追加至局內索引，回傳牌種代碼作為交易宣告。
     *
     * @param state 交易後的權威桌況。
     * @param index 本局牌與玩家的索引。
     * @param typeDictionary 跨局共用的牌種字典。
     * @return 本次交易新宣告牌的牌種索引。
     */
    private fun declareNewTiles(
        state: TableState,
        index: RoundIndex,
        typeDictionary: MutableMap<String, JsonElement>,
    ): List<Int> {
        val visibleZones = state.tileWall.getAllTiles() + state.reservedWallTiles + state.players.flatMap { player ->
            player.hand.allTiles + player.discardPile.entries.map { it.tile }
        }
        val declarations = mutableListOf<Int>()
        for (tile in visibleZones) {
            val id = tile.id.toString()
            val encoded = json.encodeToJsonElement(tile.tile.toPersistenceDto())
            if (id in index.tileIds) {
                assertEquals(index.encodedTiles[id], encoded, "A tile instance cannot change type during the round")
                continue
            }
            val typeCode = typeDictionary.getOrPut(encoded.toString()) { encoded }
                .let { typeDictionary.keys.indexOf(encoded.toString()) }
            index.tileIds[id] = index.tileTypes.size
            index.tileTypes += typeCode
            index.encodedTiles[id] = encoded
            declarations += typeCode
        }
        return declarations
    }

    /**
     * 將權威桌況轉為歷史重播需要的遊戲內容投影。
     *
     * @param state 待投影的權威桌況。
     * @param index 本局牌與玩家的索引。
     * @return 使用局內索引的遊戲內容投影。
     */
    private fun project(state: TableState, index: RoundIndex): JsonElement {
        val dto = state.toPersistenceDto(
            registries.ruleConfigs,
            registries.discardPiles,
            registries.playerRuleStates,
            registries.dynamicRuleStates,
            registries.exhaustiveDrawReasons,
            registries.extensionGameActions,
            json,
        )
        val encoded = json.encodeToJsonElement(dto) as JsonObject
        val gameplay = encoded.filterKeys { it !in setOf("id", "config", "physicalWallLayout") }.toMutableMap()
        gameplay["players"] = JsonArray(
            (encoded.getValue("players") as JsonArray).map { playerElement ->
                val player = playerElement as JsonObject
                JsonObject(player.filterKeys { it !in setOf("id", "initialSeatIndex", "aiStrategyKey", "actionHistory") })
            },
        )
        return translate(JsonObject(gameplay), index)
    }

    /**
     * 將已知牌、玩家與牌種參照翻譯為索引；拒絕未宣告的 UUID。
     *
     * @param element 待轉換的 JSON 節點。
     * @param index 本局牌與玩家的索引。
     * @return 使用局內索引的 JSON 節點。
     */
    private fun translate(element: JsonElement, index: RoundIndex): JsonElement = when (element) {
        is JsonObject -> {
            val typeCode = index.typeDictionary.keys.indexOf(element.toString())
            val tileId = (element["id"] as? JsonPrimitive)?.content
            if (typeCode >= 0) {
                JsonPrimitive(typeCode)
            } else if (tileId != null && element.size == 2 && "tile" in element && tileId in index.tileIds) {
                assertEquals(index.encodedTiles[tileId], element.getValue("tile"))
                JsonPrimitive(index.tileIds.getValue(tileId))
            } else {
                JsonObject(element.map { (key, value) -> translateKey(key, index) to translate(value, index) }.toMap())
            }
        }
        is JsonArray -> JsonArray(element.map { translate(it, index) })
        is JsonPrimitive -> {
            val value = element.content
            when {
                !element.isString || !UUID_PATTERN.matches(value) -> element
                value in index.tileIds -> JsonPrimitive(index.tileIds.getValue(value))
                value in index.playerIds -> JsonPrimitive(index.playerIds.getValue(value))
                else -> error("Undeclared UUID in replay payload: $value")
            }
        }
    }

    /**
     * 將物件鍵中的牌或玩家 UUID 翻譯為索引字串。
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
     * 單局牌與玩家參照的編碼索引。
     *
     * @property tileIds 實體牌 UUID 到局內索引的對照。
     * @property tileTypes 依局內索引排列的牌種代碼。
     * @property encodedTiles 實體牌 UUID 對應的原始牌種 DTO，用於檢查種類不變。
     * @property playerIds 玩家 UUID 到座位索引的對照。
     * @property typeDictionary 跨局共用的牌種 DTO 字典。
     */
    private data class RoundIndex(
        val tileIds: MutableMap<String, Int>,
        val tileTypes: MutableList<Int>,
        val encodedTiles: MutableMap<String, JsonElement>,
        val playerIds: Map<String, Int>,
        val typeDictionary: MutableMap<String, JsonElement>,
    )

    /**
     * 編碼期間累積的單局資料與逐交易期望狀態。
     *
     * @property number 權威局數。
     * @property tileTypes 本局初始牌的牌種代碼。
     * @property initial 本局開始時的遊戲內容投影。
     * @property transactions 已編碼的有序交易。
     * @property replayed 目前已套用交易後的投影。
     * @property expected 每筆交易結束後的權威投影。
     */
    private data class MutableRound(
        val number: Int,
        val tileTypes: List<Int>,
        val initial: JsonElement,
        val transactions: MutableList<JsonElement> = mutableListOf(),
        var replayed: JsonElement = initial,
        val expected: MutableList<JsonElement> = mutableListOf(),
    )

    /**
     * 單場原型編碼結果與驗證統計。
     *
     * @property document 完整的歷史文件。
     * @property roundCount 局數。
     * @property transactionCount 權威交易數量。
     * @property reconstructedEventCount 已處理的來源事件數量。
     * @property tileTypeCount 文件中的牌種數量。
     * @property factCounts 各語意事實種類的出現次數。
     * @property expectedProjections 依局與交易排列的權威投影。
     */
    private data class Result(
        val document: JsonObject,
        val roundCount: Int,
        val transactionCount: Int,
        val reconstructedEventCount: Int,
        val tileTypeCount: Int,
        val factCounts: Map<String, Int>,
        val expectedProjections: List<List<JsonElement>>,
    )

    /** 原型辨識 UUID 文字值時使用的共用格式。 */
    private companion object {
        /** 驗證字串是否符合 UUID 文字格式。 */
        val UUID_PATTERN = Regex("[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}")
    }
}
