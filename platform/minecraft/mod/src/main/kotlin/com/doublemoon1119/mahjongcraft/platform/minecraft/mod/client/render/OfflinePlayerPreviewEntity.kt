package com.doublemoon1119.mahjongcraft.platform.minecraft.mod.client.render

import com.doublemoon1119.mahjongcraft.platform.minecraft.metadata.MinecraftModMetadata
import com.doublemoon1119.mahjongcraft.platform.minecraft.mod.client.player.CustomSkinPlayer
import com.doublemoon1119.mahjongcraft.platform.minecraft.mod.client.player.SkinTextures
import com.doublemoon1119.mahjongcraft.platform.minecraft.mod.client.room.RoomScreen
import com.doublemoon1119.mahjongcraft.platform.minecraft.mod.vanilla.resourceId
import com.doublemoon1119.mahjongcraft.platform.minecraft.room.MinecraftRoomScreenKeys
import com.mojang.authlib.GameProfile
import net.minecraft.client.multiplayer.ClientLevel
import net.minecraft.network.chat.Component
import net.minecraft.resources.ResourceLocation

/**
 * 找不到真人玩家可預覽（離線、profile 未知）時，room screen 拿來畫全身模型的純視覺替身——固定套用
 * 全黑、正面畫白色「?」的貼圖，不去解析 [profile] 實際的皮膚，見 [RoomScreen][RoomScreen]
 * 的 `resolvePlayerPreview`。強制回傳 `"default"` model，確保套用的貼圖（手臂 4px 寬的經典版型）
 * 跟角色模型的手臂寬度一致，不受 [profile] 本身雜湊出來的模型類型影響。名稱標籤固定顯示為多國語系的
 * 「離線玩家」，不使用 [profile] 內部識別用的固定英文名稱。
 *
 * @param world 此替身所屬的 client world。
 * @param profile 與原玩家關聯的 profile。此類別不使用其中的皮膚或名稱顯示。
 */
class OfflinePlayerPreviewEntity(world: ClientLevel, profile: GameProfile) : CustomSkinPlayer(world, profile) {
    override fun customSkin(): SkinTextures = SkinTextures(texture = TEXTURE, modelName = SkinTextures.DEFAULT_MODEL, cape = null)

    override fun getName(): Component = Component.translatable(MinecraftRoomScreenKeys.OFFLINE_PLAYER)

    private companion object {
        val TEXTURE: ResourceLocation = resourceId(MinecraftModMetadata.MOD_ID, "textures/entity/player/offline_player_skin.png")
    }
}
