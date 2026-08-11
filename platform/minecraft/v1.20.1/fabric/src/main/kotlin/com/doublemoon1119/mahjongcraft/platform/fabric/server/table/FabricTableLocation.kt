package com.doublemoon1119.mahjongcraft.platform.fabric.server.table

import com.doublemoon1119.mahjongcraft.platform.minecraft.table.TableLocation
import net.minecraft.server.world.ServerWorld
import net.minecraft.util.math.BlockPos

/** 將 Fabric 1.20.1 世界與方塊座標轉為版本無關的桌子位置。 */
fun ServerWorld.toTableLocation(pos: BlockPos): TableLocation = TableLocation(
    dimensionId = registryKey.value.toString(),
    x = pos.x,
    y = pos.y,
    z = pos.z,
)
