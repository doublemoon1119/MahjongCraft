package com.doublemoon1119.mahjongcraft.platform.minecraft.mod.vanilla

import com.mojang.authlib.GameProfile
import net.minecraft.client.Minecraft

/** 本機玩家登入時使用的 profile，名稱與新版 Minecraft 相同；新版本直接使用原版的同名方法。 */
val Minecraft.gameProfile: GameProfile
    get() = user.gameProfile
