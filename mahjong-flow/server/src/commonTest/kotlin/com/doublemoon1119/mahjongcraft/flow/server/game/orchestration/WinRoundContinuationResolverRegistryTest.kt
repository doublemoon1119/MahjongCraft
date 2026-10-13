package com.doublemoon1119.mahjongcraft.flow.server.game.orchestration

import com.doublemoon1119.mahjongcraft.flow.common.game.model.ContinuingWinSettlementDetail
import com.doublemoon1119.mahjongcraft.flow.common.game.model.WinRoundContinuationContext
import com.doublemoon1119.mahjongcraft.flow.common.game.model.WinRoundDirective
import com.doublemoon1119.mahjongcraft.logic.module.MahjongRuleModule
import com.doublemoon1119.mahjongcraft.logic.rules.riichi.RiichiRuleConfig
import com.doublemoon1119.mahjongcraft.logic.rules.riichi.RiichiRuleModule
import com.doublemoon1119.mahjongcraft.testing.logic.table.FakeMahjongPlayerFactory
import com.doublemoon1119.mahjongcraft.testing.logic.table.FakeTableStateFactory
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.uuid.Uuid

/** [WinRoundContinuationResolverRegistry] 的順序、規則隔離、預設值與凍結測試。 */
class WinRoundContinuationResolverRegistryTest {
    /** 解析器所屬的立直規則模組。 */
    private val module = RiichiRuleModule("mahjongcraft:riichi", RiichiRuleConfig())

    /** 用於建立和牌後去留情境的贏家。 */
    private val winner = FakeMahjongPlayerFactory.create()

    /** 共用的結算前後狀態與和牌資訊。 */
    private val context = WinRoundContinuationContext(
        previousTableState = FakeTableStateFactory.create(players = listOf(winner), config = RiichiRuleConfig()),
        settledTableState = FakeTableStateFactory.create(players = listOf(winner), config = RiichiRuleConfig()),
        winnerPlayerIds = setOf(winner.id),
        ronDiscarderId = null,
        winningTileId = Uuid.random(),
    )

    /** 未替該規則模組登記任何 resolver 時，固定回傳 EndRound——確保現有規則行為不變。 */
    @Test
    fun `resolve defaults to EndRound when nothing is registered`() {
        val registry = WinRoundContinuationResolverRegistry().apply { freeze() }

        assertEquals(WinRoundDirective.EndRound, registry.resolve(context, module))
    }

    /** 驗證 resolver 依 priority、ID 穩定排序，且只執行目前規則的項目，第一個非 null 結果勝出。 */
    @Test
    fun `resolvers use stable order and matching rule module`() {
        val calls = mutableListOf<String>()
        val expectedDirective = WinRoundDirective.ContinueRound(
            newlyFinishedPlayerIds = setOf(winner.id),
            nextPlayerId = winner.id,
            settlementDetail = ContinuingWinSettlementDetail.SCORE_CHANGES_ONLY,
        )
        val registry = WinRoundContinuationResolverRegistry().apply {
            register(recordingResolver("test:z", module.id, 20, calls, null))
            register(recordingResolver("test:foreign", "test:foreign_rule", 0, calls, expectedDirective))
            register(recordingResolver("test:b", module.id, 10, calls, expectedDirective))
            register(recordingResolver("test:a", module.id, 10, calls, null))
            freeze()
        }

        val directive = registry.resolve(context, module)

        // test:z 優先序 20，先執行；相同優先序 10 再依 ID 交由 test:a、test:b 判定。
        // 前兩者回傳 null，因此由 test:b 提供結果；其他規則的 test:foreign 不會被呼叫。
        assertEquals(listOf("test:z", "test:a", "test:b"), calls)
        assertEquals(expectedDirective, directive)
    }

    /** 驗證凍結後拒絕任何額外登記。 */
    @Test
    fun `registration is rejected after freeze`() {
        val registry = WinRoundContinuationResolverRegistry().apply { freeze() }
        assertFailsWith<IllegalStateException> {
            registry.register(recordingResolver("test:late", "test:rule", 0, mutableListOf(), null))
        }
    }

    /** 驗證同一規則模組內的重複 ID 登記會被拒絕。 */
    @Test
    fun `duplicate id registration is rejected`() {
        val registry = WinRoundContinuationResolverRegistry()
        registry.register(recordingResolver("test:dup", module.id, 0, mutableListOf(), null))

        assertFailsWith<IllegalArgumentException> {
            registry.register(recordingResolver("test:dup", module.id, 0, mutableListOf(), null))
        }
    }

    /** 驗證 resolver ID 跨規則模組仍是全域唯一識別碼。 */
    @Test
    fun `duplicate id across rule modules is rejected`() {
        val registry = WinRoundContinuationResolverRegistry()
        registry.register(recordingResolver("test:dup", module.id, 0, mutableListOf(), null))

        assertFailsWith<IllegalArgumentException> {
            registry.register(recordingResolver("test:dup", "test:other_rule", 0, mutableListOf(), null))
        }
    }

    /** 驗證 priority 的整數邊界仍依數值由高到低執行。 */
    @Test
    fun `priority integer boundaries preserve descending order`() {
        val calls = mutableListOf<String>()
        val registry = WinRoundContinuationResolverRegistry().apply {
            register(recordingResolver("test:min", module.id, Int.MIN_VALUE, calls, null))
            register(recordingResolver("test:max", module.id, Int.MAX_VALUE, calls, null))
            freeze()
        }

        registry.resolve(context, module)

        assertEquals(listOf("test:max", "test:min"), calls)
    }

    /** 未凍結就呼叫 resolve 應拋出例外。 */
    @Test
    fun `resolve before freeze throws`() {
        val registry = WinRoundContinuationResolverRegistry()
        assertFailsWith<IllegalStateException> { registry.resolve(context, module) }
    }

    /**
     * 建立會記錄呼叫的測試解析器。
     *
     * @param id 解析器的註冊識別碼。
     * @param ruleModuleId 所屬規則模組的識別碼。
     * @param priority 解析器的執行優先序。
     * @param calls 依呼叫順序收集解析器識別碼的清單。
     * @param result 每次呼叫要回傳的指示；null 表示交由後續解析器判定。
     * @return 測試解析器。
     */
    private fun recordingResolver(
        id: String,
        ruleModuleId: String,
        priority: Int,
        calls: MutableList<String>,
        result: WinRoundDirective?,
    ): WinRoundContinuationResolver = object : WinRoundContinuationResolver {
        override val id: String = id
        override val ruleModuleId: String = ruleModuleId
        override val priority: Int = priority

        override fun resolve(context: WinRoundContinuationContext, ruleModule: MahjongRuleModule<*>): WinRoundDirective? {
            calls += id
            return result
        }
    }
}
