package com.doublemoon1119.mahjongcraft.platform.fabric.server.config

import net.minecraft.server.MinecraftServer

/** 依目前 Fabric server 類型提供 MahjongCraft server config 位置。 */
interface FabricServerConfigPathProvider {
    /** 解析 [server] 這次 session 使用的 server config 位置。 */
    fun get(server: MinecraftServer): FabricServerConfigLocation
}
