package com.doublemoon1119.mahjongcraft.flow.persistence.format.history.replay

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

/** 驗證 Replay 讀取資源記帳與限制檢查。 */
class ReplayReadBudgetTest {
    /** JSON 測試解析器。 */
    private val json = Json

    /** 預設限制應提供穩定的安全邊界。 */
    @Test
    fun `default limits expose the replay read contract`() {
        val limits = ReplayReadLimits()
        assertEquals(64, limits.maxDepth)
        assertEquals(1_000_000L, limits.maxInputNodes)
        assertEquals(1_000_000L, limits.maxProjectionNodes)
        assertEquals(32L * 1024L * 1024L, limits.maxExpandedStringBytes)
        assertEquals(8_000_000L, limits.maxWorkUnits)
        assertEquals(256L, limits.cancellationInterval)
    }

    /** 輸入節點與深度限制應拒絕超出上限的文件。 */
    @Test
    fun `input inspection enforces node and depth limits`() {
        val nodeBudget = ReplayReadBudget(ReplayReadLimits(maxInputNodes = 2), {})
        assertFailsWith<ReplayReadException> { nodeBudget.inspectInput(json.parseToJsonElement("[1,2]")) }

        val depthBudget = ReplayReadBudget(ReplayReadLimits(maxDepth = 2), {})
        assertFailsWith<ReplayReadException> { depthBudget.inspectInput(json.parseToJsonElement("[[1]]")) }
    }

    /** 投影節點與字串大小限制應拒絕超出上限的展開。 */
    @Test
    fun `projection inspection enforces live nodes and expanded strings`() {
        val nodeBudget = ReplayReadBudget(ReplayReadLimits(maxProjectionNodes = 1), {})
        assertFailsWith<ReplayReadException> { nodeBudget.inspectProjection(json.parseToJsonElement("[1]")) }

        val stringBudget = ReplayReadBudget(ReplayReadLimits(maxExpandedStringBytes = 3), {})
        stringBudget.expandedString("四")
        assertFailsWith<ReplayReadException> { stringBudget.expandedString("a") }
    }

    /** 工作單位應安全拒絕溢出並依間隔觸發取消檢查。 */
    @Test
    fun `charging checks cancellation and work limit`() {
        var checks = 0
        val budget = ReplayReadBudget(ReplayReadLimits(maxWorkUnits = 3, cancellationInterval = 2)) { checks++ }
        budget.charge(3)
        assertEquals(2, checks)
        assertFailsWith<ReplayReadException> { budget.charge(1) }
    }

    /** 診斷資料應累積節點、字串與交易統計。 */
    @Test
    fun `diagnostics report cumulative accounting`() {
        val budget = ReplayReadBudget(ReplayReadLimits(), {})
        budget.inspectProjection(json.parseToJsonElement("{\"name\":\"ok\"}"))
        assertEquals(0L, budget.diagnostics().expandedNodes)
        budget.expandedNode()
        budget.expandedNode()
        budget.expandedString("ok")
        budget.transactionRebuilt()
        val diagnostics = budget.diagnostics()
        assertEquals(2L, diagnostics.expandedNodes)
        assertEquals(2L, diagnostics.expandedStringBytes)
        assertEquals(1, diagnostics.transactionsRebuilt)
    }

    /** 有界套用沿用原有數字字串路徑，且不改變完整 decoder 的結果。 */
    @Test
    fun `bounded patch shares legacy numeric string path grammar`() {
        val before = json.parseToJsonElement("{\"players\":[{\"score\":1}]}")
        val path = listOf(JsonPrimitive("players"), JsonPrimitive("0"), JsonPrimitive("score"))
        val operations = JsonArray(listOf(JsonArray(listOf(JsonPrimitive(0), JsonPrimitive(0), JsonPrimitive(20)))))
        assertEquals(FlatPatchCodec.apply(before, operations, listOf(path)), FlatPatchCodec.applyBounded(before, operations, listOf(path), ReplayReadBudget(ReplayReadLimits()) {}))
    }

    /** 加入原本不存在的欄位也須在配置前計入新節點，不能少算一個基底節點。 */
    @Test
    fun `patch rejects growth before replacing missing leaf`() {
        val before = JsonObject(emptyMap())
        val path = listOf(JsonPrimitive("added"))
        val operations = JsonArray(listOf(JsonArray(listOf(JsonPrimitive(0), JsonPrimitive(0), JsonArray(listOf(JsonPrimitive(1)))))))
        val failure = assertFailsWith<ReplayReadException> { FlatPatchCodec.applyBounded(before, operations, listOf(path), ReplayReadBudget(ReplayReadLimits(maxProjectionNodes = 2)) {}) }
        assertEquals(ReplayReadError.LIMIT_EXCEEDED, failure.error)
        assertEquals(JsonObject(emptyMap()), before)
    }

    /** 字典展開而非重新檢查既有投影時，才累積展開節點與字串位元組。 */
    @Test
    fun `dictionary expansion accounts restored strings and nodes`() {
        val budget = ReplayReadBudget(ReplayReadLimits()) {}
        val encoded = CompactReplayDictionary.encode(json.parseToJsonElement("{\"name\":\"ok\"}"))
        val view = CompactReplayDictionary.open(encoded, budget)
        view.expand(view.data)
        assertEquals(2L, budget.diagnostics().expandedNodes)
        assertEquals(6L, budget.diagnostics().expandedStringBytes)
    }
}
