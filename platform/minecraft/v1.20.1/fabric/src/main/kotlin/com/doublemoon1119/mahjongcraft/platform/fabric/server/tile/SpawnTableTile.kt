package com.doublemoon1119.mahjongcraft.platform.fabric.server.tile

import com.doublemoon1119.mahjongcraft.platform.fabric.entity.MahjongTileEntity
import com.doublemoon1119.mahjongcraft.platform.fabric.server.entity.FabricEntitySpawnGateway
import com.doublemoon1119.mahjongcraft.platform.minecraft.tile.MahjongTileWallPlacement
import net.minecraft.server.world.ServerWorld
import kotlin.uuid.Uuid
import kotlin.uuid.toJavaUuid

/** 建立首次離開牌池的桌面牌，等到落下動畫開始才顯示。 */
internal fun spawnTableTile(
    gateway: FabricEntitySpawnGateway,
    world: ServerWorld,
    tableId: Uuid,
    tileId: Uuid,
    placement: MahjongTileWallPlacement,
): MahjongTileEntity? {
    val tile = MahjongTileEntity(world = world).apply {
        uuid = tileId.toJavaUuid()
        refreshPositionAndAngles(placement.x, placement.y, placement.z, placement.yaw, 0.0f)
        assignToTable(tableId)
        isInvisible = true
    }
    return tile.takeIf { gateway.spawn(world, it, "table-tile", tableId) }
}
