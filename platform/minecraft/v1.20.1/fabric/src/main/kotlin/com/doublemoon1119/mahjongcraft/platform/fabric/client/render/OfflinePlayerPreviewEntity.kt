package com.doublemoon1119.mahjongcraft.platform.fabric.client.render

import com.doublemoon1119.mahjongcraft.platform.minecraft.metadata.MinecraftModMetadata
import com.doublemoon1119.mahjongcraft.platform.minecraft.room.MinecraftRoomScreenKeys
import com.mojang.authlib.GameProfile
import net.minecraft.client.network.OtherClientPlayerEntity
import net.minecraft.client.world.ClientWorld
import net.minecraft.text.Text
import net.minecraft.util.Identifier

/**
 * 找不到真人玩家可預覽（離線、profile 未知）時，room screen 拿來畫全身模型的純視覺替身——固定套用
 * 全黑、正面畫白色「?」的貼圖，不去解析 [profile] 實際的皮膚，見 [RoomScreen][com.doublemoon1119.mahjongcraft.platform.fabric.client.room.RoomScreen]
 * 的 `resolvePlayerPreview`。強制回傳 `"default"` model，確保套用的貼圖（手臂 4px 寬的經典版型）
 * 跟角色模型的手臂寬度一致，不受 [profile] 本身雜湊出來的模型類型影響。名稱標籤固定顯示為多國語系的
 * 「離線玩家」，不使用 [profile] 內部識別用的固定英文名稱。
 */
class OfflinePlayerPreviewEntity(world: ClientWorld, profile: GameProfile) : OtherClientPlayerEntity(world, profile) {
    override fun hasSkinTexture(): Boolean = true

    override fun getSkinTexture(): Identifier = TEXTURE

    override fun getModel(): String = "default"

    override fun getName(): Text = Text.translatable(MinecraftRoomScreenKeys.OFFLINE_PLAYER)

    private companion object {
        val TEXTURE: Identifier = Identifier(MinecraftModMetadata.MOD_ID, "textures/entity/player/offline_player_skin.png")
    }
}
