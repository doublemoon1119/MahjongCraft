package com.doublemoon1119.mahjongcraft.platform.minecraft.mod.server.tile

import com.doublemoon1119.mahjongcraft.platform.minecraft.mod.entity.MahjongTileEntity
import com.doublemoon1119.mahjongcraft.platform.minecraft.mod.server.entity.EntitySpawnGateway
import com.doublemoon1119.mahjongcraft.platform.minecraft.tile.MahjongTileWallPlacement
import net.minecraft.server.level.ServerLevel
import kotlin.uuid.Uuid
import kotlin.uuid.toJavaUuid

/** 建立首次離開牌池的桌面牌，等到落下動畫開始才顯示。 */
internal fun spawnTableTile(
    gateway: EntitySpawnGateway,
    world: ServerLevel,
    tableId: Uuid,
    tileId: Uuid,
    placement: MahjongTileWallPlacement,
): MahjongTileEntity? {
    val tile = MahjongTileEntity(world = world).apply {
        setUUID(tileId.toJavaUuid())
        moveTo(placement.x, placement.y, placement.z, placement.yaw, 0.0f)
        assignToTable(tableId)
        isInvisible = true
    }
    return tile.takeIf { gateway.spawn(world, it, "table-tile", tableId) }
}
