package com.doublemoon1119.mahjongcraft.platform.minecraft.mod.debug.vanilla

import net.minecraft.client.multiplayer.ClientLevel
import net.minecraft.world.entity.Entity

/**
 * 把只存在於用戶端的 entity 加入世界。伺服器不知道它，也不會送來它的更新。
 *
 * @param level 用戶端世界。
 * @param entity 要加入的 entity，使用建立時取得的 ID。
 */
fun addClientOnlyEntity(level: ClientLevel, entity: Entity) {
    level.putNonPlayerEntity(entity.id, entity)
}
