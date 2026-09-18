package com.doublemoon1119.mahjongcraft.platform.minecraft.rule.riichi

import com.doublemoon1119.mahjongcraft.logic.module.BuiltInRuleModuleIds
import com.doublemoon1119.mahjongcraft.logic.rules.riichi.RiichiDynamicState
import com.doublemoon1119.mahjongcraft.logic.rules.riichi.RiichiPlayerState
import com.doublemoon1119.mahjongcraft.logic.table.TableState
import com.doublemoon1119.mahjongcraft.platform.minecraft.stick.MahjongScoringStickDimensions
import com.doublemoon1119.mahjongcraft.platform.minecraft.table.BuiltInTablePropKinds
import com.doublemoon1119.mahjongcraft.platform.minecraft.table.TablePropDescriber
import com.doublemoon1119.mahjongcraft.platform.minecraft.table.TablePropDescriberRegistry
import com.doublemoon1119.mahjongcraft.platform.minecraft.table.TablePropPlacement
import com.doublemoon1119.mahjongcraft.platform.minecraft.table.TableSeatAnchor
import com.doublemoon1119.mahjongcraft.platform.minecraft.table.TableSeatOffset
import com.doublemoon1119.mahjongcraft.platform.minecraft.tile.MahjongTileDimensions
import com.doublemoon1119.mahjongcraft.platform.minecraft.tile.MahjongTileTableLayout

/**
 * 日麻桌上的點棒。
 *
 * - 立直中的玩家：在自己牌河靠桌子中心那一側、緊貼牌河放一根千點棒。
 * - 莊家角落：先疊本場棒（百點，支數等於連莊數），再接著疊流局延續下來、尚未被收下的供託（千點）。
 *
 * 場上的供託總數包含這局宣告中的立直棒，所以延續供託的支數是總數扣掉目前立直中的人數。
 */
object RiichiTableProps : TablePropDescriber {
    /** 本場棒的面額。 */
    private const val COMBO_STICK_VARIANT = "100"

    /** 立直棒與供託的面額。 */
    private const val STICK_POT_VARIANT = "1000"

    /** 立直棒與牌河之間留下的縫隙，讓兩者看得出是分開的（單位：方塊）。 */
    internal const val RIICHI_STICK_CLEARANCE_GAP: Double = 0.05

    override fun describe(tableState: TableState): List<TablePropPlacement> {
        val riichiSeatIndices = tableState.players.withIndex()
            .filter { (_, player) -> (player.playerRuleState as? RiichiPlayerState)?.isRiichi == true }
            .map { (seatIndex, _) -> seatIndex }
        val stickPotCount = (tableState.dynamicRuleState as? RiichiDynamicState)?.riichiStickCount ?: 0
        val pooledStickCount = (stickPotCount - riichiSeatIndices.size).coerceAtLeast(0)
        val dealerSeatIndex = tableState.dealerIndex
        return riichiSeatIndices.map(::riichiStick) +
            List(tableState.comboCount) { stackIndex -> cornerStick(dealerSeatIndex, stackIndex, COMBO_STICK_VARIANT) } +
            List(pooledStickCount) { poolIndex ->
                cornerStick(dealerSeatIndex, tableState.comboCount + poolIndex, STICK_POT_VARIANT)
            }
    }

    /** 立直棒：從牌河內緣再往桌子中心退半根棒子厚度與一道縫隙，沿排列方向置中。 */
    private fun riichiStick(seatIndex: Int) = TablePropPlacement(
        kind = BuiltInTablePropKinds.SCORING_STICK,
        variant = STICK_POT_VARIANT,
        seatIndex = seatIndex,
        anchor = TableSeatAnchor.DISCARD_INNER_EDGE,
        offset = TableSeatOffset(z = -(MahjongScoringStickDimensions.STICK_DEPTH / 2.0 + RIICHI_STICK_CLEARANCE_GAP)),
    )

    /**
     * 角落那一疊的第 [stackIndex] 根：從副露角落往手牌方向排，每排最多
     * [MahjongTileTableLayout.STICKS_PER_ROW] 根，排滿往上疊一層；棒子橫放，長邊朝桌子中心。
     */
    private fun cornerStick(seatIndex: Int, stackIndex: Int, variant: String): TablePropPlacement {
        val column = stackIndex % MahjongTileTableLayout.STICKS_PER_ROW
        val layer = stackIndex / MahjongTileTableLayout.STICKS_PER_ROW
        val stepAlong = MahjongScoringStickDimensions.STICK_DEPTH + MahjongTileDimensions.TILE_SMALL_PADDING
        val layerHeight = MahjongScoringStickDimensions.STICK_HEIGHT + MahjongTileDimensions.TILE_SMALL_PADDING
        return TablePropPlacement(
            kind = BuiltInTablePropKinds.SCORING_STICK,
            variant = variant,
            seatIndex = seatIndex,
            anchor = TableSeatAnchor.MELD_CORNER,
            offset = TableSeatOffset(
                x = -(column + 0.5) * stepAlong,
                y = layer * layerHeight,
                z = -MahjongScoringStickDimensions.STICK_WIDTH / 2.0,
            ),
            yawOffset = MahjongTileTableLayout.SIDEWAYS_YAW_OFFSET,
        )
    }
}

/** 登記內建日麻的桌面物件描述。 */
fun TablePropDescriberRegistry.registerBuiltInRiichiTableProps() {
    register(BuiltInRuleModuleIds.RIICHI, RiichiTableProps)
}
