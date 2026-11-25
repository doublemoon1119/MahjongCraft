package com.doublemoon1119.mahjongcraft.platform.minecraft.mod.client.player

import com.mojang.authlib.GameProfile
import net.minecraft.client.multiplayer.ClientLevel
import net.minecraft.client.player.RemotePlayer
import net.minecraft.client.resources.PlayerSkin

/**
 * 外觀由本 mod 決定的用戶端玩家，例如回放中的玩家或房間畫面的預覽：子類別在 [customSkin] 提供外觀，各版本的原版外觀
 * 方法都交給它。
 *
 * @param world 所屬的用戶端世界。
 * @param profile 玩家 profile。只用來建立模型。
 */
abstract class CustomSkinPlayer(world: ClientLevel, profile: GameProfile) : RemotePlayer(world, profile) {
    /**
     * 這位玩家要顯示的外觀。
     *
     * @return 皮膚、手臂款式與披風。
     */
    abstract fun customSkin(): SkinTextures

    final override fun getSkin(): PlayerSkin = customSkin().let { skin ->
        PlayerSkin(skin.texture, null, skin.cape, null, PlayerSkin.Model.byName(skin.modelName), false)
    }
}
