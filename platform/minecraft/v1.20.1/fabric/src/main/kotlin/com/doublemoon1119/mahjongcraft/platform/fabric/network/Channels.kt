package com.doublemoon1119.mahjongcraft.platform.fabric.network

import com.doublemoon1119.mahjongcraft.platform.fabric.logging.mahjongCraftLogger
import com.doublemoon1119.mahjongcraft.platform.minecraft.metadata.MinecraftModMetadata
import kotlinx.serialization.KSerializer
import kotlinx.serialization.json.Json
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking
import net.fabricmc.fabric.api.networking.v1.PacketByteBufs
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking
import net.minecraft.network.PacketByteBuf
import net.minecraft.server.MinecraftServer
import net.minecraft.server.network.ServerPlayerEntity
import net.minecraft.util.Identifier
import java.util.concurrent.atomic.AtomicBoolean

/** JSON 字串走 [net.minecraft.network.PacketByteBuf] 內建的 varint 長度前綴字串編碼，上限字元數。 */
private const val MAX_PAYLOAD_LENGTH = 1 shl 20

/** PacketByteBuf 字串 varint 長度前綴的最大位元組數。 */
private const val MAX_STRING_PREFIX_BYTES = 5

/**
 * 在反序列化前驗證有界字串，拒絕過大、損壞或帶有額外內容的封包。
 *
 * @param buf 僅在接收回呼期間有效的封包 buffer。
 * @param json 線路序列化設定。
 * @param serializer 要求的 DTO 契約。
 * @param maxPayloadBytes JSON 的 UTF-8 位元組上限。
 * @return 完整且合法的 DTO；拒絕時為 null。
 */
internal fun <T> decodeBoundedPayload(buf: PacketByteBuf, json: Json, serializer: KSerializer<T>, maxPayloadBytes: Int): T? {
    if (buf.readableBytes() > maxPayloadBytes + MAX_STRING_PREFIX_BYTES) return null
    return try {
        val raw = buf.readString(maxPayloadBytes)
        if (buf.isReadable || raw.toByteArray(Charsets.UTF_8).size > maxPayloadBytes) return null
        json.decodeFromString(serializer, raw)
    } catch (_: Exception) {
        null
    }
}

/**
 * 伺服器→客戶端頻道：把 [T]（線路 DTO）序列化成 JSON 字串，包進 1.20.1 舊式 raw-buffer 封包
 * （[net.minecraft.network.PacketByteBuf]）送給單一玩家。
 *
 * @param id 模組命名空間內的頻道名稱。
 * @property serializer 線路資料的序列化契約。
 * @property maxPayloadBytes JSON UTF-8 位元組上限；null 保持既有頻道行為。
 */
class S2CChannel<T>(id: String, private val serializer: KSerializer<T>, private val maxPayloadBytes: Int? = null) {
    /** Fabric 網路使用的頻道識別碼。 */
    val channelId: Identifier = Identifier(MinecraftModMetadata.MOD_ID, id)

    /** 記錄丟棄過大回應的 logger。 */
    private val logger = mahjongCraftLogger(S2CChannel::class)

    /** 這個頻道是否已以警告記錄過丟棄過大的回應；之後改用 debug，避免洗版。 */
    private val oversizedWarned = AtomicBoolean(false)

    /**
     * 編碼並傳送一份符合頻道限制的回應。
     *
     * @param player 回應的連線玩家。
     * @param json 線路序列化設定。
     * @param value 要傳送的線路資料。
     */
    fun sendTo(player: ServerPlayerEntity, json: Json, value: T) {
        val buf = PacketByteBufs.create()
        val encoded = json.encodeToString(serializer, value)
        require(maxPayloadBytes == null || encoded.toByteArray(Charsets.UTF_8).size <= maxPayloadBytes) { "Channel response exceeds its payload limit" }
        buf.writeString(encoded, MAX_PAYLOAD_LENGTH)
        ServerPlayNetworking.send(player, channelId, buf)
    }

