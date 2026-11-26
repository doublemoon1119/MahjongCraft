package com.doublemoon1119.mahjongcraft.example.minecraft.mod

import net.minecraft.SharedConstants
import net.minecraft.server.Bootstrap

/** 讓測試在使用原版登記表之前完成 Minecraft 的初始化。重複呼叫不會重做。 */
object ExampleMinecraftTestBootstrap {
    /** 確認 Minecraft 已初始化。 */
    fun ensure() {
        SharedConstants.tryDetectVersion()
        Bootstrap.bootStrap()
    }
}
