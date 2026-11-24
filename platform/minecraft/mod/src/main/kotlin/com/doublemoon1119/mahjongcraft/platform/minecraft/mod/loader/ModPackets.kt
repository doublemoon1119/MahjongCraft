package com.doublemoon1119.mahjongcraft.platform.minecraft.mod.loader

/**
 * 封包頻道使用的收送方式。
 *
 * 載入器的進入點在建立任何頻道之前安裝；用戶端的部分只在用戶端安裝。尚未安裝就收送會丟出例外。
 */
object ModPackets {
    /** 伺服器與用戶端都會用到的收送方式。 */
    @Volatile
    private var installedTransport: PacketTransport? = null

    /** 只在用戶端使用的收送方式。 */
    @Volatile
    private var installedClientTransport: ClientPacketTransport? = null

    /** 伺服器與用戶端都會用到的收送方式。 */
    val transport: PacketTransport
        get() = checkNotNull(installedTransport) { "Packet transport is not installed" }

    /** 只在用戶端使用的收送方式。 */
    val clientTransport: ClientPacketTransport
        get() = checkNotNull(installedClientTransport) { "Client packet transport is not installed" }

    /**
     * 安裝伺服器與用戶端都會用到的收送方式。
     *
     * @param transport 載入器的收送方式。
     */
    fun install(transport: PacketTransport) {
        installedTransport = transport
    }

    /**
     * 安裝只在用戶端使用的收送方式。
     *
     * @param transport 載入器的用戶端收送方式。
     */
    fun installClient(transport: ClientPacketTransport) {
        installedClientTransport = transport
    }
}
