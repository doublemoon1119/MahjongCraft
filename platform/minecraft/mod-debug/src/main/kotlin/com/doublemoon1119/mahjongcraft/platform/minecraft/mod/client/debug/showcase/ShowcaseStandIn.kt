package com.doublemoon1119.mahjongcraft.platform.minecraft.mod.client.debug.showcase

import com.mojang.authlib.GameProfile
import net.minecraft.client.multiplayer.ClientLevel
import net.minecraft.client.player.RemotePlayer
import java.util.UUID

/**
 * 宣傳截圖裡坐在本機玩家座位的史蒂夫替身，只存在於用戶端，不加入牌局。
 *
 * - 外觀是原版依 [ID] 挑出的預設外觀：寬手臂的史蒂夫。
 * - 手上不拿東西：剛摸到的牌在桌上，手上再拿一張牌會不合理。
 *
 * @param level 用戶端世界。
 */
internal class ShowcaseStandIn(level: ClientLevel) : RemotePlayer(level, GameProfile(ID, NAME)) {
    private companion object {
        /** 替身的 UUID。原版依 UUID 從預設外觀中挑選，這個 UUID 對應寬手臂的史蒂夫。 */
        val ID: UUID = UUID(0L, 15L)

        /** 替身的名字。截圖時隱藏介面，不會顯示。 */
        const val NAME = "Steve"
    }
}
