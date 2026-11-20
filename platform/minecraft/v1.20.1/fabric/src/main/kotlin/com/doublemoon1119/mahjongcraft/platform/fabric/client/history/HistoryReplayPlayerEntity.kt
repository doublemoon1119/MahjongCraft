package com.doublemoon1119.mahjongcraft.platform.fabric.client.history

import com.doublemoon1119.mahjongcraft.platform.fabric.client.player.ClientPlayerProfileResolver
import com.mojang.authlib.GameProfile
import net.minecraft.client.network.OtherClientPlayerEntity
import net.minecraft.client.util.DefaultSkinHelper
import net.minecraft.client.world.ClientWorld
import net.minecraft.util.Identifier
import kotlin.uuid.Uuid
import kotlin.uuid.toJavaUuid

/**
 * 牌譜回放場景中的真人玩家模型，不加入世界。
 *
 * 外觀與牌譜頭像相同：在線上時用玩家列表的皮膚，正版帳號用原生皮膚服務載入的皮膚，都拿不到時用原版依 UUID 決定的
 * 預設皮膚。每一幀重新查詢，皮膚載入完成後自動換上；手臂粗細跟著同一份皮膚。
 *
 * @param world 目前客戶端世界。
 * @property playerId 玩家識別碼。
 * @param profile 玩家 profile；只用來建立模型。
 * @property profiles 皮膚與手臂款式的來源。
 */
internal class HistoryReplayPlayerEntity(
    world: ClientWorld,
    private val playerId: Uuid,
    profile: GameProfile,
    private val profiles: ClientPlayerProfileResolver,
) : OtherClientPlayerEntity(world, profile) {
    override fun hasSkinTexture(): Boolean = true

    override fun getSkinTexture(): Identifier = profiles.resolvedSkinTexture(playerId) ?: DefaultSkinHelper.getTexture(playerId.toJavaUuid())

    override fun getModel(): String = profiles.resolvedSkinModel(playerId) ?: DefaultSkinHelper.getModel(playerId.toJavaUuid())
}
