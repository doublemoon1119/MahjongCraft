package com.doublemoon1119.mahjongcraft.platform.fabric.server.game.debug.presentation

import com.doublemoon1119.mahjongcraft.flow.common.game.model.ContinuingWinSettlementDetail
import com.doublemoon1119.mahjongcraft.flow.common.game.model.WinRoundContinuationContext
import com.doublemoon1119.mahjongcraft.flow.common.game.model.WinRoundDirective
import com.doublemoon1119.mahjongcraft.flow.common.game.model.applyTo
import com.doublemoon1119.mahjongcraft.logic.module.MahjongModuleRegistryImpl
import com.doublemoon1119.mahjongcraft.logic.rules.riichi.RiichiRuleConfig
import com.doublemoon1119.mahjongcraft.logic.table.TableState
import com.doublemoon1119.mahjongcraft.testing.flow.bundled.registerBundledRuleModules
import com.doublemoon1119.mahjongcraft.testing.logic.table.FakeMahjongPlayerFactory
import com.doublemoon1119.mahjongcraft.testing.logic.table.FakeTableStateFactory
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.uuid.Uuid

/**
 * [DebugWinRoundContinuationResolver] 的判定測試。
 *
 * 驗證贏家完成標記、下一位玩家與結束本局的條件。
 */
class DebugWinRoundContinuationResolverTest {
    /** 提供測試所需的內建規則模組。 */
    private val moduleRegistry = MahjongModuleRegistryImpl().apply { registerBundledRuleModules() }

    /** 用於續局判定的日麻規則模組。 */
    private val ruleModule = moduleRegistry.getModule(RiichiRuleConfig())

    /** 各桌的測試用續局模式設定。 */
    private val state = DebugWinRoundContinuationState()

    /** 受測的續局判定器。 */
    private val resolver = DebugWinRoundContinuationResolver(ruleModuleId = ruleModule.id, state = state)

    /** 預設關閉時不提供續局判定。 */
    @Test
    fun `an off resolver stays inert`() {
        val table = table(playerCount = 4)

        assertNull(resolver.resolve(tsumoContext(table, winnerIndex = 0), ruleModule))
    }

    /** 自摸後將贏家標記為已完成，回合交給其後第一位仍在局中的玩家。 */
    @Test
    fun `a tsumo continues the round and passes the turn to the next active player`() {
        val table = table(playerCount = 4)
        state.setMode(table.id, DebugWinRoundContinuationMode.FULL)

        val directive = resolver.resolve(tsumoContext(table, winnerIndex = 0), ruleModule)

        val continueRound = assertContinueRound(directive)
        assertEquals(setOf(table.players[0].id), continueRound.newlyFinishedPlayerIds)
        assertEquals(table.players[1].id, continueRound.nextPlayerId)
        assertEquals(ContinuingWinSettlementDetail.WINNER_DETAILS, continueRound.settlementDetail)
    }

    /** 榮和後從放銃者之後選擇下一位玩家。 */
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

    /** 胡牌後仍在局中的玩家少於兩位時結束本局。 */
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
            DebugWinRoundContinuationMode.FULL to ContinuingWinSettlementDetail.WINNER_DETAILS,
            DebugWinRoundContinuationMode.BRIEF to ContinuingWinSettlementDetail.SCORE_CHANGES_ONLY,
        ).forEach { (debugMode, expected) ->
            state.setMode(table.id, debugMode)

            val continueRound = assertContinueRound(resolver.resolve(tsumoContext(table, winnerIndex = 0), ruleModule))

            assertEquals(expected, continueRound.settlementDetail, "Debug mode $debugMode must map to $expected.")
        }
    }

    /** 續局判定套用至桌況後，完成標記與目前玩家符合預期。 */
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

    /** 清除該桌設定後不再提供續局判定。 */
    @Test
    fun `clearing a table returns it to inert`() {
        val table = table(playerCount = 4)
        state.setMode(table.id, DebugWinRoundContinuationMode.FULL)
        state.clear(table.id)

        assertEquals(emptySet(), state.activeTableIds())
        assertNull(resolver.resolve(tsumoContext(table, winnerIndex = 0), ruleModule))
    }

    /** 設為 OFF 時移除該桌設定。 */
    @Test
    fun `setting a table to off removes its entry`() {
        val table = table(playerCount = 4)
        state.setMode(table.id, DebugWinRoundContinuationMode.FULL)
        state.setMode(table.id, DebugWinRoundContinuationMode.OFF)

        assertEquals(emptySet(), state.activeTableIds())
    }

    /**
     * 建立供 resolver 測試使用的桌況。
     *
     * @param playerCount 桌上的玩家數量。
     * @return 使用內建日麻規則設定的桌況。
     */
    private fun table(playerCount: Int): TableState = FakeTableStateFactory.create(
        id = Uuid.random(),
        players = List(playerCount) { FakeMahjongPlayerFactory.create() },
        config = RiichiRuleConfig(),
    )

    /**
     * 建立單一玩家自摸的 resolver 輸入。
     *
     * @param table 自摸前後的桌況。
     * @param winnerIndex 贏家在玩家清單中的索引。
     * @return 對應的胡牌後流程判定情境。
     */
    private fun tsumoContext(table: TableState, winnerIndex: Int): WinRoundContinuationContext = WinRoundContinuationContext(
        previousTableState = table,
        settledTableState = table,
        winnerPlayerIds = setOf(table.players[winnerIndex].id),
        ronDiscarderId = null,
        winningTileId = Uuid.random(),
    )

    /**
     * 斷言結果是續局 directive，並回傳其內容供後續欄位檢查。
     *
     * @param directive resolver 產生的判定結果。
     * @return 續局 directive；其他結果會使測試失敗。
     */
    private fun assertContinueRound(directive: WinRoundDirective?): WinRoundDirective.ContinueRound {
        check(directive is WinRoundDirective.ContinueRound) { "Expected ContinueRound but was $directive" }
        return directive
    }
}
