package com.doublemoon1119.mahjongcraft.platform.fabric.block

import net.minecraft.util.math.BlockPos
import net.minecraft.util.math.Direction

/** 3×3×2 麻將桌的純座標換算；不讀取或修改 Minecraft 世界。 */
object MahjongTableStructure {
    /** 麻將桌固定包含的 part 數量。 */
    const val PART_COUNT: Int = 18

    /**
     * 取得指定朝向下所有 part 的世界座標。
     *
     * @param controllerPos 底層中央 controller 的世界座標。
     * @param facing 麻將桌朝向，必須為水平方向。
     * @return 每個 part 對應的世界座標。
     */
    fun placements(controllerPos: BlockPos, facing: Direction): Map<MahjongTablePart, BlockPos> = MahjongTablePart.entries.associateWith { part -> position(controllerPos, part, facing) }

    /**
     * 將指定 part 的相對座標旋轉至 [facing] 並轉成世界座標。
     *
     * @param controllerPos 底層中央 controller 的世界座標。
     * @param part 結構中的 part。
     * @param facing 麻將桌朝向，必須為水平方向。
     * @return 指定 part 的世界座標。
     * @throws IllegalArgumentException 當 [facing] 不是水平方向時。
     */
    fun position(controllerPos: BlockPos, part: MahjongTablePart, facing: Direction): BlockPos {
        require(facing.axis.isHorizontal) { "Mahjong table facing must be horizontal" }
        val (x, z) = rotate(part.localX, part.localZ, facing)
        return controllerPos.add(x, part.localY, z)
    }

    /**
     * 由任意 part 的世界座標反查 controller 世界座標。
     *
     * @param partPos 任意 part 的世界座標。
     * @param part 該座標對應的 part。
     * @param facing 麻將桌朝向，必須為水平方向。
     * @return 底層中央 controller 的世界座標。
     * @throws IllegalArgumentException 當 [facing] 不是水平方向時。
     */
    fun controllerPosition(partPos: BlockPos, part: MahjongTablePart, facing: Direction): BlockPos {
        require(facing.axis.isHorizontal) { "Mahjong table facing must be horizontal" }
        val (x, z) = rotate(part.localX, part.localZ, facing)
        return partPos.add(-x, -part.localY, -z)
    }

    /**
     * 依水平朝向旋轉朝北時的局部 X/Z 位移。
     *
     * @param x 朝北時的局部 X 位移。
     * @param z 朝北時的局部 Z 位移。
     * @param facing 目標水平朝向。
     * @return 旋轉後的 X/Z 位移。
     */
    private fun rotate(x: Int, z: Int, facing: Direction): Pair<Int, Int> = when (facing) {
        Direction.NORTH -> x to z
        Direction.EAST -> -z to x
        Direction.SOUTH -> -x to -z
        Direction.WEST -> z to -x
        else -> error("Mahjong table facing must be horizontal")
    }
}
