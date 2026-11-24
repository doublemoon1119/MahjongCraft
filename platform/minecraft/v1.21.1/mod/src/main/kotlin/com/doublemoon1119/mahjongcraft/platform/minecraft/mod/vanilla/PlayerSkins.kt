package com.doublemoon1119.mahjongcraft.platform.minecraft.mod.vanilla

import com.doublemoon1119.mahjongcraft.platform.minecraft.mod.client.player.SkinTextures
import com.mojang.authlib.GameProfile
import net.minecraft.client.Minecraft
import net.minecraft.client.multiplayer.PlayerInfo
import net.minecraft.client.resources.DefaultPlayerSkin
import net.minecraft.client.resources.PlayerSkin
import net.minecraft.resources.ResourceLocation
import java.util.UUID

/**
 * 玩家列表中這位玩家目前的外觀材質。
 *
 * @return 皮膚、手臂款式與披風。
 */
fun PlayerInfo.skinTextures(): SkinTextures = skin.toSkinTextures()

/**
 * 沒有取得皮膚時，原版依玩家識別碼給的預設外觀。
 *
 * @param playerId 玩家識別碼。
 * @return 預設的皮膚與手臂款式，沒有披風。
 */
fun defaultSkinTextures(playerId: UUID): SkinTextures = DefaultPlayerSkin.get(playerId).toSkinTextures()

/**
 * 透過原版的皮膚管理器非同步載入 profile 的皮膚與披風。回呼可能在其他執行緒執行，需要時由呼叫端切回主執行緒。
 *
 * @param profile 已知的玩家 profile。
 * @param onSkin 皮膚載入結束時呼叫，帶入皮膚材質與手臂款式；沒有取得皮膚時材質為 null。
 * @param onCape 取得披風時呼叫；玩家沒有披風時不呼叫。
 */
fun requestSkinTextures(profile: GameProfile, onSkin: (ResourceLocation?, String) -> Unit, onCape: (ResourceLocation) -> Unit) {
    Minecraft.getInstance().skinManager.getOrLoad(profile).whenComplete { skin, error ->
        // 原版在 profile 沒有皮膚時回傳預設外觀，網址為 null；這種情況和載入失敗一樣視為沒有取得皮膚。
        if (error != null || skin.textureUrl() == null) {
            onSkin(null, SkinTextures.DEFAULT_MODEL)
        } else {
            onSkin(skin.texture(), skin.model().id())
        }
        skin?.capeTexture()?.let(onCape)
    }
}

/** 轉成共用程式碼使用的外觀材質。 */
private fun PlayerSkin.toSkinTextures(): SkinTextures = SkinTextures(texture(), model().id(), capeTexture())
