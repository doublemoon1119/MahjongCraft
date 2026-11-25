package com.doublemoon1119.mahjongcraft.platform.minecraft.mod.vanilla

import net.minecraft.client.Minecraft

/** 這一幀在目前遊戲刻中的進度，0 到 1。遊戲暫停時停在暫停當下的值。 */
val Minecraft.framePartialTick: Float
    get() = frameTime
