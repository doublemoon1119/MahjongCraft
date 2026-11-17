package com.doublemoon1119.mahjongcraft.logic.rules.riichi.opening

import com.doublemoon1119.mahjongcraft.logic.table.opening.FixedOpeningProcedure
import com.doublemoon1119.mahjongcraft.logic.table.opening.WallOpening

/**
 * 不砌牌牆時四人與三人日麻共用的開局程序：不擲骰，開門位置固定在莊家面前的牌牆、右數兩墩。
 *
 * 不砌牌牆時開門位置只用來切分摸牌序列與王牌，不對應桌面位置；兩墩是擲骰可能的最小值，四人與三人的牌牆都適用。
 */
internal val RIICHI_WITHOUT_WALL_OPENING_PROCEDURE: FixedOpeningProcedure = FixedOpeningProcedure(
    wallOpening = WallOpening(wallSideOffsetFromDealer = 0, stacksFromRight = 2),
)
