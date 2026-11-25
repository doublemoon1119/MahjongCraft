package com.doublemoon1119.mahjongcraft.platform.minecraft.mod.loader

/** 頻道上的封包往哪個方向送。 */
enum class ChannelDirection {
    /** 伺服器送給用戶端。 */
    TO_CLIENT,

    /** 用戶端送給伺服器。 */
    TO_SERVER,
}
