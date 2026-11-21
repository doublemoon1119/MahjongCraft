package com.doublemoon1119.mahjongcraft.example.fabric.furniture.block

import net.minecraft.core.Direction
import net.minecraft.world.phys.AABB

/** 與長凳模型一致的局部碰撞範圍，每格以十六單位表示。 */
internal object ExampleBenchGeometry {
    /**
     * 依朝向及左右連接選出本格家具，鄰格不影響椅面與靠背。
     *
     * @param facing 本格前方。
     * @param leftConnected 左側是否接到另一格長凳。
     * @param rightConnected 右側是否接到另一格長凳。
     * @return 椅面、靠背、椅腳與必要端點的範圍。
     */
    fun boxes(
        facing: Direction,
        leftConnected: Boolean,
        rightConnected: Boolean,
    ): List<AABB> {
        require(facing.axis.isHorizontal) { "Bench facing must be horizontal" }
        val boxes = mutableListOf(
            AABB(
                0.0,
                6.0,
                0.0,
                16.0,
                9.0,
                16.0,
            ),
            AABB(
                0.0,
                9.0,
                13.0,
                16.0,
                20.0,
                16.0,
            ),
        )
        for (x in listOf(1.0, 13.0)) {
            for (z in listOf(1.0, 13.0)) {
                boxes += AABB(
                    x,
                    0.0,
                    z,
                    x + 2.0,
                    6.0,
                    z + 2.0,
                )
            }
        }
        if (!leftConnected) {
            boxes += AABB(
                0.0,
                9.0,
                0.0,
                2.0,
                13.0,
                13.0,
            )
        }
        if (!rightConnected) {
            boxes += AABB(
                14.0,
                9.0,
                0.0,
                16.0,
                13.0,
                13.0,
            )
        }
        return boxes.map { box ->
            var result = box
            repeat((facing.get2DDataValue() + 2) % 4) {
                result = AABB(
                    16.0 - result.maxZ,
                    result.minY,
                    result.minX,
                    16.0 - result.minZ,
                    result.maxY,
                    result.maxX,
                )
            }
            result
        }
    }
}
