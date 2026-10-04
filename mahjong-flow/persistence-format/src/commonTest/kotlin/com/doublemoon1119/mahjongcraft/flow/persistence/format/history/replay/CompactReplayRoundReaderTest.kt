package com.doublemoon1119.mahjongcraft.flow.persistence.format.history.replay

import com.doublemoon1119.mahjongcraft.flow.common.game.history.replay.HistoryReplayDiscard
import com.doublemoon1119.mahjongcraft.flow.common.game.history.replay.HistoryReplayFact
import com.doublemoon1119.mahjongcraft.flow.common.game.history.replay.HistoryReplayIdentity
import com.doublemoon1119.mahjongcraft.flow.common.game.history.replay.HistoryReplayPlayerIdentity
import com.doublemoon1119.mahjongcraft.flow.common.game.history.replay.HistoryReplayRuleInformation
import com.doublemoon1119.mahjongcraft.flow.common.game.history.replay.HistoryRoundPosition
import com.doublemoon1119.mahjongcraft.flow.common.game.history.replay.HistoryRoundTileCatalog
import com.doublemoon1119.mahjongcraft.flow.common.game.history.replay.HistoryTileReference
import com.doublemoon1119.mahjongcraft.logic.base.Tile
import com.doublemoon1119.mahjongcraft.logic.table.RoundCompletionClassification
import com.doublemoon1119.mahjongcraft.logic.table.RoundTransitionDirective
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
import kotlin.uuid.Uuid

/** 驗證局級讀取使用的強型別擴充 registry 與受限索引上下文。 */
class CompactReplayRoundReaderTest {
    /** 讀取摸牌後的投影時，最後摸入牌與立牌分開保留。 */
    @Test
    fun `state mapping preserves separately stored last drawn tile`() {
        val mapper = HistoryReplayProjectionMapper(HistoryReplayProjectionRegistry().also(::registerBuiltInHistoryReplayProjections))
        val state = mapper.mapState(
            projection = playerProjection(standingTiles = listOf(0), lastDrawn = 1, wallTiles = listOf(2)),
            identity = identity(),
            roundNumber = 1,
            position = HistoryRoundPosition.Initial,
            tileCatalog = HistoryRoundTileCatalog(listOf(Tile.Honor.East, Tile.Honor.South, Tile.Honor.West)),
            budget = ReplayReadBudget(ReplayReadLimits()) {},
        )

        assertEquals(listOf(HistoryTileReference(0)), state.players.single().handTiles)
        assertEquals(HistoryTileReference(1), state.players.single().lastDrawn)
    }

    /** 不允許最後摸入牌同時出現在立牌中，避免同一實體牌被重複持有。 */
    @Test
    fun `state mapping rejects last drawn tile duplicated in standing hand`() {
        val mapper = HistoryReplayProjectionMapper(HistoryReplayProjectionRegistry().also(::registerBuiltInHistoryReplayProjections))

        assertFailsWith<IllegalArgumentException> {
            mapper.mapState(
                projection = playerProjection(standingTiles = listOf(0), lastDrawn = 0, wallTiles = listOf(1)),
                identity = identity(),
                roundNumber = 1,
                position = HistoryRoundPosition.Initial,
                tileCatalog = HistoryRoundTileCatalog(listOf(Tile.Honor.East, Tile.Honor.South)),
                budget = ReplayReadBudget(ReplayReadLimits()) {},
            )
        }
    }

    /** 不允許最後摸入牌同時出現在活牌牆，避免牌張從牌牆與手牌重複持有。 */
    @Test
    fun `state mapping rejects last drawn tile duplicated in wall`() {
        val mapper = HistoryReplayProjectionMapper(HistoryReplayProjectionRegistry().also(::registerBuiltInHistoryReplayProjections))

        assertFailsWith<IllegalArgumentException> {
            mapper.mapState(
                projection = playerProjection(standingTiles = listOf(0), lastDrawn = 1, wallTiles = listOf(1)),
                identity = identity(),
                roundNumber = 1,
                position = HistoryRoundPosition.Initial,
                tileCatalog = HistoryRoundTileCatalog(listOf(Tile.Honor.East, Tile.Honor.South)),
                budget = ReplayReadBudget(ReplayReadLimits()) {},
            )
        }
    }

