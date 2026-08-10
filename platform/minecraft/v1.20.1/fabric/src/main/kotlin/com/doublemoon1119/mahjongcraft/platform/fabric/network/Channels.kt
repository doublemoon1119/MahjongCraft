package com.doublemoon1119.mahjongcraft.platform.fabric.network

import kotlinx.serialization.KSerializer
import kotlinx.serialization.json.Json
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking
import net.fabricmc.fabric.api.networking.v1.PacketByteBufs
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking
import net.minecraft.server.MinecraftServer
import net.minecraft.server.network.ServerPlayerEntity
import net.minecraft.util.Identifier

/** JSON 字串走 [net.minecraft.network.PacketByteBuf] 內建的 varint 長度前綴字串編碼，上限字元數。 */
private const val MAX_PAYLOAD_LENGTH = 1 shl 20

/**
 * 伺服器→客戶端頻道：把 [T]（線路 DTO）序列化成 JSON 字串，包進 1.20.1 舊式 raw-buffer 封包
 * （[net.minecraft.network.PacketByteBuf]）送給單一玩家。
 */
class S2CChannel<T>(id: String, private val serializer: KSerializer<T>) {
    val channelId: Identifier = Identifier("mahjongcraft", id)

    fun sendTo(player: ServerPlayerEntity, json: Json, value: T) {
        val buf = PacketByteBufs.create()
        buf.writeString(json.encodeToString(serializer, value), MAX_PAYLOAD_LENGTH)
        ServerPlayNetworking.send(player, channelId, buf)
    }

    /** 只能在 client entrypoint 呼叫。收到的封包會先同步讀完 buffer，再把 [handler] 丟回客戶端主執行緒執行。 */
    fun registerClientReceiver(json: Json, handler: (T) -> Unit) {
        ClientPlayNetworking.registerGlobalReceiver(channelId) { client, _, buf, _ ->
            val raw = buf.readString(MAX_PAYLOAD_LENGTH)
            client.execute { handler(json.decodeFromString(serializer, raw)) }
        }
    }
}

/**
 * 客戶端→伺服器頻道。[registerServerReceiver] 的 receive callback 跑在網路執行緒——`buf` 在回呼結束後
 * 可能被釋放，必須同步讀完；實際處理邏輯透過 [handler] 丟回伺服器執行緒執行，玩家身分一律用回呼收到
 * 的 [ServerPlayerEntity] 自己的 UUID，不信任封包內容宣稱的身分。
 */
class C2SChannel<T>(id: String, private val serializer: KSerializer<T>) {
    val channelId: Identifier = Identifier("mahjongcraft", id)

    /** 只能在 client entrypoint 呼叫。 */
    fun sendToServer(json: Json, value: T) {
        val buf = PacketByteBufs.create()
        buf.writeString(json.encodeToString(serializer, value), MAX_PAYLOAD_LENGTH)
        ClientPlayNetworking.send(channelId, buf)
    }

    fun registerServerReceiver(json: Json, handler: (MinecraftServer, ServerPlayerEntity, T) -> Unit) {
        ServerPlayNetworking.registerGlobalReceiver(channelId) { server, player, _, buf, _ ->
            val raw = buf.readString(MAX_PAYLOAD_LENGTH)
            val decoded = json.decodeFromString(serializer, raw)
            server.execute { handler(server, player, decoded) }
        }
    }
}
