package com.doublemoon1119.mahjongcraft.platform.fabric.server.game

import com.doublemoon1119.mahjongcraft.flow.common.di.registerBuiltInRuleModules
import com.doublemoon1119.mahjongcraft.flow.common.game.model.ContinuingWinSettlementMode
import com.doublemoon1119.mahjongcraft.flow.common.game.model.WinRoundContinuationContext
import com.doublemoon1119.mahjongcraft.flow.common.game.model.WinRoundDirective
import com.doublemoon1119.mahjongcraft.flow.common.game.model.applyTo
import com.doublemoon1119.mahjongcraft.logic.module.MahjongModuleRegistryImpl
import com.doublemoon1119.mahjongcraft.logic.rules.riichi.RiichiRuleConfig
import com.doublemoon1119.mahjongcraft.logic.table.TableState
import com.doublemoon1119.mahjongcraft.testing.logic.table.FakeMahjongPlayerFactory
import com.doublemoon1119.mahjongcraft.testing.logic.table.FakeTableStateFactory
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.uuid.Uuid

/**
 * [DebugWinRoundContinuationResolver] 的判定測試。
 *
 * 這是開發用的測試輔助工具，但它決定的是**權威**桌況（誰被標記為已完成、回合交給誰），判斷錯誤會讓
 * 進遊戲驗證中途胡牌時看到的是工具本身的 bug 而不是被測機制的 bug，因此照樣覆蓋。
 */
class DebugWinRoundContinuationResolverTest {
    private val moduleRegistry = MahjongModuleRegistryImpl().apply { registerBuiltInRuleModules() }
    private val ruleModule = moduleRegistry.getModule(RiichiRuleConfig())
    private val state = DebugWinRoundContinuationState()
    private val resolver = DebugWinRoundContinuationResolver(ruleModuleId = ruleModule.id, state = state)

    /** 預設關閉時完全 inert：回傳 `null`，registry 因此落回 EndRound，胡牌後立即結束本局。 */
    @Test
    fun `an off resolver stays inert`() {
        val table = table(playerCount = 4)

        assertNull(resolver.resolve(tsumoContext(table, winnerIndex = 0), ruleModule))
    }

    /** 自摸：贏家標記為已完成，回合交給贏家之後的第一位 active 玩家。 */
    @Test
    fun `a tsumo continues the round and passes the turn to the next active player`() {
        val table = table(playerCount = 4)
        state.setMode(table.id, DebugWinRoundContinuationMode.FULL)

        val directive = resolver.resolve(tsumoContext(table, winnerIndex = 0), ruleModule)

        val continueRound = assertContinueRound(directive)
        assertEquals(setOf(table.players[0].id), continueRound.newlyFinishedPlayerIds)
        assertEquals(table.players[1].id, continueRound.nextPlayerId)
        assertEquals(ContinuingWinSettlementMode.FULL, continueRound.settlementMode)
    }

    /** 榮和：回合接在放銃者之後，而不是贏家之後——維持原本的順位感。 */
    @Test
    fun `a ron passes the turn to the player after the discarder`() {
        val table = table(playerCount = 4)
        state.setMode(table.id, DebugWinRoundContinuationMode.FULL)

        val directive = resolver.resolve(
            WinRoundContinuationContext(
                previousTableState = table,
                settledTableState = table,
                winnerPlayerIds = setOf(table.players[3].id),
                ronDiscarderId = table.players[1].id,
                winningTileId = Uuid.random(),
            ),
            ruleModule,
        )

        val continueRound = assertContinueRound(directive)
        assertEquals(table.players[2].id, continueRound.nextPlayerId)
    }

    /** 已完成的玩家會被跳過，回合交給再下一位仍在局中的玩家。 */
    @Test
    fun `an already finished player is skipped when choosing the next turn`() {
        val base = table(playerCount = 4)
        val table = base.copy(finishedPlayerIds = setOf(base.players[1].id))
        state.setMode(table.id, DebugWinRoundContinuationMode.FULL)

        val directive = resolver.resolve(tsumoContext(table, winnerIndex = 0), ruleModule)

        val continueRound = assertContinueRound(directive)
        assertEquals(table.players[2].id, continueRound.nextPlayerId)
    }

