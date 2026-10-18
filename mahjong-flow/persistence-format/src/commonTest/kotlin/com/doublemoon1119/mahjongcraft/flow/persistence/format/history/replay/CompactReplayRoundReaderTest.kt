package com.doublemoon1119.mahjongcraft.flow.persistence.format.history.replay

import com.doublemoon1119.mahjongcraft.flow.common.game.history.HistoryFactTypeKeys
import com.doublemoon1119.mahjongcraft.flow.common.game.history.replay.HistoryActionTypeKeys
import com.doublemoon1119.mahjongcraft.flow.common.game.history.replay.HistoryReplayDiscard
import com.doublemoon1119.mahjongcraft.flow.common.game.history.replay.HistoryReplayFact
import com.doublemoon1119.mahjongcraft.flow.common.game.history.replay.HistoryReplayIdentity
import com.doublemoon1119.mahjongcraft.flow.common.game.history.replay.HistoryReplayPlayerIdentity
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

/** 驗證局級讀取把不分規則的投影轉為歷史讀模型，並以受限索引上下文驗證參照。 */
class CompactReplayRoundReaderTest {
    /** 讀取摸牌後的投影時，最後摸入牌與立牌分開保留。 */
    @Test
    fun `state mapping preserves separately stored last drawn tile`() {
        val state = HistoryReplayProjectionMapper.mapState(
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
        assertFailsWith<IllegalArgumentException> {
            HistoryReplayProjectionMapper.mapState(
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
        assertFailsWith<IllegalArgumentException> {
            HistoryReplayProjectionMapper.mapState(
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
        val identity = identity()
        val catalog = HistoryRoundTileCatalog(listOf(Tile.Honor.East, Tile.Honor.South, Tile.Honor.West))
        val budget = ReplayReadBudget(ReplayReadLimits()) {}

        val afterDraw = HistoryReplayProjectionMapper.mapState(
            playerProjection(standingTiles = listOf(0), lastDrawn = 1, wallTiles = listOf(2)),
            identity,
            1,
            HistoryRoundPosition.Initial,
            catalog,
            budget,
        )
        val afterDiscard = HistoryReplayProjectionMapper.mapState(
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
        val state = HistoryReplayProjectionMapper.mapState(
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
        assertFailsWith<IllegalArgumentException> {
            HistoryReplayProjectionMapper.mapState(
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
                ReplaySourceKeys.OUTCOME_ID to JsonPrimitive("test:win"),
                ReplaySourceKeys.CLASSIFICATION to JsonPrimitive("WIN"),
                ReplaySourceKeys.BENEFICIARY_PLAYER_IDS to JsonArray(listOf(JsonPrimitive(0))),
                ReplaySourceKeys.RESPONSIBLE_PLAYER_IDS to JsonArray(listOf(JsonPrimitive(1))),
                ReplaySourceKeys.SETTLED_SCORES_BY_PLAYER_ID to JsonObject(mapOf("0" to JsonPrimitive(26000), "1" to JsonPrimitive(24000))),
                ReplaySourceKeys.TRANSITION_DIRECTIVE to JsonPrimitive("ADVANCE_DEALER"),
            ),
        )
        val result = HistoryReplayProjectionMapper.mapFacts(listOf(JsonObject(mapOf(ReplaySourceKeys.TYPE to JsonPrimitive(HistoryFactTypeKeys.ROUND_COMPLETED), ReplaySourceKeys.SUMMARY to summary)), JsonObject(mapOf(ReplaySourceKeys.TYPE to JsonPrimitive(HistoryFactTypeKeys.RULE_EFFECT_RESOLVED), ReplaySourceKeys.REASON_ID to JsonPrimitive("test:effect"), ReplaySourceKeys.ROUND_COMPLETION to summary))), listOf(null, null), identity, 1, HistoryRoundTileCatalog(emptyList()), ReplayReadBudget(ReplayReadLimits()) {})
        val completed = assertIs<HistoryReplayFact.Completion>(result[0]).outcome
        assertEquals(RoundCompletionClassification.WIN, completed?.classification)
        assertEquals(listOf(1), completed?.responsibleSeats)
        assertEquals(RoundTransitionDirective.ADVANCE_DEALER, completed?.transitionDirective)
        assertEquals(completed, assertIs<HistoryReplayFact.RuleEffect>(result[1]).outcome)
    }

    /** 中止事實保留原因，但不偽造完整對局的分數或結算分類。 */
    @Test
    fun `aborted match mapping preserves reason without completion details`() {
        val identity = HistoryReplayIdentity(
            matchId = Uuid.random(),
            venueId = Uuid.random(),
            players = listOf(HistoryReplayPlayerIdentity(0, Uuid.random(), null)),
        )
        val fact = JsonObject(
            mapOf(
                ReplaySourceKeys.TYPE to JsonPrimitive(HistoryFactTypeKeys.MATCH_ABORTED),
                ReplaySourceKeys.REASON_ID to JsonPrimitive("test:aborted"),
            ),
        )

        val result = HistoryReplayProjectionMapper.mapFacts(
            facts = listOf(fact),
            actorSeats = listOf(null),
            identity = identity,
            roundNumber = 1,
            tileCatalog = HistoryRoundTileCatalog(emptyList()),
            budget = ReplayReadBudget(ReplayReadLimits()) {},
        ).single()

        val completion = assertIs<HistoryReplayFact.Completion>(result)
        assertEquals(HistoryFactTypeKeys.MATCH_ABORTED, completion.typeKey)
        assertEquals("test:aborted", completion.outcome?.reasonId)
        assertEquals(emptyMap(), completion.outcome?.scoresBySeat)
        assertEquals(null, completion.outcome?.classification)
    }

    /** 擲骰事實保留輪次、行為者與每顆骰子的點數。 */
    @Test
    fun `dice roll mapping preserves transcript fields`() {
        val identity = identity()
        val fact = buildJsonObject {
            put(ReplaySourceKeys.TYPE, HistoryFactTypeKeys.DICE_ROLLED)
            put(
                ReplaySourceKeys.ROLL,
                buildJsonObject {
                    put(ReplaySourceKeys.ROLL_INDEX, 2)
                    put(ReplaySourceKeys.PLAYER_ID, 0)
                    put(ReplaySourceKeys.DICE, JsonArray(listOf(JsonPrimitive(2), JsonPrimitive(6))))
                },
            )
        }

        val result = HistoryReplayProjectionMapper.mapFacts(
            facts = listOf(fact),
            actorSeats = listOf(0),
            identity = identity,
            roundNumber = 1,
            tileCatalog = HistoryRoundTileCatalog(emptyList()),
            budget = ReplayReadBudget(ReplayReadLimits()) {},
        ).single()

        assertEquals(HistoryReplayFact.DiceRolled(HistoryFactTypeKeys.DICE_ROLLED, 2, 0, listOf(2, 6)), result)
    }

    /** 擲骰事實的記錄玩家與交易行為者不一致時拒絕回放。 */
    @Test
    fun `dice roll mapping rejects actor mismatch`() {
        val identity = HistoryReplayIdentity(
            matchId = Uuid.random(),
            venueId = Uuid.random(),
            players = listOf(
                HistoryReplayPlayerIdentity(0, Uuid.random(), null),
                HistoryReplayPlayerIdentity(1, Uuid.random(), null),
            ),
        )
        val fact = buildJsonObject {
            put(ReplaySourceKeys.TYPE, HistoryFactTypeKeys.DICE_ROLLED)
            put(
                ReplaySourceKeys.ROLL,
                buildJsonObject {
                    put(ReplaySourceKeys.ROLL_INDEX, 0)
                    put(ReplaySourceKeys.PLAYER_ID, 1)
                    put(ReplaySourceKeys.DICE, JsonArray(listOf(JsonPrimitive(1), JsonPrimitive(1))))
                },
            )
        }

        assertFailsWith<IllegalArgumentException> {
            HistoryReplayProjectionMapper.mapFacts(
                facts = listOf(fact),
                actorSeats = listOf(0),
                identity = identity,
                roundNumber = 1,
                tileCatalog = HistoryRoundTileCatalog(emptyList()),
                budget = ReplayReadBudget(ReplayReadLimits()) {},
            )
        }
    }

    /** 擴充動作只保留種類，不暴露私有資料；缺少種類的 envelope 被拒絕。 */
    @Test
    fun `extension action mapping keeps only its type and rejects missing envelope`() {
        val identity = HistoryReplayIdentity(Uuid.random(), Uuid.random(), listOf(HistoryReplayPlayerIdentity(0, Uuid.random(), null)))
        val budget = ReplayReadBudget(ReplayReadLimits()) {}
        val action = JsonObject(mapOf(ReplaySourceKeys.TYPE to JsonPrimitive(HistoryActionTypeKeys.EXTENSION), ReplaySourceKeys.VALUE to JsonObject(mapOf(ReplaySourceKeys.TYPE_KEY to JsonPrimitive("test:spell"), ReplayFormatKeys.PAYLOAD to JsonObject(mapOf("private" to JsonPrimitive("secret")))))))

        /**
         * 映射一筆動作事實。
         *
         * @param value 動作 envelope。
         * @return 公開歷史事實。
         */
        fun facts(value: JsonObject) = HistoryReplayProjectionMapper.mapFacts(listOf(JsonObject(mapOf(ReplaySourceKeys.TYPE to JsonPrimitive(HistoryFactTypeKeys.ACTION_ACCEPTED), ReplaySourceKeys.ACTION to value))), listOf(0), identity, 1, HistoryRoundTileCatalog(emptyList()), budget)
        assertEquals(HistoryReplayFact.KnownAction(HistoryFactTypeKeys.ACTION_ACCEPTED, 0, HistoryActionTypeKeys.EXTENSION, emptyList(), emptyList(), "test:spell"), facts(action).single())
        assertFailsWith<NoSuchElementException> { facts(JsonObject(mapOf(ReplaySourceKeys.TYPE to JsonPrimitive(HistoryActionTypeKeys.EXTENSION)))) }
    }

    /** 牌河項目保留牌、鳴走與公開標記；省略的欄位代表未鳴走與沒有標記。 */
    @Test
    fun `state mapping reads rule neutral discards`() {
        val state = HistoryReplayProjectionMapper.mapState(
            projection = playerProjection(
                standingTiles = listOf(0),
                lastDrawn = null,
                wallTiles = emptyList(),
                discards = listOf(
                    discard(tile = 1),
                    discard(tile = 2, taken = true, markers = listOf("test:declared")),
                ),
            ),
            identity = identity(),
            roundNumber = 1,
            position = HistoryRoundPosition.Initial,
            tileCatalog = HistoryRoundTileCatalog(listOf(Tile.Honor.East, Tile.Honor.South, Tile.Honor.West)),
            budget = ReplayReadBudget(ReplayReadLimits()) {},
        )

        assertEquals(
            listOf(
                HistoryReplayDiscard(HistoryTileReference(1), false, emptySet()),
                HistoryReplayDiscard(HistoryTileReference(2), true, setOf("test:declared")),
            ),
            state.players.single().discards,
        )
    }

    /** 移出手牌的牌依保存順序讀出。 */
    @Test
    fun `state mapping reads set aside tiles in saved order`() {
        val state = HistoryReplayProjectionMapper.mapState(
            projection = playerProjection(standingTiles = listOf(0), lastDrawn = null, wallTiles = emptyList(), setAsideTiles = listOf(2, 1)),
            identity = identity(),
            roundNumber = 1,
            position = HistoryRoundPosition.Initial,
            tileCatalog = HistoryRoundTileCatalog(listOf(Tile.Honor.East, Tile.Honor.North, Tile.Honor.North)),
            budget = ReplayReadBudget(ReplayReadLimits()) {},
        )

        assertEquals(listOf(HistoryTileReference(2), HistoryTileReference(1)), state.players.single().setAsideTiles)
    }

    /** 牌河項目出現未知欄位、明寫未鳴走或參照未宣告的牌時拒絕整份投影。 */
    @Test
    fun `state mapping rejects malformed discards`() {
        val malformed = listOf(
            JsonObject(mapOf(ReplayFormatKeys.DISCARD_TILE to JsonPrimitive(1), "isRiichi" to JsonPrimitive(true))),
            JsonObject(mapOf(ReplayFormatKeys.DISCARD_TILE to JsonPrimitive(1), ReplayFormatKeys.DISCARD_TAKEN to JsonPrimitive(false))),
            discard(tile = 5),
        )

        malformed.forEach { entry ->
            assertFailsWith<Exception>("$entry") {
                HistoryReplayProjectionMapper.mapState(
                    projection = playerProjection(standingTiles = listOf(0), lastDrawn = null, wallTiles = emptyList(), discards = listOf(entry)),
                    identity = identity(),
                    roundNumber = 1,
                    position = HistoryRoundPosition.Initial,
                    tileCatalog = HistoryRoundTileCatalog(listOf(Tile.Honor.East, Tile.Honor.South)),
                    budget = ReplayReadBudget(ReplayReadLimits()) {},
                )
            }
        }
    }

    /** 受限上下文拒絕超出已宣告範圍的牌與座位索引。 */
    @Test
    fun `projection context rejects out of range references`() {
        val context = HistoryReplayProjectionContext(ReplayReadBudget(ReplayReadLimits()) {}, 2, 2)

        assertEquals(HistoryTileReference(1), context.tile(1))
        assertEquals(1, context.seat(1))
        assertFailsWith<ReplayReadException> { context.tile(2) }
        assertFailsWith<ReplayReadException> { context.seat(2) }
    }

    /**
     * 建立單玩家歷史身分標頭。
     *
     * @return 可供投影測試使用的玩家身分。
     */
    private fun identity(): HistoryReplayIdentity = HistoryReplayIdentity(
        Uuid.random(),
        Uuid.random(),
        listOf(HistoryReplayPlayerIdentity(0, Uuid.random(), null)),
    )

    /**
     * 建立包含手牌、牌牆與必要局位欄位的桌況投影。
     *
     * @param standingTiles 立牌索引。
     * @param lastDrawn 最近摸入牌索引，沒有時為 null。
     * @param wallTiles 活牌牆索引。
     * @param reservedTiles 保留牌區索引。
     * @param discards 牌河項目。
     * @param setAsideTiles 移出手牌的牌索引。
     * @return 可供投影映射器讀取的 JSON 桌況。
     */
    private fun playerProjection(
        standingTiles: List<Int>,
        lastDrawn: Int?,
        wallTiles: List<Int>,
        reservedTiles: List<Int> = emptyList(),
        discards: List<JsonObject> = emptyList(),
        setAsideTiles: List<Int> = emptyList(),
    ): JsonObject = buildJsonObject {
        put(
            ReplayFormatKeys.PLAYERS,
            JsonArray(
                listOf(
                    buildJsonObject {
                        put("initialSeatIndex", 0)
                        put(
                            ReplaySourceKeys.HAND,
                            buildJsonObject {
                                put(ReplaySourceKeys.HAND_TILES, JsonArray(standingTiles.map(::JsonPrimitive)))
                                put(ReplaySourceKeys.MELDS, JsonArray(emptyList()))
                                put(ReplaySourceKeys.LAST_DRAWN, lastDrawn?.let(::JsonPrimitive) ?: JsonNull)
                            },
                        )
                        put(ReplayFormatKeys.DISCARDS, JsonArray(discards))
                        put(ReplayFormatKeys.SET_ASIDE_TILES, JsonArray(setAsideTiles.map(::JsonPrimitive)))
                        put(ReplaySourceKeys.SCORE, 25000)
                        put(ReplaySourceKeys.SEAT_WIND, "EAST")
                    },
                ),
            ),
        )
        put(ReplaySourceKeys.TILE_WALL, buildJsonObject { put(ReplaySourceKeys.WALL_TILES, JsonArray(wallTiles.map(::JsonPrimitive))) })
        put(ReplaySourceKeys.RESERVED_WALL_TILES, JsonArray(reservedTiles.map(::JsonPrimitive)))
        put(ReplaySourceKeys.DEALER_PLAYER_ID, 0)
        put(ReplaySourceKeys.CURRENT_PLAYER_INDEX, 0)
        put(
            ReplaySourceKeys.ROUND_POSITION,
            buildJsonObject {
                put("sequenceIndex", 0)
                put(ReplaySourceKeys.PREVALENT_WIND, "EAST")
                put("localRoundNumber", 1)
                put("phase", "REGULAR")
            },
        )
        put(ReplaySourceKeys.PREVALENT_WIND, "EAST")
        put(ReplaySourceKeys.COMBO_COUNT, 0)
        put(ReplaySourceKeys.FINISHED_PLAYER_IDS, JsonArray(emptyList()))
        put(ReplaySourceKeys.PENDING_REACTION, JsonNull)
        put(ReplaySourceKeys.PENDING_ROBBING_REACTION, JsonNull)
    }

    /**
     * 建立一筆不分規則的牌河項目。
     *
     * @param tile 牌索引。
     * @param taken 是否已被鳴走；未鳴走時省略欄位。
     * @param markers 公開標記；沒有標記時省略欄位。
     * @return 牌河項目投影。
     */
    private fun discard(
        tile: Int,
        taken: Boolean = false,
        markers: List<String> = emptyList(),
    ): JsonObject = buildJsonObject {
        put(ReplayFormatKeys.DISCARD_TILE, tile)
        if (taken) put(ReplayFormatKeys.DISCARD_TAKEN, true)
        if (markers.isNotEmpty()) put(ReplayFormatKeys.DISCARD_MARKERS, JsonArray(markers.map(::JsonPrimitive)))
    }
}
