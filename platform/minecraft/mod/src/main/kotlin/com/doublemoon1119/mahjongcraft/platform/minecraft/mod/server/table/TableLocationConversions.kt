package com.doublemoon1119.mahjongcraft.platform.minecraft.mod.server.table

import com.doublemoon1119.mahjongcraft.platform.minecraft.table.TableLocation
import net.minecraft.core.BlockPos
import net.minecraft.server.level.ServerLevel

/**
 * 將 Minecraft 1.20.1 世界與方塊座標轉為版本無關的桌子位置。
 *
 * @param pos 要轉換的方塊座標。
 * @return 含維度 ID 與座標的桌子位置。
 */
fun ServerLevel.toTableLocation(pos: BlockPos): TableLocation = TableLocation(
    dimensionId = dimension().location().toString(),
    x = pos.x,
    y = pos.y,
    z = pos.z,
)