    /** 讀取摸牌後再捨牌的相鄰桌況，確認摸入牌可併入立牌並從獨立欄位移除。 */
    @Test
    fun `state mapping follows draw and discard transition`() {
        val mapper = HistoryReplayProjectionMapper(HistoryReplayProjectionRegistry().also(::registerBuiltInHistoryReplayProjections))
        val identity = identity()
        val catalog = HistoryRoundTileCatalog(listOf(Tile.Honor.East, Tile.Honor.South, Tile.Honor.West))
        val budget = ReplayReadBudget(ReplayReadLimits()) {}

        val afterDraw = mapper.mapState(
            playerProjection(standingTiles = listOf(0), lastDrawn = 1, wallTiles = listOf(2)),
            identity,
            1,
            HistoryRoundPosition.Initial,
            catalog,
            budget,
        )
        val afterDiscard = mapper.mapState(
            playerProjection(standingTiles = listOf(1), lastDrawn = null, wallTiles = listOf(2)),
            identity,
            1,
            HistoryRoundPosition.Initial,
            catalog,
            ReplayReadBudget(ReplayReadLimits()) {},
        )

        assertEquals(HistoryTileReference(1), afterDraw.players.single().lastDrawn)
        assertEquals(listOf(HistoryTileReference(1)), afterDiscard.players.single().handTiles)
        assertEquals(null, afterDiscard.players.single().lastDrawn)
    }

    /** 替換摸牌使用同一個獨立摸入欄位，不會把牌張誤判為立牌的一部分。 */
    @Test
    fun `state mapping preserves replacement draw separately`() {
        val mapper = HistoryReplayProjectionMapper(HistoryReplayProjectionRegistry().also(::registerBuiltInHistoryReplayProjections))
        val state = mapper.mapState(
            playerProjection(standingTiles = listOf(0, 1), lastDrawn = 2, wallTiles = emptyList()),
            identity(),
            1,
            HistoryRoundPosition.Initial,
            HistoryRoundTileCatalog(listOf(Tile.Honor.East, Tile.Honor.South, Tile.Honor.West)),
            ReplayReadBudget(ReplayReadLimits()) {},
        )

        assertEquals(listOf(HistoryTileReference(0), HistoryTileReference(1)), state.players.single().handTiles)
        assertEquals(HistoryTileReference(2), state.players.single().lastDrawn)
    }

    /** 不允許最後摸入牌同時出現在保留牌區，避免牌張跨區重複持有。 */
    @Test
    fun `state mapping rejects last drawn tile duplicated in reserved wall`() {
        val mapper = HistoryReplayProjectionMapper(HistoryReplayProjectionRegistry().also(::registerBuiltInHistoryReplayProjections))

        assertFailsWith<IllegalArgumentException> {
            mapper.mapState(
                projection = playerProjection(standingTiles = listOf(0), lastDrawn = 1, wallTiles = emptyList(), reservedTiles = listOf(1)),
                identity = identity(),
                roundNumber = 1,
                position = HistoryRoundPosition.Initial,
                tileCatalog = HistoryRoundTileCatalog(listOf(Tile.Honor.East, Tile.Honor.South)),
                budget = ReplayReadBudget(ReplayReadLimits()) {},
            )
        }
    }

