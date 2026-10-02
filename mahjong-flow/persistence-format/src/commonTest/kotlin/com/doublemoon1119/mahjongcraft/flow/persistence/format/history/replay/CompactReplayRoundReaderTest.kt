package com.doublemoon1119.mahjongcraft.flow.persistence.format.history.replay

import com.doublemoon1119.mahjongcraft.flow.common.game.history.replay.HistoryReplayDiscard
import com.doublemoon1119.mahjongcraft.flow.common.game.history.replay.HistoryReplayFact
import com.doublemoon1119.mahjongcraft.flow.common.game.history.replay.HistoryReplayIdentity
import com.doublemoon1119.mahjongcraft.flow.common.game.history.replay.HistoryReplayPlayerIdentity
import com.doublemoon1119.mahjongcraft.flow.common.game.history.replay.HistoryReplayRuleInformation
import com.doublemoon1119.mahjongcraft.flow.common.game.history.replay.HistoryRoundTileCatalog
import com.doublemoon1119.mahjongcraft.flow.common.game.history.replay.HistoryTileReference
import com.doublemoon1119.mahjongcraft.logic.table.RoundCompletionClassification
import com.doublemoon1119.mahjongcraft.logic.table.RoundTransitionDirective
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
import kotlin.uuid.Uuid

/** 驗證局級讀取使用的強型別擴充 registry 與受限索引上下文。 */
class CompactReplayRoundReaderTest {
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
}
