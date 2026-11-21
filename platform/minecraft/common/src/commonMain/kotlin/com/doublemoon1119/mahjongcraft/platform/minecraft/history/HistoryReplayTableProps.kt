package com.doublemoon1119.mahjongcraft.platform.minecraft.history

import com.doublemoon1119.mahjongcraft.platform.minecraft.api.table.MahjongTableFacing
import com.doublemoon1119.mahjongcraft.platform.minecraft.table.MahjongTableSeatLayout
import com.doublemoon1119.mahjongcraft.platform.minecraft.table.TableLocation
import com.doublemoon1119.mahjongcraft.platform.minecraft.table.TablePropKindDefinition
import com.doublemoon1119.mahjongcraft.platform.minecraft.table.TablePropLayoutResult
import com.doublemoon1119.mahjongcraft.platform.minecraft.table.TablePropPlacement
import com.doublemoon1119.mahjongcraft.platform.minecraft.table.TablePropPresentation
import com.doublemoon1119.mahjongcraft.platform.minecraft.table.TablePropTarget
import com.doublemoon1119.mahjongcraft.platform.minecraft.tile.ArrangedTile
import com.doublemoon1119.mahjongcraft.platform.minecraft.tile.TableTileArrangement
import kotlin.uuid.Uuid

/** 牌譜回放中桌上物件的擺放，與對局中使用同一套位置計算。 */
object HistoryReplayTableProps {
    /** 回放牌桌所在的維度；回放場景不在任何世界中，只用來建立桌子位置。 */
    private const val REPLAY_DIMENSION_ID = "minecraft:overworld"

    /**
     * 算出桌上物件在回放牌桌上的位置；回放牌桌的基準方塊在原點、朝北，與回放場景擺牌相同。
     *
     * 客戶端不認得的種類不畫，也不佔位置；中央物件避開擺在桌子中央的牌，與對局中相同。
     *
     * @param placements 牌譜記下的桌上物件。
     * @param ruleModuleId 該局規則。
     * @param playerCount 玩家人數。
     * @param arranged 回放擺好的牌桌。
     * @param definitions 客戶端認得的物件種類。
     * @return 各物件的位置；擺不下時為空。
     */
    fun targets(
        placements: List<TablePropPlacement>,
        ruleModuleId: String,
        playerCount: Int,
        arranged: Map<Int, ArrangedTile>,
        definitions: Map<String, TablePropKindDefinition>,
    ): List<TablePropTarget> {
        val known = placements.filter { it.kind in definitions }
        if (known.isEmpty()) return emptyList()
        val presentation = TablePropPresentation(
            tableId = Uuid.NIL,
            ruleModuleId = ruleModuleId,
            tableLocation = TableLocation(dimensionId = REPLAY_DIMENSION_ID, x = 0, y = 0, z = 0),
            tableFacing = MahjongTableFacing.NORTH,
            seatLayout = MahjongTableSeatLayout.forPlayerCount(playerCount),
            placements = known,
            centerTilePlacements = arranged.values.filter { it.row.group == TableTileArrangement.CENTER_ROW_GROUP }.map { it.placement },
        )
        return (presentation.targets(definitions) as? TablePropLayoutResult.Success)?.targets.orEmpty()
    }
}
