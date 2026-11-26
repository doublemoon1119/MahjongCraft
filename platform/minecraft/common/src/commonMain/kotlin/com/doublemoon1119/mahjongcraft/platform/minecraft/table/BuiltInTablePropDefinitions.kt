package com.doublemoon1119.mahjongcraft.platform.minecraft.table

import com.doublemoon1119.mahjongcraft.platform.minecraft.stick.MahjongScoringStickDenomination
import com.doublemoon1119.mahjongcraft.platform.minecraft.stick.MahjongScoringStickDimensions
import com.doublemoon1119.mahjongcraft.platform.minecraft.tile.MahjongTileDimensions

/** 各版本 adapter 共用的內建物件內容與實際模型尺寸，不包含原生實體工廠。 */
object BuiltInTablePropDefinitions {
    /** 點棒在未旋轉時的寬、高與深度，僅接受已知面額。 */
    val SCORING_STICK = TablePropKindDefinition(
        id = BuiltInTablePropKinds.SCORING_STICK,
        dimensions = TablePropDimensions(
            width = MahjongScoringStickDimensions.STICK_WIDTH,
            height = MahjongScoringStickDimensions.STICK_HEIGHT,
            depth = MahjongScoringStickDimensions.STICK_DEPTH,
        ),
        acceptsContent = { content -> content is TablePropContent.CustomVariant && MahjongScoringStickDenomination.entries.any { it.points.toString() == content.value } },
    )

    /** 公開牌平放時的寬、厚度及牌長，只接受型別化的公開牌面。 */
    val FACE_UP_TILE = TablePropKindDefinition(
        id = BuiltInTablePropKinds.FACE_UP_TILE,
        dimensions = TablePropDimensions(
            width = MahjongTileDimensions.TILE_WIDTH,
            height = MahjongTileDimensions.TILE_DEPTH,
            depth = MahjongTileDimensions.TILE_HEIGHT,
        ),
        acceptsContent = { it is TablePropContent.FaceUpTile },
    )
}
