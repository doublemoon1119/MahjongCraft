package com.doublemoon1119.mahjongcraft.flow.server.game.orchestration

import com.doublemoon1119.mahjongcraft.flow.common.game.history.HistoryFact
import com.doublemoon1119.mahjongcraft.flow.common.game.history.HistoryOutboxEvent
import com.doublemoon1119.mahjongcraft.flow.common.game.history.HistoryRecordingState
import com.doublemoon1119.mahjongcraft.flow.common.game.history.HistoryTableResult
import com.doublemoon1119.mahjongcraft.flow.persistence.dto.history.HistoryRecordingPersistenceMapper
import com.doublemoon1119.mahjongcraft.flow.persistence.dto.registry.buildBuiltInPersistenceRegistries
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
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonPrimitive
import java.io.ByteArrayOutputStream
import java.util.zip.GZIPOutputStream
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** 暫時量測原始歷史格式中的重複欄位與識別碼成本；不作為正式 replay 編碼器。 */
class CompactHistoryPrototypeTest {
    /** 量測移除動作結果重複欄位前後的完整對局大小。 */
    @Test
    fun measureSlimActions() = runBlocking {
        val mapper = HistoryRecordingPersistenceMapper(buildBuiltInPersistenceRegistries())
        for (length in listOf(RiichiGameLength.East, RiichiGameLength.TwoWinds)) {
            val matches = RoomToRoomFullLifecycleIntegrationTest().measureFullLifecycle(length)
            for ((index, events) in matches.withIndex()) {
                val validatedActions = verifyActionResultRedundancy(events)
                val dto = mapper.encode(HistoryRecordingState(pendingEvents = events))
                assertEquals(events.size, dto.pendingEvents.size)
                val raw = Json.encodeToString(dto.pendingEvents)
                val withPlayerDeltas = replacePlayersWithDeltas(Json.parseToJsonElement(raw) as JsonArray)
                val withSlimActions = slimActionResults(withPlayerDeltas)
                val slimText = withSlimActions.toString()
                val keys = linkedMapOf<String, Int>()
                val uuids = linkedMapOf<String, Int>()
                val compactEvents = compact(withSlimActions, keys, uuids)
                val compactDocument = JsonObject(
                    mapOf(
                        "k" to JsonArray(keys.keys.map(::JsonPrimitive)),
                        "u" to JsonArray(uuids.keys.map(::JsonPrimitive)),
                        "e" to compactEvents,
                    ),
                )
                val compactText = compactDocument.toString()
                val bytesByFact = withSlimActions.groupBy { event ->
                    ((event as JsonObject).getValue("fact") as JsonObject).getValue("type").jsonPrimitive.content
                }.mapValues { (_, values) -> values.sumOf { it.toString().toByteArray().size } }
                assertTrue(validatedActions > 0, "No actions were validated for match ${index + 1}")
                assertTrue(bytesByFact.isNotEmpty(), "No facts were encoded for match ${index + 1}")
                assertTrue(compactText.isNotEmpty() && slimText.isNotEmpty() && raw.isNotEmpty())
            }
        }
    }

    /** 驗證每次權威桌況變更中的玩家差異皆可獨立還原。 */
    @Test
    fun playerDeltasReconstructEveryChangedPlayer() = runBlocking {
        var changedPlayers = 0
        var changedTables = 0
        for (length in listOf(RiichiGameLength.East, RiichiGameLength.TwoWinds)) {
            val matches = RoomToRoomFullLifecycleIntegrationTest().measureFullLifecycle(length)
            for (events in matches) {
                var current: TableState? = null
                for (event in events) {
                    when (val fact = event.fact) {
                        is HistoryFact.MatchStarted -> current = fact.tableState
                        is HistoryFact.RoundStarted -> current = fact.tableState
                        is HistoryFact.TableChanged -> {
                            val before = checkNotNull(current)
                            val after = when (val result = fact.result) {
                                is HistoryTableResult.Change -> result.change.applyTo(before)
                                is HistoryTableResult.Checkpoint -> result.tableState
                            }
                            val restoredPlayers = before.players.zip(after.players).map { (old, new) ->
                                val delta = TestPlayerDelta.between(old, new)
                                if (delta != null) changedPlayers++
                                delta?.applyTo(old) ?: old
                            }
                            assertEquals(after.players, restoredPlayers, "Player delta mismatch at sequence ${event.sequence}")
                            changedTables++
                            current = after
                        }
                        else -> Unit
                    }
                }
            }
        }
        assertTrue(changedPlayers > 0)
        assertTrue(changedTables > 0)
    }

