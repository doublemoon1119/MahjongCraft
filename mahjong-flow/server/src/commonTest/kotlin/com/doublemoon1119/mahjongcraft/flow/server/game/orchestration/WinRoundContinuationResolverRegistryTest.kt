package com.doublemoon1119.mahjongcraft.flow.server.game.orchestration

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
    private val module = RiichiRuleModule("mahjongcraft:riichi", RiichiRuleConfig())
    private val winner = FakeMahjongPlayerFactory.create()
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
            presentationMode = com.doublemoon1119.mahjongcraft.flow.common.game.model.ContinuingWinPresentationMode.NONE,
        )
        val registry = WinRoundContinuationResolverRegistry().apply {
            register(recordingResolver("test:z", module.id, 20, calls, null))
            register(recordingResolver("test:foreign", "test:foreign_rule", 0, calls, expectedDirective))
            register(recordingResolver("test:b", module.id, 10, calls, expectedDirective))
            register(recordingResolver("test:a", module.id, 10, calls, null))
            freeze()
        }

        val directive = registry.resolve(context, module)

        // test:a（優先序 10，字典序早於 test:b）先執行但回傳 null，falls through 到 test:b 才成立；
        // test:foreign 屬於別的規則模組，不該被呼叫；test:z 優先序較低，成立後不應再被呼叫。
        assertEquals(listOf("test:a", "test:b"), calls)
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

    /** 驗證重複 ID 登記會被拒絕。 */
    @Test
    fun `duplicate id registration is rejected`() {
        val registry = WinRoundContinuationResolverRegistry()
        registry.register(recordingResolver("test:dup", module.id, 0, mutableListOf(), null))

        assertFailsWith<IllegalArgumentException> {
            registry.register(recordingResolver("test:dup", module.id, 0, mutableListOf(), null))
        }
    }

    /** 未凍結就呼叫 resolve 應拋出例外。 */
    @Test
    fun `resolve before freeze throws`() {
        val registry = WinRoundContinuationResolverRegistry()
        assertFailsWith<IllegalStateException> { registry.resolve(context, module) }
    }

    /** 建立只記錄呼叫、回傳指定結果的測試 resolver。 */
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
