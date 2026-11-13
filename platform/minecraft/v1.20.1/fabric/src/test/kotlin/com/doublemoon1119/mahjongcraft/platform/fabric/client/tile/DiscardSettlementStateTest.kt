package com.doublemoon1119.mahjongcraft.platform.fabric.client.tile

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.uuid.Uuid

/** 驗證捨牌要等真的落到牌河才算落定。 */
class DiscardSettlementStateTest {
    private val table = Uuid.random()
    private val earlier = Uuid.random()
    private val discarded = Uuid.random()
    private val state = DiscardSettlementState<String>()
    private val positions = mutableMapOf(earlier to "river", discarded to "hand")
    private val moving = mutableSetOf<Uuid>()

    /**
     * 以目前的位置與移動狀態更新。
     *
     * @param discards 目前在牌河紀錄中的牌。
     */
    private fun update(vararg discards: Uuid) = state.update(table, discards.toSet(), positions::get) { it in moving }

    /** 第一次看到這張桌子時，已經在牌河的牌都算落定，例如剛進入或重新連線。 */
    @Test
    fun `discards already in the river on first sight are settled`() {
        update(earlier)

        assertTrue(state.isSettled(table, earlier))
    }

    /** 還沒判斷過的牌不算落定，例如桌況剛更新、下一次更新還沒到。 */
    @Test
    fun `a discard not yet seen by an update is not settled`() {
        update(earlier)

        assertFalse(state.isSettled(table, discarded))
    }

    /** 剛打出、還停在手牌位置的牌不算落定。 */
    @Test
    fun `a new discard still in the hand is not settled`() {
        update(earlier)
        update(earlier, discarded)
        update(earlier, discarded)

        assertFalse(state.isSettled(table, discarded))
        assertTrue(state.isSettled(table, earlier))
    }

    /** 開始移動後仍不算落定，停下來之後才算。 */
    @Test
    fun `a new discard settles once its motion ends`() {
        update(earlier)
        update(earlier, discarded)
        positions[discarded] = "river"
        moving += discarded
        update(earlier, discarded)
        assertFalse(state.isSettled(table, discarded))

        moving -= discarded
        update(earlier, discarded)
        assertTrue(state.isSettled(table, discarded))
    }

    /** 第一次看到時已經在移動，位置不會再變；停下來後就算落定。 */
    @Test
    fun `a discard first seen while moving settles when it stops`() {
        update(earlier)
        positions[discarded] = "river"
        moving += discarded
        update(earlier, discarded)
        assertFalse(state.isSettled(table, discarded))

        moving -= discarded
        update(earlier, discarded)
        assertTrue(state.isSettled(table, discarded))
    }

    /** 沒有動畫、直接移到牌河的牌，位置改變後就算落定。 */
    @Test
    fun `a discard moved without motion settles once its position changes`() {
        update(earlier)
        update(earlier, discarded)
        positions[discarded] = "river"
        update(earlier, discarded)

        assertTrue(state.isSettled(table, discarded))
    }

    /** 牌實體還不在世界中時保持未落定，出現後才開始判斷。 */
    @Test
    fun `a discard without a loaded tile stays unsettled`() {
        positions.remove(discarded)
        update(earlier)
        update(earlier, discarded)

        assertFalse(state.isSettled(table, discarded))
    }

    /** 沒有追蹤過或已經不再有對局的桌子，牌都不算落定。 */
    @Test
    fun `untracked tables report nothing as settled`() {
        assertFalse(state.isSettled(table, earlier))

        update(earlier)
        state.retainTables(emptySet())

        assertFalse(state.isSettled(table, earlier))
    }
}