    /** 局結算與規則效果使用已保存結果，不重新計算分數或丟失責任座位。 */
    @Test
    fun `completion mapping preserves saved classification and responsibility`() {
        val identity = HistoryReplayIdentity(Uuid.random(), Uuid.random(), listOf(HistoryReplayPlayerIdentity(0, Uuid.random(), null), HistoryReplayPlayerIdentity(1, Uuid.random(), null)))
        val summary = JsonObject(
            mapOf(
                "outcomeId" to JsonPrimitive("test:win"),
                "classification" to JsonPrimitive("WIN"),
                "beneficiaryPlayerIds" to JsonArray(listOf(JsonPrimitive(0))),
                "responsiblePlayerIds" to JsonArray(listOf(JsonPrimitive(1))),
                "settledScoresByPlayerId" to JsonObject(mapOf("0" to JsonPrimitive(26000), "1" to JsonPrimitive(24000))),
                "transitionDirective" to JsonPrimitive("ADVANCE_DEALER"),
            ),
        )
        val mapper = HistoryReplayProjectionMapper(HistoryReplayProjectionRegistry())
        val result = mapper.mapFacts(listOf(JsonObject(mapOf("type" to JsonPrimitive("round_completed"), "summary" to summary)), JsonObject(mapOf("type" to JsonPrimitive("rule_effect_resolved"), "reasonId" to JsonPrimitive("test:effect"), "roundCompletion" to summary))), listOf(null, null), identity, 1, HistoryRoundTileCatalog(emptyList()), ReplayReadBudget(ReplayReadLimits()) {})
        val completed = assertIs<HistoryReplayFact.Completion>(result[0]).outcome
        assertEquals(RoundCompletionClassification.WIN, completed?.classification)
        assertEquals(listOf(1), completed?.responsibleSeats)
        assertEquals(RoundTransitionDirective.ADVANCE_DEALER, completed?.transitionDirective)
        assertEquals(completed, assertIs<HistoryReplayFact.RuleEffect>(result[1]).outcome)
    }

    /** 擴充動作經明確 codec 映射，未註冊時只保留種類，不暴露私有資料。 */
    @Test
    fun `extension action mapping preserves opaque fallback and rejects missing envelope`() {
        val registry = HistoryReplayProjectionRegistry()
        val identity = HistoryReplayIdentity(Uuid.random(), Uuid.random(), listOf(HistoryReplayPlayerIdentity(0, Uuid.random(), null)))
        val budget = ReplayReadBudget(ReplayReadLimits()) {}
        val mapper = HistoryReplayProjectionMapper(registry)
        val action = JsonObject(mapOf("type" to JsonPrimitive("extension"), "value" to JsonObject(mapOf("typeKey" to JsonPrimitive("test:spell"), "payload" to JsonObject(mapOf("private" to JsonPrimitive("secret")))))))

        /** 映射一筆動作事實。
         * @param value 動作 envelope。
         * @return 公開歷史事實。
         */
        fun facts(value: JsonObject) = mapper.mapFacts(listOf(JsonObject(mapOf("type" to JsonPrimitive("action_accepted"), "action" to value))), listOf(0), identity, 1, HistoryRoundTileCatalog(emptyList()), budget)
        assertEquals(HistoryReplayFact.KnownAction("action_accepted", 0, "extension", emptyList(), emptyList(), "test:spell"), facts(action).single())
        registry.registerAction("test:spell") { _, scope -> HistoryReplayFact.Reaction("test:spell", "public", scope.seat(0)) }
        registry.freeze()
        assertEquals(HistoryReplayFact.Reaction("test:spell", "public", 0), facts(action).single())
        assertFailsWith<NoSuchElementException> { facts(JsonObject(mapOf("type" to JsonPrimitive("extension")))) }
    }

    /** 重複註冊不會取代已登記的 codec，凍結後不能新增。 */
    @Test
    fun `registry rejects duplicate and frozen registrations`() {
        val registry = HistoryReplayProjectionRegistry()
        val context = HistoryReplayProjectionContext(ReplayReadBudget(ReplayReadLimits()) {}, 2, 2)
        registry.registerFact("test:fact") { _, _ -> HistoryReplayFact.Opaque("test:original") }
        assertFailsWith<IllegalArgumentException> { registry.registerFact("test:fact") { _, _ -> HistoryReplayFact.Opaque("test:replacement") } }
        assertEquals(HistoryReplayFact.Opaque("test:original"), registry.decodeFact("test:fact", JsonNull, context))
        registry.freeze()
        assertFailsWith<IllegalStateException> { registry.registerDiscard("test:discard") { _, _ -> emptyList() } }
    }