    /**
     * 註冊客戶端接收器；同步讀取 buffer 後交回主執行緒，連線已切換時不套用舊回覆。
     *
     * @param json 線路序列化設定。
     * @param handler 主執行緒上的接收處理。
     */
    fun registerClientReceiver(json: Json, handler: (T) -> Unit) {
        ClientPlayNetworking.registerGlobalReceiver(channelId) { client, connection, buf, _ ->
            if (maxPayloadBytes != null && buf.readableBytes() > maxPayloadBytes + MAX_STRING_PREFIX_BYTES) {
                reportOversized(buf.readableBytes(), maxPayloadBytes)
                return@registerGlobalReceiver
            }
            val raw = buf.readString(MAX_PAYLOAD_LENGTH)
            if (maxPayloadBytes != null) {
                val rawBytes = raw.toByteArray(Charsets.UTF_8).size
                if (rawBytes > maxPayloadBytes) {
                    reportOversized(rawBytes, maxPayloadBytes)
                    return@registerGlobalReceiver
                }
            }
            client.execute {
                if (client.networkHandler === connection) handler(json.decodeFromString(serializer, raw))
            }
        }
    }

    /** 丟棄過大的回應；每個頻道第一次以警告記錄，之後改用 debug。 */
    private fun reportOversized(bytes: Int, limit: Int) {
        if (oversizedWarned.compareAndSet(false, true)) {
            logger.warn("Dropped an oversized message on {}: {} bytes exceeds the limit of {} bytes; later drops on this channel are logged at debug level", channelId, bytes, limit)
        } else {
            logger.debug("Dropped an oversized message on {}: {} bytes exceeds the limit of {} bytes", channelId, bytes, limit)
        }
    }
}

/**
 * 客戶端→伺服器頻道。[registerServerReceiver] 的 receive callback 跑在網路執行緒——`buf` 在回呼結束後
 * 可能被釋放，必須同步讀完；實際處理邏輯透過 [handler] 丟回伺服器執行緒執行，玩家身分一律用回呼收到
 * 的 [ServerPlayerEntity] 自己的 UUID，不信任封包內容宣稱的身分。
 *
 * @param id 模組命名空間內的頻道名稱。
 * @property serializer 線路資料的序列化契約。
 * @property maxPayloadBytes 解碼前的 JSON UTF-8 位元組上限；null 保持既有頻道行為。
 */
class C2SChannel<T>(id: String, private val serializer: KSerializer<T>, private val maxPayloadBytes: Int? = null) {
    /** 有界頻道拒絕無法解碼要求時使用的內部診斷。 */
    private val logger = mahjongCraftLogger(C2SChannel::class)

    /** Fabric 網路使用的頻道識別碼。 */
    val channelId: Identifier = Identifier(MinecraftModMetadata.MOD_ID, id)

    /**
     * 從客戶端傳送一份符合頻道限制的要求。
     *
     * @param json 線路序列化設定。
     * @param value 要傳送的線路資料。
     */
    fun sendToServer(json: Json, value: T) {
        val buf = PacketByteBufs.create()
        val encoded = json.encodeToString(serializer, value)
        require(maxPayloadBytes == null || encoded.toByteArray(Charsets.UTF_8).size <= maxPayloadBytes) { "Channel request exceeds its payload limit" }
        buf.writeString(encoded, MAX_PAYLOAD_LENGTH)
        ClientPlayNetworking.send(channelId, buf)
    }

    /**
     * 註冊伺服器接收器，有界頻道會在解碼前拒絕過大內容。
     *
     * @param json 線路序列化設定。
     * @param handler 伺服器主執行緒上的接收處理。
     */
    fun registerServerReceiver(json: Json, handler: (MinecraftServer, ServerPlayerEntity, T) -> Unit) {
        ServerPlayNetworking.registerGlobalReceiver(channelId) { server, player, _, buf, _ ->
            if (maxPayloadBytes != null) {
                val decoded = decodeBoundedPayload(buf, json, serializer, maxPayloadBytes)
                if (decoded == null) {
                    logger.debug("Rejected malformed bounded request on {}", channelId)
                    return@registerGlobalReceiver
                }
                server.execute { handler(server, player, decoded) }
                return@registerGlobalReceiver
            }
            val raw = buf.readString(MAX_PAYLOAD_LENGTH)
            val decoded = json.decodeFromString(serializer, raw)
            server.execute { handler(server, player, decoded) }
        }
    }
}