    /** 量測原始歷史、玩家差異及字典化格式的容量。 */
    @Test
    fun measure() = runBlocking {
        val mapper = HistoryRecordingPersistenceMapper(buildBuiltInPersistenceRegistries())
        for (length in listOf(RiichiGameLength.East, RiichiGameLength.TwoWinds)) {
            val matches = RoomToRoomFullLifecycleIntegrationTest().measureFullLifecycle(length)
            for ((index, events) in matches.withIndex()) {
                val dto = mapper.encode(HistoryRecordingState(pendingEvents = events))
                assertEquals(events.size, dto.pendingEvents.size, "History encoding omitted events")
                val raw = Json.encodeToString(dto.pendingEvents)
                val rawElement = Json.parseToJsonElement(raw)
                val playerDeltaEvents = replacePlayersWithDeltas(rawElement as JsonArray)
                val playerDeltaText = playerDeltaEvents.toString()
                val playerDeltaPerEventGzip = playerDeltaEvents.sumOf { gzipSize(it.toString()) }
                val playerDeltaMaxEvent = playerDeltaEvents.maxOf { it.toString().toByteArray().size }
                val factCounts = events.groupingBy { it.fact::class.simpleName.orEmpty() }.eachCount()
                val roundTileCounts = events.mapNotNull { event ->
                    val state = when (val fact = event.fact) {
                        is HistoryFact.MatchStarted -> fact.tableState
                        is HistoryFact.RoundStarted -> fact.tableState
                        else -> null
                    } ?: return@mapNotNull null
                    initialTileInventory(state).size
                }
                assertTrue(roundTileCounts.all { it == roundTileCounts.first() })
                val deltaKeys = linkedMapOf<String, Int>()
                val deltaUuids = linkedMapOf<String, Int>()
                val deltaCompact = compact(playerDeltaEvents, deltaKeys, deltaUuids)
                val deltaDocument = JsonObject(
                    mapOf(
                        "k" to JsonArray(deltaKeys.keys.map(::JsonPrimitive)),
                        "u" to JsonArray(deltaUuids.keys.map(::JsonPrimitive)),
                        "e" to deltaCompact,
                    ),
                )
                val deltaDictionaryText = deltaDocument.toString()
                val deltaWithoutUuidDictionary = JsonObject(deltaDocument.filterKeys { it != "u" }).toString()
                val bytesByFact = dto.pendingEvents.groupBy { it.fact::class.simpleName.orEmpty() }
                    .mapValues { (_, values) -> values.sumOf { Json.encodeToString(it).toByteArray().size } }
                val deltaBytesByFact = playerDeltaEvents.groupBy { event ->
                    ((event as JsonObject).getValue("fact") as JsonObject).getValue("type").jsonPrimitive.content
                }.mapValues { (_, values) -> values.sumOf { it.toString().toByteArray().size } }
                assertTrue(raw.isNotEmpty() && playerDeltaText.isNotEmpty() && deltaDictionaryText.isNotEmpty())
                assertTrue(playerDeltaPerEventGzip > 0 && playerDeltaMaxEvent > 0)
                assertTrue(deltaWithoutUuidDictionary.isNotEmpty() && factCounts.isNotEmpty())
                assertTrue(bytesByFact.isNotEmpty() && deltaBytesByFact.isNotEmpty())
            }
        }
    }

    /**
     * 收集開局牌牆、保留牌及玩家區域中的實體牌身分。
     *
     * @param state 開局時的權威桌況。
     * @return 開局所有實體牌的識別碼。
     */
    private fun initialTileInventory(state: TableState): List<String> {
        val tiles = state.tileWall.getAllTiles() + state.reservedWallTiles +
            state.players.flatMap { player ->
                player.hand.allTiles + player.discardPile.entries.map { it.tile }
            }
        val ids = tiles.map { it.id.toString() }
        assertEquals(ids.size, ids.distinct().size, "Opening state contains the same tile in multiple zones")
        return ids
    }

    /**
     * 比對動作回傳的部分結果與交易後桌況，以確認哪些欄位重複。
     *
     * @param events 同一場對局的有序權威事件。
     * @return 已比對的動作數量。
     */
    private fun verifyActionResultRedundancy(events: List<HistoryOutboxEvent>): Int {
        var current: TableState? = null
        var actions = 0
        for (transaction in events.groupBy { it.transactionFirstSequence }.values) {
            for (event in transaction) {
                current = when (val fact = event.fact) {
                    is HistoryFact.MatchStarted -> fact.tableState
                    is HistoryFact.RoundStarted -> fact.tableState
                    is HistoryFact.TableChanged -> when (val result = fact.result) {
                        is HistoryTableResult.Change -> result.change.applyTo(checkNotNull(current))
                        is HistoryTableResult.Checkpoint -> result.tableState
                    }
                    else -> current
                }
            }
            for (event in transaction) {
                val fact = event.fact as? HistoryFact.ActionAccepted ?: continue
                val after = checkNotNull(current)
                assertEquals(after.tileWall.remainingCount, fact.result.remainingWallTileCount)
                assertEquals(after.reservedWallTiles.map { it.id }, fact.result.reservedWallTileIds)
                assertEquals(after.players.associate { it.id to it.score }, fact.result.scoresByPlayerId)
                assertEquals(after.currentPlayer.id, fact.result.nextPlayerId)
                actions++
            }
        }
        assertTrue(actions > 0)
        return actions
    }

