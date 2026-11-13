package com.doublemoon1119.mahjongcraft.flow.server.game.orchestration

import com.doublemoon1119.mahjongcraft.flow.common.game.history.HistoryFact
import com.doublemoon1119.mahjongcraft.flow.common.game.history.HistoryOutboxEvent
import com.doublemoon1119.mahjongcraft.flow.common.game.history.HistoryTableResult
import com.doublemoon1119.mahjongcraft.flow.persistence.format.game.toPersistenceDto
import com.doublemoon1119.mahjongcraft.flow.persistence.format.history.HistoryRecordingPersistenceMapper
import com.doublemoon1119.mahjongcraft.flow.persistence.format.history.replay.CompactReplayCodec
import com.doublemoon1119.mahjongcraft.logic.rules.riichi.RiichiGameLength
import com.doublemoon1119.mahjongcraft.logic.table.TableState
import com.doublemoon1119.mahjongcraft.testing.flow.bundled.bundledPersistenceRegistries
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.encodeToJsonElement
import org.junit.Assume.assumeTrue
import java.io.ByteArrayOutputStream
import java.util.logging.Logger
import java.util.zip.GZIPOutputStream
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * 驗證正式精簡牌譜的容量上限、編碼穩定性與逐交易還原。
 *
 * 期望的逐交易內容由權威桌況直接投影而得，不經正式編碼器，因此能獨立檢查正式解碼結果。
 */
class CompactReplayCapacityTest {
    /** 投影使用的 JSON 編解碼設定。 */
    private val json = Json

    /** 用於轉換內建規則資料的 persistence registries。 */
    private val registries = bundledPersistenceRegistries()

    /** 將權威歷史事實轉為 persistence DTO 的 mapper。 */
    private val mapper = HistoryRecordingPersistenceMapper(registries)

    /** 以固定權威事件驗證正式牌譜的容量上限與逐交易還原。 */
    @Test
    fun `fixed matches stay within replay capacity and decode every transaction`() {
        for (fixture in ReplayCapacityFixtures.all) {
            val measurement = verifyMatch(fixture.events)
            assertTrue(measurement.jsonBytes <= MAX_JSON_BYTES, "Compact Replay JSON exceeds 230 KB: ${measurement.jsonBytes} bytes")
            assertTrue(measurement.gzipBytes <= MAX_GZIP_BYTES, "Compact Replay gzip exceeds 35 KB: ${measurement.gzipBytes} bytes")
        }
    }

    /** 同一場對局重複編碼得到完全相同的正式牌譜。 */
    @Test
    fun `repeated encoding of the same match is identical`() {
        val events = ReplayCapacityFixtures.east1.events
        val first = CompactReplayCodec.encodeCompact(events, mapper, registries).toString()
        val second = CompactReplayCodec.encodeCompact(events, mapper, registries).toString()
        assertEquals(first, second)
    }

    /**
     * 以即時完整對局統計容量分布；只在設定 `MAHJONGCRAFT_REPLAY_MEASUREMENT_BATCHES` 時執行，平常略過。
     *
     * 每批打完一組東風戰與半莊戰，並同樣驗證逐交易還原。
     */
    @Test
    fun `random full matches report replay capacity distribution`() = runBlocking {
        val configuredBatches = System.getenv(MEASUREMENT_BATCHES_ENV)
        assumeTrue("Set $MEASUREMENT_BATCHES_ENV to measure replay capacity", configuredBatches != null)
        val batches = checkNotNull(configuredBatches).toIntOrNull() ?: error("$MEASUREMENT_BATCHES_ENV must be an integer")
        require(batches in 1..MAX_MEASUREMENT_BATCHES) { "$MEASUREMENT_BATCHES_ENV must be between 1 and $MAX_MEASUREMENT_BATCHES" }
        val grouped = linkedMapOf<RiichiGameLength, MutableList<Measurement>>()
        repeat(batches) {
            for (length in listOf(RiichiGameLength.East, RiichiGameLength.TwoWinds)) {
                val measurements = grouped.getOrPut(length) { mutableListOf() }
                RoomToRoomFullLifecycleIntegrationTest().measureFullLifecycle(length).forEach { events ->
                    measurements += verifyMatch(events)
                }
            }
        }
        grouped.forEach { (length, measurements) -> logMetrics(length, measurements) }
    }

    /**
     * 以正式編碼器編碼一場對局，確認解碼後每筆交易都與權威投影一致。
     *
     * @param events 依事件序號排序的權威歷史事件。
     * @return 此場的容量與數量量測結果。
     */
    private fun verifyMatch(events: List<HistoryOutboxEvent>): Measurement {
        val expected = expectedProjections(events)
        val production = CompactReplayCodec.encodeCompact(events, mapper, registries)
        val productionText = production.toString()
        val decoded = CompactReplayCodec.decodeCompact(production)
        assertEquals(expected, decoded.map { round -> round.map { it.projection } })
        return Measurement(
            jsonBytes = productionText.toByteArray().size,
            gzipBytes = gzipSize(productionText),
            rounds = decoded.size,
            events = events.size,
            transactions = decoded.sumOf { it.size },
        )
    }

