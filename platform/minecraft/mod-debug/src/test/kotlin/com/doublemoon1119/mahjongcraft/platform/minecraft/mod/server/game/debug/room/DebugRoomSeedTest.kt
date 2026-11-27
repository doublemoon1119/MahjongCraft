package com.doublemoon1119.mahjongcraft.platform.minecraft.mod.server.game.debug.room

import com.doublemoon1119.mahjongcraft.logic.module.BuiltInRuleModuleIds
import com.doublemoon1119.mahjongcraft.logic.rules.riichi.threeplayer.ThreePlayerRiichiRuleConfig
import com.doublemoon1119.mahjongcraft.logic.rules.riichi.threeplayer.ThreePlayerRiichiRuleModule
import com.doublemoon1119.mahjongcraft.logic.table.GameInitializer
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.uuid.Uuid

/**
 * 驗證指定種子開局時，三人日麻第一局的開門結果固定。
 *
 * - 種子 [CROSS_GAP_SEED] 讓開門整理牌牆時，有牌要從空座位一側的牌牆移到另一側，用來在遊戲中觀察這段移動。
 * - 開門只取決於種子與規則設定，與呼叫者坐哪個座位無關。
 */
class DebugRoomSeedTest {
    /** 以預設規則設定開局時，種子 [CROSS_GAP_SEED] 不論坐哪個座位，第一局都有牌跨過空座位。 */
    @Test
    fun `the cross-gap seed moves tiles across the empty seat`() {
        for (seat in 0..2) {
            assertTrue(crossesTheGap(CROSS_GAP_SEED, seat), "seat $seat")
        }
    }

    /** 對照：多數種子的第一局沒有這種移動，判斷方式不是一律成立。 */
    @Test
    fun `most seeds do not move tiles across the empty seat`() {
        assertFalse(crossesTheGap(NO_CROSSING_SEED, seat = 0))
    }

    /**
     * 以指定種子與座位建立三人日麻第一局，判斷開門時是否有牌在隔著空座位的兩面牌牆之間移動。
     *
     * - 開門前的牌牆位置與開門後的位置不在同一面，而且兩面在桌子上正對著，中間隔著空座位。
     *
     * @param seed 指令輸入的種子字詞。
     * @param seat 呼叫者要坐的座位。
     * @return 有牌跨過空座位時為 true。
     */
    private fun crossesTheGap(seed: String, seat: Int): Boolean {
        val module = ThreePlayerRiichiRuleModule(BuiltInRuleModuleIds.RIICHI_THREE_PLAYER, ThreePlayerRiichiRuleConfig())
        val players = List(3) { index -> Uuid.fromLongs(0L, index.toLong() + 1) }
        val seatOrder = DebugRoomCommand.seatOrder(players, players.first(), seat)
        val result = GameInitializer.initialize(
            id = Uuid.fromLongs(1L, 1L),
            playerIds = players,
            module = module,
            randomness = DebugRoomCommand.matchSeed(seed).roundRandomness(0),
            seatOrder = seatOrder,
        )
        val dealerSeat = seatOrder.indexOf(result.tableState.dealerPlayerId)
        val arrangement = module.createWallLayout(players.size).sideArrangement(dealerSeat)
        val structure = checkNotNull(result.wallStructure)
        val finalLayout = checkNotNull(result.initialPhysicalWallLayout)
        return structure.any { (tileId, from) ->
            val to = finalLayout.placements[tileId]?.position ?: return@any false
            from.side != to.side && (arrangement.quarterTurnsOf(from.side) - arrangement.quarterTurnsOf(to.side)).mod(QUARTER_TURNS) == HALF_TURN
        }
    }

    private companion object {
        /** 第一局有牌跨過空座位的種子。 */
        const val CROSS_GAP_SEED = "cross-gap-8"

        /** 第一局沒有牌跨過空座位的種子。 */
        const val NO_CROSSING_SEED = "cross-gap"

        /** 桌子一圈的四分之一圈數。 */
        const val QUARTER_TURNS = 4

        /** 正對面相差的四分之一圈數。 */
        const val HALF_TURN = 2
    }
}
