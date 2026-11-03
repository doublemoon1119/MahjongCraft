package com.doublemoon1119.mahjongcraft.example.fabric.furniture.block

import net.minecraft.util.math.Direction

/** 長凳開口、一般放置視角及指定座位朝向的換算。 */
internal object ExampleBenchPlacement {
    /**
     * 一般放置時開口朝向放置者。
     *
     * @param playerFacing 放置視角的水平朝向。
     * @return 朝向放置者的長凳開口。
     */
    fun facing(playerFacing: Direction): Direction = playerFacing.opposite

    /**
     * 讓座位物品放置後的開口朝向指定席位。
     *
     * @param seatYaw 指定乘坐面向。
     * @return 對應的一般放置視角。
     */
    fun playerYaw(seatYaw: Float): Float = seatYaw + 180f
}
