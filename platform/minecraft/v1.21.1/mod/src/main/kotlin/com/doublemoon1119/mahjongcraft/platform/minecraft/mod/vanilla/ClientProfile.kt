package com.doublemoon1119.mahjongcraft.platform.minecraft.mod.vanilla

import com.mojang.authlib.GameProfile
import net.minecraft.client.Minecraft

/**
 * 本機玩家登入時使用的 profile。
 *
 * - 這個版本的原版已有同名的 getter，這個宣告只讓共用程式碼的 import 能夠解析。
 * - 明確呼叫 getter，避免這個擴充屬性解析成自己。
 */
val Minecraft.gameProfile: GameProfile
    get() = getGameProfile()
