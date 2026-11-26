package com.doublemoon1119.mahjongcraft.platform.neoforge.loader

import com.doublemoon1119.mahjongcraft.platform.minecraft.mod.loader.ChannelDirection
import io.netty.buffer.Unpooled
import net.minecraft.network.FriendlyByteBuf
import net.minecraft.network.RegistryFriendlyByteBuf
import net.minecraft.network.codec.StreamCodec
import net.minecraft.network.protocol.common.custom.CustomPacketPayload
import net.minecraft.resources.ResourceLocation
import net.neoforged.bus.api.EventPriority
import net.neoforged.bus.api.IEventBus
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent
import net.neoforged.neoforge.network.handling.IPayloadContext
import java.util.concurrent.ConcurrentHashMap

/**
 * 把各頻道包成 NeoForge 的封包內容類型。
 *
 * - NeoForge 只在登記封包的事件中接受類型與處理方式，因此宣告的頻道先記下，事件發生時一次登記。
 * - 處理方式在收到封包時才查詢登記的接收者，接收者可以在事件之前或之後登記。
 * - 頻道是選用的：連到沒有安裝本 mod 的另一端時照常連線。
 */
internal object NeoForgeChannelPayloads {
    /** 封包協定的版本。兩端版本不同時 NeoForge 拒絕連線。 */
    private const val PROTOCOL_VERSION = "1"

    /** 宣告的頻道與方向，依宣告順序排列。 */
    private val declared = ConcurrentHashMap<ResourceLocation, Declaration>()

    /** 伺服器收到各頻道封包時的處理。 */
    private val serverReceivers = ConcurrentHashMap<ResourceLocation, (IPayloadContext, FriendlyByteBuf) -> Unit>()

    /** 用戶端收到各頻道封包時的處理。 */
    private val clientReceivers = ConcurrentHashMap<ResourceLocation, (FriendlyByteBuf) -> Unit>()

    /** NeoForge 是否已登記過封包；之後宣告的頻道無法使用。 */
    @Volatile
    private var registered = false

    /**
     * 接上本 mod 的事件匯流排，在 NeoForge 登記封包時登記所有宣告的頻道。
     *
     * @param modBus 本 mod 的事件匯流排。
     */
    fun attach(modBus: IEventBus) {
        modBus.addListener(EventPriority.NORMAL, false, RegisterPayloadHandlersEvent::class.java, ::register)
    }

    /**
     * 宣告一個頻道。
     *
     * @param channelId 頻道識別碼。
     * @param direction 頻道上的封包往哪個方向送。
     * @throws IllegalStateException 頻道已宣告過，或 NeoForge 已登記過封包。
     */
    fun declare(channelId: ResourceLocation, direction: ChannelDirection) {
        check(!registered) { "Channel $channelId is declared after NeoForge registered the payloads" }
        val declaration = Declaration(CustomPacketPayload.Type(channelId), direction)
        check(declared.putIfAbsent(channelId, declaration) == null) { "Channel $channelId is declared twice" }
    }

    /**
     * 登記伺服器收到這個頻道的封包時的處理。
     *
     * @param channelId 頻道識別碼。
     * @param receiver 收到封包的情境與封包內容。
     */
    fun registerServerReceiver(channelId: ResourceLocation, receiver: (IPayloadContext, FriendlyByteBuf) -> Unit) {
        check(serverReceivers.putIfAbsent(channelId, receiver) == null) { "Channel $channelId already has a server receiver" }
    }

    /**
     * 登記用戶端收到這個頻道的封包時的處理。
     *
     * @param channelId 頻道識別碼。
     * @param receiver 收到的封包內容。
     */
    fun registerClientReceiver(channelId: ResourceLocation, receiver: (FriendlyByteBuf) -> Unit) {
        check(clientReceivers.putIfAbsent(channelId, receiver) == null) { "Channel $channelId already has a client receiver" }
    }

    /**
     * 把要送出的內容包成這個頻道的封包。
     *
     * @param channelId 頻道識別碼。
     * @param buf 封包內容。讀出後不再使用。
     * @return 封包。
     */
    fun wrap(channelId: ResourceLocation, buf: FriendlyByteBuf): ChannelPayload = ChannelPayload(
        payloadType = declaration(channelId).type,
        bytes = ByteArray(buf.readableBytes()).also(buf::readBytes),
    )

    /**
     * 在 NeoForge 登記封包時登記所有宣告的頻道。
     *
     * @param event 登記封包的事件。
     */
    private fun register(event: RegisterPayloadHandlersEvent) {
        registered = true
        val registrar = event.registrar(PROTOCOL_VERSION).optional()
        declared.values.forEach { declaration ->
            val codec = StreamCodec.of<RegistryFriendlyByteBuf, ChannelPayload>(
                { buf, payload -> buf.writeBytes(payload.bytes) },
                { buf -> ChannelPayload(declaration.type, ByteArray(buf.readableBytes()).also(buf::readBytes)) },
            )
            val id = declaration.type.id()
            when (declaration.direction) {
                ChannelDirection.TO_CLIENT -> registrar.playToClient(declaration.type, codec) { payload, _ ->
                    receiverFor(clientReceivers, id)(unwrap(payload))
                }
                ChannelDirection.TO_SERVER -> registrar.playToServer(declaration.type, codec) { payload, context ->
                    receiverFor(serverReceivers, id)(context, unwrap(payload))
                }
            }
        }
    }

    /**
     * 取得頻道的宣告。
     *
     * @param channelId 頻道識別碼。
     * @return 宣告。
     * @throws IllegalStateException 頻道沒有宣告。
     */
    private fun declaration(channelId: ResourceLocation): Declaration = checkNotNull(declared[channelId]) { "Channel $channelId is not declared" }

    /**
     * 取得頻道的接收者。
     *
     * @param R 接收者的型別。
     * @param receivers 接收者表。
     * @param channelId 頻道識別碼。
     * @return 接收者。
     * @throws IllegalStateException 頻道沒有接收者。
     */
    private fun <R : Any> receiverFor(receivers: Map<ResourceLocation, R>, channelId: ResourceLocation): R = checkNotNull(receivers[channelId]) { "Channel $channelId has no receiver" }

    /**
     * 把收到的封包內容包成可讀取的緩衝區。
     *
     * @param payload 收到的封包。
     * @return 封包內容。
     */
    private fun unwrap(payload: ChannelPayload): FriendlyByteBuf = FriendlyByteBuf(Unpooled.wrappedBuffer(payload.bytes))

    /**
     * 一個頻道的宣告。
     *
     * @property type 頻道的封包類型。
     * @property direction 頻道上的封包往哪個方向送。
     */
    private class Declaration(val type: CustomPacketPayload.Type<ChannelPayload>, val direction: ChannelDirection)
}

/**
 * 一個頻道上的封包，內容是原始位元組。
 *
 * @property payloadType 頻道的封包類型。
 * @property bytes 封包內容。
 */
internal class ChannelPayload(private val payloadType: CustomPacketPayload.Type<ChannelPayload>, val bytes: ByteArray) : CustomPacketPayload {
    override fun type(): CustomPacketPayload.Type<ChannelPayload> = payloadType
}
