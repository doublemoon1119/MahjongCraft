package com.doublemoon1119.mahjongcraft.example.fabric.furniture.table

import net.minecraft.util.math.BlockPos

/** 3×3 單層範例桌子的格子位置換算。 */
internal object ExampleFlatTableStructure {
    /**
     * 列出整張桌子的 9 格。
     *
     * @param centerPos 中央控制方塊的位置。
     * @return 每一格與其世界位置。
     */
    fun placements(centerPos: BlockPos): Map<ExampleFlatTablePart, BlockPos> = ExampleFlatTablePart.entries.associateWith { part -> centerPos.add(part.offsetX, 0, part.offsetZ) }

    /**
     * 由任一格換算中央控制方塊的位置。
     *
     * @param partPos 這一格的位置。
     * @param part 這一格在桌子中的位置。
     * @return 中央控制方塊的位置。
     */
    fun centerPos(partPos: BlockPos, part: ExampleFlatTablePart): BlockPos = partPos.add(-part.offsetX, 0, -part.offsetZ)
}
