package com.doublemoon1119.mahjongcraft.platform.forge.loader

import net.minecraft.resources.ResourceLocation
import net.minecraftforge.network.NetworkRegistry
import net.minecraftforge.network.event.EventNetworkChannel
import java.util.concurrent.ConcurrentHashMap

/**
 * 本 mod 在 Forge 建立的封包頻道。
 *
 * 頻道接受沒有安裝本 mod 的對方，因此未安裝本 mod 的玩家也能進入伺服器，只是收不到本 mod 的封包。
 */
internal object ForgeNetworkChannels {
    /** 頻道的協定版本。雙方都安裝本 mod 時必須相同。 */
    private const val PROTOCOL_VERSION = "1"

    /** 已建立的頻道。 */
    private val channels = ConcurrentHashMap<ResourceLocation, EventNetworkChannel>()

    /**
     * 建立頻道。同一個頻道只建立一次。
     *
     * @param channelId 頻道識別碼。
     */
    fun declare(channelId: ResourceLocation) {
        channels.computeIfAbsent(channelId) { id ->
            NetworkRegistry.newEventChannel(
                id,
                { PROTOCOL_VERSION },
                NetworkRegistry.acceptMissingOr(PROTOCOL_VERSION),
                NetworkRegistry.acceptMissingOr(PROTOCOL_VERSION),
            )
        }
    }

    /**
     * 取得已建立的頻道。
     *
     * @param channelId 頻道識別碼。
     * @return 頻道。
     * @throws IllegalStateException 頻道尚未建立。
     */
    fun channel(channelId: ResourceLocation): EventNetworkChannel = checkNotNull(channels[channelId]) { "Channel $channelId is not declared" }
}