    /** 這次胡牌後只剩一位 active 就沒得繼續打，必須回到既有的結束本局流程。 */
    @Test
    fun `the round ends once fewer than two active players would remain`() {
        val base = table(playerCount = 4)
        val table = base.copy(finishedPlayerIds = setOf(base.players[1].id, base.players[2].id))
        state.setMode(table.id, DebugWinRoundContinuationMode.FULL)

        val directive = resolver.resolve(tsumoContext(table, winnerIndex = 0), ruleModule)

        assertEquals(WinRoundDirective.EndRound, directive)
    }

    /** 指令選定的呈現模式會原封不動傳給 directive。 */
    @Test
    fun `the selected presentation mode is passed through`() {
        val table = table(playerCount = 4)
        listOf(
            DebugWinRoundContinuationMode.FULL to ContinuingWinSettlementMode.FULL,
            DebugWinRoundContinuationMode.BRIEF to ContinuingWinSettlementMode.BRIEF,
        ).forEach { (debugMode, expected) ->
            state.setMode(table.id, debugMode)

            val continueRound = assertContinueRound(resolver.resolve(tsumoContext(table, winnerIndex = 0), ruleModule))

            assertEquals(expected, continueRound.settlementMode, "Debug mode $debugMode must map to $expected.")
        }
    }

    /** 產出的 directive 必須通過 [applyTo] 的所有 invariant——它會被直接套用到權威桌況。 */
    @Test
    fun `the produced directive satisfies the authoritative apply invariants`() {
        val table = table(playerCount = 4)
        state.setMode(table.id, DebugWinRoundContinuationMode.FULL)

        val continueRound = assertContinueRound(resolver.resolve(tsumoContext(table, winnerIndex = 0), ruleModule))
        val applied = continueRound.applyTo(table)

        assertEquals(setOf(table.players[0].id), applied.finishedPlayerIds)
        assertEquals(table.players[1].id, applied.currentPlayer.id)
    }

    /** 設定是以桌為範圍的：對另一桌開啟不得影響這一桌。 */
    @Test
    fun `enabling one table does not affect another`() {
        val enabled = table(playerCount = 4)
        val untouched = table(playerCount = 4)
        state.setMode(enabled.id, DebugWinRoundContinuationMode.FULL)

        assertNull(resolver.resolve(tsumoContext(untouched, winnerIndex = 0), ruleModule))
        assertContinueRound(resolver.resolve(tsumoContext(enabled, winnerIndex = 0), ruleModule))
    }

    /** 清除之後該桌立刻回到 inert，對局結束／桌子被破壞時就是走這條路徑。 */
    @Test
    fun `clearing a table returns it to inert`() {
        val table = table(playerCount = 4)
        state.setMode(table.id, DebugWinRoundContinuationMode.FULL)
        state.clear(table.id)

        assertEquals(emptySet(), state.activeTableIds())
        assertNull(resolver.resolve(tsumoContext(table, winnerIndex = 0), ruleModule))
    }

    /** 設為 OFF 等同移除條目，不會留下殘骸。 */
    @Test
    fun `setting a table to off removes its entry`() {
        val table = table(playerCount = 4)
        state.setMode(table.id, DebugWinRoundContinuationMode.FULL)
        state.setMode(table.id, DebugWinRoundContinuationMode.OFF)

        assertEquals(emptySet(), state.activeTableIds())
    }

    private fun table(playerCount: Int): TableState = FakeTableStateFactory.create(
        id = Uuid.random(),
        players = List(playerCount) { FakeMahjongPlayerFactory.create() },
        config = RiichiRuleConfig(),
    )

    private fun tsumoContext(table: TableState, winnerIndex: Int): WinRoundContinuationContext = WinRoundContinuationContext(
        previousTableState = table,
        settledTableState = table,
        winnerPlayerIds = setOf(table.players[winnerIndex].id),
        ronDiscarderId = null,
        winningTileId = Uuid.random(),
    )

    private fun assertContinueRound(directive: WinRoundDirective?): WinRoundDirective.ContinueRound {
        check(directive is WinRoundDirective.ContinueRound) { "Expected ContinueRound but was $directive" }
        return directive
    }
}
