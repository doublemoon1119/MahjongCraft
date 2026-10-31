package com.doublemoon1119.mahjongcraft.example.fabric.furniture.table

import net.minecraft.util.shape.VoxelShape
import net.minecraft.util.shape.VoxelShapes

/**
 * 3×3 單層範例桌子的外觀款式；兩種款式的結構相同，只有每一格的形狀不同，與方塊模型一致。
 *
 * 轉角的形狀以西北角為基準描述，其他轉角依所在位置鏡像；桌面頂端在方塊頂端，擺牌層因此是上方一整層空氣。
 */
enum class ExampleFlatTableDesign {
    /** 方形桌面、四個轉角各有一支桌腳的矮桌。 */
    LOW {
        override fun northWestCornerBoxes(): List<Box> = listOf(TABLETOP, Box(1, 0, 1, 4, 13, 4))

        override fun centerBoxes(): List<Box> = listOf(TABLETOP)
    },

    /** 轉角削成圓弧、中央有底座的圓桌；底座在桌面以下，不進入擺牌層。 */
    ROUND {
        override fun northWestCornerBoxes(): List<Box> = listOf(
            Box(8, 13, 3, 16, 16, 16),
            Box(3, 13, 8, 8, 16, 16),
            Box(5, 13, 5, 8, 16, 8),
        )

        override fun centerBoxes(): List<Box> = listOf(TABLETOP, Box(5, 0, 5, 11, 13, 11), Box(3, 0, 3, 13, 2, 13))
    },
    ;

    /** 依格子位置建立的碰撞形狀，建立後重複使用。 */
    private val shapes: Map<ExampleFlatTablePart, VoxelShape> by lazy {
        ExampleFlatTablePart.entries.associateWith { part ->
            val boxes = when {
                part == ExampleFlatTablePart.CENTER -> centerBoxes()
                part.offsetX != 0 && part.offsetZ != 0 -> northWestCornerBoxes().map { it.mirroredFor(part) }
                else -> listOf(TABLETOP)
            }
            boxes.map { it.toShape() }.reduce(VoxelShapes::union)
        }
    }

    /** @return 西北角那一格的形狀方塊。 */
    protected abstract fun northWestCornerBoxes(): List<Box>

    /** @return 中央那一格的形狀方塊。 */
    protected abstract fun centerBoxes(): List<Box>

    /**
     * 取得一格的碰撞與選取形狀。
     *
     * @param part 格子位置。
     * @return 該格的形狀。
     */
    fun shape(part: ExampleFlatTablePart): VoxelShape = shapes.getValue(part)

    /**
     * 以方塊內 0～16 座標描述的長方體。
     *
     * @property x1 最小 x。
     * @property y1 最小 y。
     * @property z1 最小 z。
     * @property x2 最大 x。
     * @property y2 最大 y。
     * @property z2 最大 z。
     */
    data class Box(
        val x1: Double,
        val y1: Double,
        val z1: Double,
        val x2: Double,
        val y2: Double,
        val z2: Double,
    ) {
        /**
         * 以整數座標建立長方體。
         *
         * @param x1 最小 x。
         * @param y1 最小 y。
         * @param z1 最小 z。
         * @param x2 最大 x。
         * @param y2 最大 y。
         * @param z2 最大 z。
         */
        constructor(x1: Int, y1: Int, z1: Int, x2: Int, y2: Int, z2: Int) :
            this(x1.toDouble(), y1.toDouble(), z1.toDouble(), x2.toDouble(), y2.toDouble(), z2.toDouble())

        /** @return 對應的碰撞形狀。 */
        fun toShape(): VoxelShape = VoxelShapes.cuboid(x1 / BLOCK_SIZE, y1 / BLOCK_SIZE, z1 / BLOCK_SIZE, x2 / BLOCK_SIZE, y2 / BLOCK_SIZE, z2 / BLOCK_SIZE)

        /**
         * 把以西北角描述的長方體鏡像到指定轉角。
         *
         * @param part 目標轉角。
         * @return 鏡像後的長方體。
         */
        fun mirroredFor(part: ExampleFlatTablePart): Box {
            val mirrorX = part.offsetX > 0
            val mirrorZ = part.offsetZ > 0
            return Box(
                x1 = if (mirrorX) BLOCK_SIZE - x2 else x1,
                y1 = y1,
                z1 = if (mirrorZ) BLOCK_SIZE - z2 else z1,
                x2 = if (mirrorX) BLOCK_SIZE - x1 else x2,
                y2 = y2,
                z2 = if (mirrorZ) BLOCK_SIZE - z1 else z2,
            )
        }
    }

    /** 共用的形狀尺寸。 */
    private companion object {
        /** 方塊內座標的邊長。 */
        const val BLOCK_SIZE: Double = 16.0

        /** 一整格的桌面板。 */
        val TABLETOP = Box(0, 13, 0, 16, 16, 16)
    }
}
