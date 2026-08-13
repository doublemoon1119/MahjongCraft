package com.doublemoon1119.mahjongcraft.platform.fabric.block

import net.minecraft.util.math.Direction
import net.minecraft.util.shape.VoxelShape
import net.minecraft.util.shape.VoxelShapes

/**
 * 麻將桌固定桌型及其 server 權威碰撞 profile。
 *
 * 桌型只決定幾何與碰撞，不保存木材或桌布等材質語意。所有桌型共用 `Y + 1.0` 的邏輯桌面高度，
 * 並以 [collisionShape] 回傳目前 part 內的局部形狀。
 */
enum class MahjongTableDesign {
    /** 四個外角具有直立桌腳，桌底中央保持可通行。 */
    FOUR_LEG,

    /** controller 具有中央柱與底座，其他位置的桌底保持可通行。 */
    PEDESTAL,
    ;

    /** 取得指定 part 與朝向的碰撞形狀。 */
    fun collisionShape(part: MahjongTablePart, facing: Direction): VoxelShape {
        require(facing.axis.isHorizontal) { "Mahjong table facing must be horizontal" }
        val northFacingShape = when {
            part.localY == 1 -> topCollisionShape(part)
            this == FOUR_LEG -> fourLegBottomCollisionShape(part)
            else -> pedestalBottomCollisionShape(part)
        }
        return rotateFromNorth(northFacingShape, facing)
    }

    /**
     * 取得可選取範圍：只涵蓋實際渲染出來的幾何。
     *
     * 上層中央沒有靜態模型，因此不提供選取框，否則玩家會在桌面上方看到一個內部空無一物的整格線框。
     * 該位置的互動與拆除改由正下方的 controller 承接——桌面是底層方塊最上緣，射線穿過空的上層中央
     * 後會直接命中桌面。空間本身仍由不可替換的 part 佔用，其他方塊放不進來。
     */
    fun outlineShape(part: MahjongTablePart, facing: Direction): VoxelShape = collisionShape(part, facing)

    /** 取得上層桌緣碰撞；中央保留空間沒有碰撞。 */
    private fun topCollisionShape(part: MahjongTablePart): VoxelShape {
        if (part == MahjongTablePart.TOP_CENTER) return VoxelShapes.empty()
        val boxes = buildList {
            if (part.localX == -1) add(cuboid(0.0, 0.0, 0.0, RIM, RIM, 16.0))
            if (part.localX == 1) add(cuboid(16.0 - RIM, 0.0, 0.0, 16.0, RIM, 16.0))
            if (part.localZ == -1) add(cuboid(0.0, 0.0, 0.0, 16.0, RIM, RIM))
            if (part.localZ == 1) add(cuboid(0.0, 0.0, 16.0 - RIM, 16.0, RIM, 16.0))
        }
        return boxes.fold(VoxelShapes.empty(), VoxelShapes::union)
    }

    /** 取得四腳桌底層桌面與外角桌腳碰撞。 */
    private fun fourLegBottomCollisionShape(part: MahjongTablePart): VoxelShape {
        val tabletop = cuboid(0.0, 14.0, 0.0, 16.0, 16.0, 16.0)
        if (part.localX == 0 || part.localZ == 0) return tabletop
        val minX = if (part.localX < 0) 1.0 else 12.0
        val minZ = if (part.localZ < 0) 1.0 else 12.0
        val leg = cuboid(minX, 0.0, minZ, minX + 3.0, 14.0, minZ + 3.0)
        return VoxelShapes.union(tabletop, leg)
    }

    /** 取得中央柱桌底層桌面、controller 底座與中央柱碰撞。 */
    private fun pedestalBottomCollisionShape(part: MahjongTablePart): VoxelShape {
        val tabletop = cuboid(0.0, 12.0, 0.0, 16.0, 16.0, 16.0)
        if (part != MahjongTablePart.BOTTOM_CENTER) return tabletop
        val base = cuboid(2.0, 0.0, 2.0, 14.0, 2.0, 14.0)
        val column = cuboid(5.0, 2.0, 5.0, 11.0, 12.0, 11.0)
        return VoxelShapes.union(tabletop, base, column)
    }

    /** 將朝北局部形狀繞方塊中心旋轉至指定世界朝向。 */
    private fun rotateFromNorth(shape: VoxelShape, facing: Direction): VoxelShape {
        if (facing == Direction.NORTH || shape.isEmpty) return shape
        val rotatedBoxes = shape.boundingBoxes.map { box ->
            when (facing) {
                Direction.EAST -> cuboid(
                    (1.0 - box.maxZ) * 16.0,
                    box.minY * 16.0,
                    box.minX * 16.0,
                    (1.0 - box.minZ) * 16.0,
                    box.maxY * 16.0,
                    box.maxX * 16.0,
                )
                Direction.SOUTH -> cuboid(
                    (1.0 - box.maxX) * 16.0,
                    box.minY * 16.0,
                    (1.0 - box.maxZ) * 16.0,
                    (1.0 - box.minX) * 16.0,
                    box.maxY * 16.0,
                    (1.0 - box.minZ) * 16.0,
                )
                Direction.WEST -> cuboid(
                    box.minZ * 16.0,
                    box.minY * 16.0,
                    (1.0 - box.maxX) * 16.0,
                    box.maxZ * 16.0,
                    box.maxY * 16.0,
                    (1.0 - box.minX) * 16.0,
                )
                else -> error("Mahjong table facing must be horizontal")
            }
        }
        return rotatedBoxes.fold(VoxelShapes.empty(), VoxelShapes::union)
    }

    /** 以模型像素為單位建立不觸發 Minecraft block registry 初始化的長方體。 */
    private fun cuboid(
        minX: Double,
        minY: Double,
        minZ: Double,
        maxX: Double,
        maxY: Double,
        maxZ: Double,
    ): VoxelShape = VoxelShapes.cuboid(
        minX / 16.0,
        minY / 16.0,
        minZ / 16.0,
        maxX / 16.0,
        maxY / 16.0,
        maxZ / 16.0,
    )

    /** 共用桌緣寬度與高度，單位為模型像素。 */
    private companion object {
        const val RIM: Double = 1.0
    }
}