    /** 擴充可以獨立解碼必要牌河、語意事實與可選公開資訊。 */
    @Test
    fun `typed extension codecs receive bounded context`() {
        val registry = HistoryReplayProjectionRegistry()
        val context = HistoryReplayProjectionContext(ReplayReadBudget(ReplayReadLimits()) {}, 2, 2)
        registry.registerDiscard("test:discard") { _, scope -> listOf(HistoryReplayDiscard(scope.tile(1), true, setOf("test:marker"))) }
        registry.registerFact("test:fact") { _, scope ->
            scope.charge()
            HistoryReplayFact.Reaction("test:fact", "test:action", scope.seat(1))
        }
        registry.registerOptionalRule("test:state") { _, scope ->
            scope.charge()
            HistoryReplayRuleInformation("test:state", "public")
        }
        registry.freeze()
        assertEquals(listOf(HistoryReplayDiscard(HistoryTileReference(1), true, setOf("test:marker"))), registry.decodeDiscard("test:discard", JsonNull, context))
        assertEquals(HistoryReplayFact.Reaction("test:fact", "test:action", 1), registry.decodeFact("test:fact", JsonNull, context))
        assertEquals(HistoryReplayRuleInformation("test:state", "public"), registry.decodeOptionalRule("test:state", JsonNull, context))
        assertEquals(null, registry.decodeDiscard("test:unknown", JsonNull, context))
        assertFailsWith<ReplayReadException> { context.tile(2) }
        assertFailsWith<ReplayReadException> { context.seat(2) }
    }

    /** 建立單玩家歷史身分標頭。
     * @return 可供投影測試使用的玩家身分。
     */
    private fun identity(): HistoryReplayIdentity = HistoryReplayIdentity(
        Uuid.random(),
        Uuid.random(),
        listOf(HistoryReplayPlayerIdentity(0, Uuid.random(), null)),
    )

    /** 建立包含手牌、牌牆與必要局位欄位的桌況投影。
     * @param standingTiles 立牌索引。
     * @param lastDrawn 最近摸入牌索引，沒有時為 null。
     * @param wallTiles 活牌牆索引。
     * @param reservedTiles 保留牌區索引。
     * @return 可供投影映射器讀取的 JSON 桌況。
     */
    private fun playerProjection(
        standingTiles: List<Int>,
        lastDrawn: Int?,
        wallTiles: List<Int>,
        reservedTiles: List<Int> = emptyList(),
    ): JsonObject = buildJsonObject {
        put(
            "players",
            JsonArray(
                listOf(
                    buildJsonObject {
                        put("initialSeatIndex", 0)
                        put(
                            "hand",
                            buildJsonObject {
                                put("tiles", JsonArray(standingTiles.map(::JsonPrimitive)))
                                put("melds", JsonArray(emptyList()))
                                put("lastDrawn", lastDrawn?.let(::JsonPrimitive) ?: JsonNull)
                            },
                        )
                        put(
                            "discardPile",
                            buildJsonObject {
                                put("typeKey", "builtin:riichi_discard_pile")
                                put("payload", buildJsonObject { put("entries", JsonArray(emptyList())) })
                            },
                        )
                        put("score", 25000)
                        put("seatWind", "EAST")
                        put("playerRuleState", JsonNull)
                    },
                ),
            ),
        )
        put("tileWall", buildJsonObject { put("tiles", JsonArray(wallTiles.map(::JsonPrimitive))) })
        put("initialDeadWall", JsonArray(reservedTiles.map(::JsonPrimitive)))
        put("dealerPlayerId", 0)
        put("currentPlayerIndex", 0)
        put(
            "roundPosition",
            buildJsonObject {
                put("sequenceIndex", 0)
                put("prevalentWind", "EAST")
                put("localRoundNumber", 1)
                put("phase", "REGULAR")
            },
        )
        put("prevalentWind", "EAST")
        put("comboCount", 0)
        put("finishedPlayerIds", JsonArray(emptyList()))
        put("dynamicRuleState", JsonNull)
        put("pendingReaction", JsonNull)
        put("pendingKanReaction", JsonNull)
    }
}
