package com.doublemoon1119.mahjongcraft.platform.minecraft.mod.client.player

import net.minecraft.client.Minecraft
import net.minecraft.world.entity.player.PlayerModelPart
import kotlin.uuid.Uuid
import kotlin.uuid.toJavaUuid

/**
 * 不在世界中的玩家外觀是否顯示指定外層部位，例如帽子或外套。
 *
 * @param part 要判斷的外層部位。
 * @param livePartShown 玩家目前在世界中時，他自己的外觀設定。不在世界中時為 null。
 * @return 有玩家自己的設定時照設定，否則一律顯示。
 */
internal fun modelPartShown(part: PlayerModelPart, livePartShown: ((PlayerModelPart) -> Boolean)?): Boolean = livePartShown?.invoke(part) ?: true

/**
 * 依目前客戶端世界判斷指定玩家的外層部位是否顯示。玩家不在目前世界中時一律顯示。
 *
 * @param playerId 玩家識別碼。
 * @param part 要判斷的外層部位。
 * @return 是否顯示該部位。
 */
internal fun modelPartShown(playerId: Uuid, part: PlayerModelPart): Boolean {
    val livePlayer = Minecraft.getInstance().level?.getPlayerByUUID(playerId.toJavaUuid())
    return modelPartShown(part, livePlayer?.let { it::isModelPartShown })
}