    /**
     * 保留動作語意並移除已可由交易後桌況讀取的結果欄位。
     *
     * @param events 待精簡的序列化事件。
     * @return 精簡動作結果後的事件。
     */
    internal fun slimActionResults(events: JsonArray): JsonArray = JsonArray(
        events.map { eventElement ->
            val event = eventElement as JsonObject
            val fact = event.getValue("fact") as JsonObject
            if (fact.getValue("type").jsonPrimitive.content != "action_accepted") return@map eventElement
            val result = fact.getValue("result") as JsonObject
            val directTiles = result.getValue("affectedTileIds") as JsonArray
            val revealedTiles = result.getValue("newlyRevealedTileIds") as JsonArray
            val reduced = fact.filterKeys { it != "result" }.toMutableMap()
            if (directTiles.isNotEmpty()) reduced["directTiles"] = directTiles
            if (revealedTiles.isNotEmpty()) reduced["revealedTiles"] = revealedTiles
            assertEquals(directTiles, reduced["directTiles"] ?: JsonArray(emptyList()))
            assertEquals(revealedTiles, reduced["revealedTiles"] ?: JsonArray(emptyList()))
            JsonObject(event + ("fact" to JsonObject(reduced)))
        },
    )

    /**
     * 將事件中的完整玩家狀態改為相對於前次狀態的差異。
     *
     * @param events 待轉換的序列化事件。
     * @return 玩家狀態使用相對差異的事件。
     */
    private fun replacePlayersWithDeltas(events: JsonArray): JsonArray {
        val previousPlayers = mutableMapOf<String, JsonObject>()
        return JsonArray(
            events.map { eventElement ->
                val event = eventElement as JsonObject
                val fact = event.getValue("fact") as JsonObject
                when (fact.getValue("type").jsonPrimitive.content) {
                    "match_started", "round_started" -> {
                        previousPlayers.clear()
                        val state = fact.getValue("state") as JsonObject
                        (state.getValue("players") as JsonArray).forEach { playerElement ->
                            val player = playerElement as JsonObject
                            previousPlayers[player.getValue("id").jsonPrimitive.content] = player
                        }
                        eventElement
                    }
                    "table_changed" -> {
                        val result = fact.getValue("result") as JsonObject
                        if (result.getValue("type").jsonPrimitive.content != "change") {
                            val state = result.getValue("state") as JsonObject
                            previousPlayers.clear()
                            (state.getValue("players") as JsonArray).forEach { playerElement ->
                                val player = playerElement as JsonObject
                                previousPlayers[player.getValue("id").jsonPrimitive.content] = player
                            }
                            eventElement
                        } else {
                            val change = result.getValue("change") as JsonObject
                            val changedPlayers = change["changedPlayers"] as? JsonArray ?: JsonArray(emptyList())
                            val transformed = JsonArray(
                                changedPlayers.map { changeElement ->
                                    val playerChange = changeElement as JsonObject
                                    val after = playerChange.getValue("playerWithoutHistory") as JsonObject
                                    val id = after.getValue("id").jsonPrimitive.content
                                    val before = checkNotNull(previousPlayers[id]) { "Missing previous player $id" }
                                    val delta = checkNotNull(jsonDelta(before, after))
                                    assertEquals(after, applyJsonDelta(before, delta), "Player DTO delta mismatch for $id")
                                    previousPlayers[id] = after
                                    JsonObject(playerChange.filterKeys { it != "playerWithoutHistory" } + ("playerDelta" to delta))
                                },
                            )
                            val nextChange = JsonObject(change + ("changedPlayers" to transformed))
                            val nextResult = JsonObject(result + ("change" to nextChange))
                            val nextFact = JsonObject(fact + ("result" to nextResult))
                            JsonObject(event + ("fact" to nextFact))
                        }
                    }
                    else -> eventElement
                }
            },
        )
    }

