package com.doublemoon1119.mahjongcraft.platform.minecraft.mod.client.history

import com.doublemoon1119.mahjongcraft.platform.minecraft.mod.client.player.ClientPlayerProfileResolver
import com.mojang.authlib.GameProfile
import net.minecraft.client.multiplayer.ClientLevel
import net.minecraft.client.player.RemotePlayer
import net.minecraft.client.resources.DefaultPlayerSkin
import net.minecraft.resources.ResourceLocation
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
    world: ClientLevel,
    private val playerId: Uuid,
    profile: GameProfile,
    private val profiles: ClientPlayerProfileResolver,
) : RemotePlayer(world, profile) {
    override fun isSkinLoaded(): Boolean = true

    override fun getSkinTextureLocation(): ResourceLocation = profiles.resolvedSkinTexture(playerId) ?: DefaultPlayerSkin.getDefaultSkin(playerId.toJavaUuid())

    override fun getModelName(): String = profiles.resolvedSkinModel(playerId) ?: DefaultPlayerSkin.getSkinModelName(playerId.toJavaUuid())
}
