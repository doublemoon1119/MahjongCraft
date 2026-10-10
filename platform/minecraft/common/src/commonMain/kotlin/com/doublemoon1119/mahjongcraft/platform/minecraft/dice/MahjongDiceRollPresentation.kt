package com.doublemoon1119.mahjongcraft.platform.minecraft.dice

import com.doublemoon1119.mahjongcraft.platform.minecraft.table.TableLocation
import com.doublemoon1119.mahjongcraft.platform.minecraft.tile.MahjongInitialDealPresentation
import kotlin.uuid.Uuid

/**
 * 正式擲骰呈現所需的版本無關資料。
 *
 * @property tableId 所屬麻將桌的穩定 UUID。
 * @property tableLocation 麻將桌 controller 的位置。
 * @property tableFacing 麻將桌 controller 的世界水平朝向。
 * @property throwSide 擲骰者相對麻將桌的局部側面。
 * @property rollSequence 同桌遞增的投擲序號，用來輪替安全 layout。
 * @property dice 由伺服器權威流程決定的骰子點數與動畫 seed；目前支援兩顆或三顆。
 * @property extraLeadDelayTicks 這次投擲動畫在每顆骰子自己的動畫佇列最前面該多等待的 tick 數（等
 * 牌牆掉落動畫播完才輪到擲骰），折算進每顆骰子自己的佇列，理由同 [MahjongInitialDealPresentation.extraLeadDelayTicks]。
 */
data class MahjongDiceRollPresentation(
    val tableId: Uuid,
    val tableLocation: TableLocation,
    val tableFacing: MahjongTableFacing,
    val throwSide: MahjongTableSide,
    val rollSequence: Long,
    val dice: List<MahjongDicePresentation>,
    val extraLeadDelayTicks: Int = 0,
) {
    init {
        require(rollSequence >= 0) { "Roll sequence must not be negative" }
        require(dice.size in SUPPORTED_DICE_COUNTS) { "Dice presentation must contain two or three dice" }
    }

    /** 目前已提供安全桌面 layout 的骰子數量。 */
    private companion object {
        val SUPPORTED_DICE_COUNTS: IntRange = 2..3
    }
}