    /**
     * 遞迴計算兩份 JSON 值之間可套用的物件及列表差異。
     *
     * @param before 變更前的 JSON 值。
     * @param after 變更後的 JSON 值。
     * @return 可套用的差異；內容相同時為 null。
     */
    internal fun jsonDelta(before: JsonElement, after: JsonElement): JsonElement? {
        if (before == after) return null
        if (before is JsonObject && after is JsonObject) {
            val changes = linkedMapOf<String, JsonElement>()
            for (key in before.keys + after.keys) {
                when {
                    key !in after -> changes[key] = JsonObject(mapOf("_x" to JsonNull))
                    key !in before -> changes[key] = JsonObject(mapOf("_v" to after.getValue(key)))
                    else -> jsonDelta(before.getValue(key), after.getValue(key))?.let { changes[key] = it }
                }
            }
            return JsonObject(mapOf("_o" to JsonObject(changes)))
        }
        if (before is JsonArray && after is JsonArray) {
            val prefix = before.zip(after).takeWhile { (old, new) -> old == new }.size
            val commonLimit = minOf(before.size, after.size) - prefix
            val suffix = (0 until commonLimit).takeWhile { offset ->
                before[before.lastIndex - offset] == after[after.lastIndex - offset]
            }.count()
            val splice = JsonObject(
                mapOf(
                    "_a" to JsonArray(
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
                val indexed = JsonObject(
                    before.indices.mapNotNull { index ->
                        jsonDelta(before[index], after[index])?.let { index.toString() to it }
                    }.toMap(),
                )
                val indexedPatch = JsonObject(mapOf("_l" to indexed))
                if (indexedPatch.toString().length < splice.toString().length) return indexedPatch
            }
            return splice
        }
        return JsonObject(mapOf("_v" to after))
    }

    /**
     * 套用 [jsonDelta] 產生的差異，還原交易後的 JSON 值。
     *
     * @param before 變更前的 JSON 值。
     * @param delta [jsonDelta] 產生的結構差異。
     * @return 還原後的 JSON 值。
     */
    internal fun applyJsonDelta(before: JsonElement, delta: JsonElement): JsonElement {
        val operation = delta as JsonObject
        operation["_v"]?.let { return it }
        operation["_a"]?.let { arrayElement ->
            val parts = arrayElement as JsonArray
            val source = before as JsonArray
            val prefix = parts[0].jsonPrimitive.content.toInt()
            val removed = parts[1].jsonPrimitive.content.toInt()
            val suffix = parts[3].jsonPrimitive.content.toInt()
            require(prefix + removed + suffix == source.size)
            return JsonArray(source.take(prefix) + (parts[2] as JsonArray) + source.takeLast(suffix))
        }
        operation["_l"]?.let { indexedElement ->
            val source = before as JsonArray
            val indexed = indexedElement as JsonObject
            require(indexed.keys.all { it.toIntOrNull() in source.indices })
            return JsonArray(
                source.mapIndexed { index, value ->
                    indexed[index.toString()]?.let { applyJsonDelta(value, it) } ?: value
                },
            )
        }
        val changes = operation.getValue("_o") as JsonObject
        val result = (before as JsonObject).toMutableMap()
        for ((key, patch) in changes) {
            if (patch is JsonObject && "_x" in patch) {
                result.remove(key)
            } else {
                result[key] = applyJsonDelta(result[key] ?: JsonNull, patch)
            }
        }
        return JsonObject(result)
    }

    /**
     * 以共用欄位與 UUID 字典編碼 JSON 樹狀資料。
     *
     * @param element 待編碼的 JSON 節點。
     * @param keys 欄位名稱到字典索引的可變對照。
     * @param uuids UUID 字串到字典索引的可變對照。
     * @return 使用字典索引的 JSON 節點。
     */
    internal fun compact(element: JsonElement, keys: MutableMap<String, Int>, uuids: MutableMap<String, Int>): JsonElement = when (element) {
        is JsonObject -> JsonObject(
            element.mapKeys { (key, _) -> (keys.getOrPut(key) { keys.size }).toString() }
                .mapValues { (_, value) -> compact(value, keys, uuids) },
        )
        is JsonArray -> JsonArray(element.map { compact(it, keys, uuids) })
        is JsonPrimitive -> {
            val value = element.contentOrNull
            if (element.isString && value != null && UUID_PATTERN.matches(value)) {
                JsonObject(mapOf("@" to JsonPrimitive(uuids.getOrPut(value) { uuids.size })))
            } else {
                element
            }
        }
    }

    /**
     * 計算一份 JSON 文字作為獨立 gzip 串流時的位元組數。
     *
     * @param value 待壓縮的 JSON 文字。
     * @return 壓縮後的位元組數。
     */
    internal fun gzipSize(value: String): Int {
        val output = ByteArrayOutputStream()
        GZIPOutputStream(output).use { it.write(value.toByteArray()) }
        return output.size()
    }

    /** 收納原型共用的 UUID 文字格式。 */
    private companion object {
        /** 用於辨識可字典化 UUID 字串的正規表示式。 */
        val UUID_PATTERN = Regex("[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}")
    }
}