    /**
     * 依交易分組重播權威事件，取得每筆交易結束後的遊戲內容投影。
     *
     * 開局事件之前若同一筆交易已有其他事實，那些事實屬於前一局的最後一筆交易。
     *
     * @param events 依事件序號排序的權威歷史事件。
     * @return 依局與交易排列的期望投影。
     */
    private fun expectedProjections(events: List<HistoryOutboxEvent>): List<List<JsonElement>> {
        val typeDictionary = linkedMapOf<String, JsonElement>()
        val rounds = mutableListOf<MutableList<JsonElement>>()
        var current: TableState? = null
        var roundIndex: RoundIndex? = null
        for (transaction in events.groupBy { it.transactionFirstSequence }.values) {
            var hasFacts = false
            for (event in transaction) {
                when (val fact = event.fact) {
                    is HistoryFact.MatchStarted, is HistoryFact.RoundStarted -> {
                        if (hasFacts) {
                            rounds.last() += project(checkNotNull(current), checkNotNull(roundIndex))
                            hasFacts = false
                        }
                        val state = when (fact) {
                            is HistoryFact.MatchStarted -> fact.tableState
                            is HistoryFact.RoundStarted -> fact.tableState
                        }
                        current = state
                        roundIndex = createRoundIndex(state, typeDictionary)
                        rounds += mutableListOf<JsonElement>()
                    }
                    is HistoryFact.TableChanged -> current = when (val result = fact.result) {
                        is HistoryTableResult.Change -> result.change.applyTo(checkNotNull(current))
                        is HistoryTableResult.Checkpoint -> result.tableState
                    }
                    else -> hasFacts = true
                }
            }
            val state = checkNotNull(current)
            val index = checkNotNull(roundIndex)
            declareNewTiles(state, index, typeDictionary)
            rounds.last() += project(state, index)
        }
        return rounds
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
     * 將局中新增的牌追加到局內索引。
     *
     * @param state 交易後的權威桌況。
     * @param index 本局牌與玩家的索引。
     * @param typeDictionary 跨局共用的牌種字典。
     */
    private fun declareNewTiles(
        state: TableState,
        index: RoundIndex,
        typeDictionary: MutableMap<String, JsonElement>,
    ) {
        val visibleZones = state.tileWall.getAllTiles() + state.reservedWallTiles + state.players.flatMap { player ->
            player.hand.allTiles + player.discardPile.entries.map { it.tile }
        }
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
        }
    }

