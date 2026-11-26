package com.doublemoon1119.mahjongcraft.logic.rules.riichi

import com.doublemoon1119.mahjongcraft.logic.component.ComponentSet
import com.doublemoon1119.mahjongcraft.logic.rules.riichi.tile.RiichiTileComponents
import com.doublemoon1119.mahjongcraft.logic.table.TableState
import com.doublemoon1119.mahjongcraft.logic.tile.component.BuiltInTileComponents
import com.doublemoon1119.mahjongcraft.logic.tile.component.TileComponentDescription
import com.doublemoon1119.mahjongcraft.logic.tile.component.TileFaceAccess
import com.doublemoon1119.mahjongcraft.logic.tile.component.TilePose

/**
 * 依正式公開進度描述日麻指示牌的讀取權與姿態，四人及三人使用同一入口。
 *
 * @param tableState 本局權威桌況。
 * @return 目前公開牌張的純組件效果，不修改權威牌張。
 */
fun riichiTileComponentDescriptions(tableState: TableState): List<TileComponentDescription> = tableState.components[RiichiTableComponents.WALL_PROGRESS]?.getDoraIndicators(tableState)?.first.orEmpty().map { tile ->
    TileComponentDescription(
        tileId = tile.id,
        components = ComponentSet.EMPTY
            .with(BuiltInTileComponents.FACE_ACCESS, TileFaceAccess.Public)
            .with(BuiltInTileComponents.POSE, TilePose.FACE_UP)
            .with(RiichiTileComponents.DORA_INDICATOR, Unit),
    )
}
