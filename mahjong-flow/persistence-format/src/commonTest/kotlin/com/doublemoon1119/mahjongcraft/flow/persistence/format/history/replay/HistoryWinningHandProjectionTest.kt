package com.doublemoon1119.mahjongcraft.flow.persistence.format.history.replay

import com.doublemoon1119.mahjongcraft.flow.common.game.history.replay.HistoryReplayFact
import com.doublemoon1119.mahjongcraft.flow.common.game.history.replay.HistoryReplayIdentity
import com.doublemoon1119.mahjongcraft.flow.common.game.history.replay.HistoryReplayPlayerIdentity
import com.doublemoon1119.mahjongcraft.flow.common.game.history.replay.HistoryRoundTileCatalog
import com.doublemoon1119.mahjongcraft.flow.common.game.history.replay.HistoryTileReference
import com.doublemoon1119.mahjongcraft.logic.base.Tile
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.uuid.Uuid

/** 驗證精簡保存後的和牌牌組描述可投影為局內索引，且缺少描述時維持 null。 */
class HistoryWinningHandProjectionTest {
    /** 多名贏家可共享榮和／搶槓的外部和牌索引，立牌順序與局內索引均保留。 */
    @Test
    fun `compact win details project shared external winning tile`() {
        val details = JsonArray(
            listOf(
                winner(0, listOf(2, 0), 1),
                winner(1, listOf(3, 4), 1),
            ),
        )
        val fact = JsonObject(
            mapOf(
                "type" to JsonPrimitive("win_settled"),
                "outcomeId" to JsonPrimitive("test:ron"),
                "responsiblePlayerIds" to JsonArray(listOf(JsonPrimitive(2))),
                "winDetails" to details,
            ),
        )
        val encoded = CompactFactCodec.encode(fact, linkedMapOf(), linkedMapOf())
        val restored = CompactFactCodec.decode(encoded, listOf("win_settled"), emptyList())
        val projected = mapper().mapFacts(
            listOf(restored),
            listOf(null),
            identity(3),
            1,
            catalog(5),
            budget(),
        ).single()
        val completion = assertIs<HistoryReplayFact.Completion>(projected)
        val winners = completion.outcome?.winnerDetails.orEmpty()

        assertEquals(listOf(2, 0), winners[0].hand?.standingTiles?.map(HistoryTileReference::tileIndex))
        assertEquals(1, winners[0].hand?.winningTile?.tileIndex)
        assertEquals(listOf(3, 4), winners[1].hand?.standingTiles?.map(HistoryTileReference::tileIndex))
        assertEquals(1, winners[1].hand?.winningTile?.tileIndex)
    }

    /** 和牌詳情未保存時，精簡編碼及歷史投影均維持 optional hand 為 null。 */
    @Test
    fun `compact win details preserve absent winning hand`() {
        val fact = JsonObject(
            mapOf(
                "type" to JsonPrimitive("win_settled"),
                "outcomeId" to JsonPrimitive("test:ron"),
                "responsiblePlayerIds" to JsonArray(emptyList()),
                "winDetails" to JsonArray(listOf(winner(0, null, null))),
            ),
        )
        val encoded = CompactFactCodec.encode(fact, linkedMapOf(), linkedMapOf())
        val restored = CompactFactCodec.decode(encoded, listOf("win_settled"), emptyList())
        val projected = mapper().mapFacts(listOf(restored), listOf(null), identity(1), 1, catalog(1), budget()).single()
        val completion = assertIs<HistoryReplayFact.Completion>(projected)

        assertNull(completion.outcome?.winnerDetails?.single()?.hand)
    }

    /** 建立包含牌目錄中的局內牌索引。
     * @param size 牌目錄大小。
     * @return 供投影上下文使用的牌目錄。
     */
    private fun catalog(size: Int) = HistoryRoundTileCatalog(List(size) { Tile.Numeric(Tile.Suit.Character, (it % 9) + 1) })

    /** 建立受限投影預算。
     * @return 可供單筆測試事實使用的讀取預算。
     */
    private fun budget() = ReplayReadBudget(ReplayReadLimits()) {}

    /** 建立投影 mapper。
     * @return 未註冊擴充資料的標準 mapper。
     */
    private fun mapper() = HistoryReplayProjectionMapper(HistoryReplayProjectionRegistry())

    /** 建立測試用 canonical 玩家身分。
     * @param count 玩家數量。
     * @return 與事實座位索引相符的對局身分。
     */
    private fun identity(count: Int) = HistoryReplayIdentity(
        Uuid.parse("00000000-0000-0000-0000-000000000001"),
        Uuid.parse("00000000-0000-0000-0000-000000000002"),
        List(count) { HistoryReplayPlayerIdentity(it, Uuid.random(), null) },
    )

    /** 建立單一贏家明細。
     * @param seat 贏家座位索引。
     * @param standing 有序立牌索引；null 表示不保存牌組描述。
     * @param winning 獨立和牌索引；特殊結算時為 null。
     * @return 可交給精簡事實 codec 的贏家 JSON。
     */
    private fun winner(seat: Int, standing: List<Int>?, winning: Int?): JsonObject = JsonObject(
        buildMap {
            put("playerId", JsonPrimitive(seat))
            put("templateKey", JsonPrimitive("test:template"))
            put("detailFields", JsonArray(emptyList()))
            if (standing != null) {
                put(
                    "hand",
                    JsonObject(
                        mapOf(
                            "standingTileIds" to JsonArray(standing.map(::JsonPrimitive)),
                            "winningTileId" to (winning?.let(::JsonPrimitive) ?: JsonNull),
                        ),
                    ),
                )
            }
        },
    )
}