    /**
     * 將權威桌況轉為歷史重播需要的遊戲內容投影。
     *
     * 牌河使用不分規則的清單，與正式編碼器保存的內容一致。
     *
     * @param state 待投影的權威桌況。
     * @param index 本局牌與玩家的索引。
     * @return 使用局內索引的遊戲內容投影。
     */
    private fun project(
        state: TableState,
        index: RoundIndex,
    ): JsonElement {
        val dto = state.toPersistenceDto(
            registries.ruleConfigs,
            registries.discardPiles,
            registries.exhaustiveDrawReasons,
            registries.extensionGameActions,
            registries.components,
        )
        val encoded = json.encodeToJsonElement(dto) as JsonObject
        val gameplay = encoded.filterKeys { it !in setOf("id", "config", "physicalWallLayout") }.toMutableMap()
        // 即時提交的視窗身分不參與歷史內容投影。
        (gameplay["turnPhase"] as? JsonObject)?.let { phase ->
            (phase["reaction"] as? JsonObject)?.let { reaction ->
                gameplay["turnPhase"] = JsonObject(phase + ("reaction" to JsonObject(reaction.filterKeys { it != "id" })))
            }
        }
        gameplay["players"] = JsonArray(
            (encoded.getValue("players") as JsonArray).zip(state.players) { playerElement, player ->
                val projected = (playerElement as JsonObject).filterKeys { it !in setOf("id", "actionHistory", "discardPile") }.toMutableMap()
                projected["discards"] = JsonArray(
                    player.discardPile.entries.map { entry ->
                        JsonObject(
                            buildMap {
                                put("tile", json.encodeToJsonElement(entry.tile.toPersistenceDto(registries.components)))
                                if (entry.isTaken) put("taken", JsonPrimitive(true))
                                if (entry.publicMarkerIds.isNotEmpty()) put("markers", JsonArray(entry.publicMarkerIds.sorted().map(::JsonPrimitive)))
                            },
                        )
                    },
                )
                JsonObject(projected)
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
            val typeCode = index.typeCodeOf(element)
            val tileId = (element["id"] as? JsonPrimitive)?.content
            if (typeCode >= 0) {
                JsonPrimitive(typeCode)
            } else if (tileId != null && element.keys == setOf("id", "tile", "components") && tileId in index.tileIds) {
                assertEquals(index.encodedTiles[tileId], element.getValue("tile"))
                val reference = JsonPrimitive(index.tileIds.getValue(tileId))
                val components = element.getValue("components") as JsonArray
                if (components.isEmpty()) {
                    reference
                } else {
                    // 帶特性的牌與正式精簡牌譜相同：保留局內索引，另附當時的特性版本。
                    JsonObject(mapOf("r" to reference, "c" to components))
                }
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
     * 計算文字以 GZIP 壓縮後的位元組數。
     *
     * @param text 待壓縮的文字。
     * @return 壓縮後的位元組數。
     */
    private fun gzipSize(text: String): Int {
        val output = ByteArrayOutputStream()
        GZIPOutputStream(output).use { it.write(text.toByteArray()) }
        return output.size()
    }

    /**
     * 將同一對局長度的容量與數量量測寫入測試記錄。
     *
     * @param length 對局使用的日麻長度。
     * @param measurements 同一對局長度的量測結果。
     */
    private fun logMetrics(length: RiichiGameLength, measurements: List<Measurement>) {
        /**
         * 格式化量測的最小值、中位數、P95 與最大值；百分位數採最近秩次。
         *
         * @param selector 從量測結果取得數值的選擇器。
         * @return 包含四個摘要統計值的文字。
         */
        fun summary(selector: (Measurement) -> Double): String {
            val sorted = measurements.map(selector).sorted()

            /**
             * 依最近秩次取得百分位數。
             *
             * @param percentile 要查詢的百分位數，範圍為 1 至 100。
             * @return 百分位數值。
             */
            fun at(percentile: Int): Double = sorted[(((sorted.size * percentile) + 99) / 100).coerceAtLeast(1) - 1]
            return "min=%.1f median=%.1f p95=%.1f max=%.1f".format(sorted.first(), at(50), at(95), sorted.last())
        }

        logger.info(
            "Replay measurements length=$length count=${measurements.size} " +
                "jsonBytes[${summary { it.jsonBytes.toDouble() }}] gzipBytes[${summary { it.gzipBytes.toDouble() }}] " +
                "rounds[${summary { it.rounds.toDouble() }}] events[${summary { it.events.toDouble() }}] " +
                "transactions[${summary { it.transactions.toDouble() }}] " +
                "jsonBytesPerRound[${summary { it.jsonBytes.toDouble() / it.rounds }}] " +
                "gzipBytesPerRound[${summary { it.gzipBytes.toDouble() / it.rounds }}] " +
                "jsonBytesPerTransaction[${summary { it.jsonBytes.toDouble() / it.transactions }}] " +
                "gzipBytesPerTransaction[${summary { it.gzipBytes.toDouble() / it.transactions }}]",
        )
    }

    /**
     * 單局牌與玩家參照的索引。
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
    ) {
        /** 建立查詢快取時的牌種數量；牌種字典只會增加，數量不同就重建快取。 */
        private var cachedTypeCount = -1

        /** 牌種 DTO 會出現的欄位組合。 */
        private var typeKeySets: Set<Set<String>> = emptySet()

        /** 牌種 DTO 文字到牌種代碼的對照。 */
        private var typeCodes: Map<String, Int> = emptyMap()

        /**
         * 取得與 [element] 內容相同的牌種代碼；只有欄位組合與某個牌種相同的物件才轉成文字比對。
         *
         * @param element 待比對的 JSON 物件。
         * @return 牌種代碼；不是牌種時為 -1。
         */
        fun typeCodeOf(element: JsonObject): Int {
            if (cachedTypeCount != typeDictionary.size) {
                typeKeySets = typeDictionary.values.mapTo(mutableSetOf()) { (it as JsonObject).keys }
                typeCodes = typeDictionary.keys.withIndex().associate { it.value to it.index }
                cachedTypeCount = typeDictionary.size
            }
            if (element.keys !in typeKeySets) return -1
            return typeCodes[element.toString()] ?: -1
        }
    }

    /**
     * 單場正式牌譜的容量與數量量測結果。
     *
     * @property jsonBytes 正式牌譜 JSON 的 UTF-8 位元組數。
     * @property gzipBytes 正式牌譜 JSON 經 GZIP 壓縮後的位元組數。
     * @property rounds 牌譜中的局數。
     * @property events 輸入權威事件數量。
     * @property transactions 牌譜中的交易數量。
     */
    private data class Measurement(
        val jsonBytes: Int,
        val gzipBytes: Int,
        val rounds: Int,
        val events: Int,
        val transactions: Int,
    )

    /** 容量上限與量測設定。 */
    private companion object {
        /** 將容量量測寫入 JVM 測試記錄。 */
        private val logger: Logger = Logger.getLogger(CompactReplayCapacityTest::class.java.name)

        /** 啟用即時對局量測的環境變數，值為批次數。 */
        const val MEASUREMENT_BATCHES_ENV = "MAHJONGCRAFT_REPLAY_MEASUREMENT_BATCHES"

        /** 即時對局量測的批次上限。 */
        const val MAX_MEASUREMENT_BATCHES = 25

        /** 正式牌譜 JSON 的位元組上限。 */
        const val MAX_JSON_BYTES = 230_000

        /** 正式牌譜 GZIP 後的位元組上限。 */
        const val MAX_GZIP_BYTES = 35_000

        /** 辨識 UUID 文字值的格式。 */
        val UUID_PATTERN = Regex("[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}")
    }
}
