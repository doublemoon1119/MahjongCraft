package com.doublemoon1119.mahjongcraft.platform.fabric

import com.doublemoon1119.mahjongcraft.flow.dto.buildMahjongDtoSerializersModule
import com.doublemoon1119.mahjongcraft.flow.dto.toDomain
import com.doublemoon1119.mahjongcraft.platform.fabric.client.ClientMahjongStateStore
import com.doublemoon1119.mahjongcraft.platform.fabric.item.MahjongTileItem
import com.doublemoon1119.mahjongcraft.platform.fabric.network.MahjongChannels
import com.doublemoon1119.mahjongcraft.platform.fabric.registry.ModItems
import com.doublemoon1119.mahjongcraft.platform.minecraft.tile.ALL_RIICHI_TILE_ASSET_KEYS
import kotlinx.serialization.json.Json
import net.fabricmc.api.ClientModInitializer
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents
import net.minecraft.client.item.ModelPredicateProviderRegistry
import net.minecraft.util.Identifier
import kotlin.uuid.Uuid

class MahjongCraftModClient : ClientModInitializer {

    override fun onInitializeClient() {
        ModelPredicateProviderRegistry.register(ModItems.MAHJONG_TILE, Identifier("tile")) { stack, _, _, _ ->
            val storedKey = stack.nbt?.getString(MahjongTileItem.NBT_KEY_TILE)
            val index = storedKey?.let { ALL_RIICHI_TILE_ASSET_KEYS.indexOf(it) } ?: -1
            (if (index >= 0) index else 0) / 100f
        }

        val json = Json { serializersModule = buildMahjongDtoSerializersModule() }
        MahjongChannels.roomUpdate.registerClientReceiver(json, ClientMahjongStateStore::apply)
        MahjongChannels.gameUpdate.registerClientReceiver(json, ClientMahjongStateStore::apply)
        MahjongChannels.roomSnapshot.registerClientReceiver(json) { payload ->
            ClientMahjongStateStore.applyRoomSnapshot(Uuid.parse(payload.roomId), payload.snapshot.toDomain())
        }
        MahjongChannels.gameSnapshot.registerClientReceiver(json) { payload ->
            ClientMahjongStateStore.applyGameSnapshot(Uuid.parse(payload.gameId), payload.snapshot.toDomain())
        }
        ClientPlayConnectionEvents.DISCONNECT.register { _, _ -> ClientMahjongStateStore.clear() }
    }
}
