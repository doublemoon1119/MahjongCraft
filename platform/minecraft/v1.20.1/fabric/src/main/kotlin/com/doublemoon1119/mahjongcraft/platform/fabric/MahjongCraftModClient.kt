package com.doublemoon1119.mahjongcraft.platform.fabric

import com.doublemoon1119.mahjongcraft.flow.network.dto.rule.NetworkDtoRegistries
import com.doublemoon1119.mahjongcraft.flow.network.dto.snapshot.toDomain
import com.doublemoon1119.mahjongcraft.platform.fabric.client.ClientMahjongStateStore
import com.doublemoon1119.mahjongcraft.platform.fabric.item.MahjongTileItem
import com.doublemoon1119.mahjongcraft.platform.fabric.network.MahjongChannels
import com.doublemoon1119.mahjongcraft.platform.fabric.registry.ModItems
import com.doublemoon1119.mahjongcraft.platform.minecraft.tile.ALL_RIICHI_TILE_ASSET_KEYS
import net.fabricmc.api.ClientModInitializer
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents
import net.minecraft.client.item.ModelPredicateProviderRegistry
import net.minecraft.util.Identifier
import org.koin.core.context.GlobalContext
import kotlin.uuid.Uuid

class MahjongCraftModClient : ClientModInitializer {

    override fun onInitializeClient() {
        ModelPredicateProviderRegistry.register(ModItems.MAHJONG_TILE, Identifier("tile")) { stack, _, _, _ ->
            val storedKey = stack.nbt?.getString(MahjongTileItem.NBT_KEY_TILE)
            val index = storedKey?.let { ALL_RIICHI_TILE_ASSET_KEYS.indexOf(it) } ?: -1
            (if (index >= 0) index else 0) / 100f
        }

        val koin = GlobalContext.get()
        val json = koin.get<kotlinx.serialization.json.Json>()
        val networkRegistries = koin.get<NetworkDtoRegistries>()
        val stateStore = koin.get<ClientMahjongStateStore>()
        MahjongChannels.roomUpdate.registerClientReceiver(json, stateStore::apply)
        MahjongChannels.gameUpdate.registerClientReceiver(json, stateStore::apply)
        MahjongChannels.roomSnapshot.registerClientReceiver(json) { payload ->
            stateStore.applyRoomSnapshot(
                Uuid.parse(payload.roomId),
                payload.snapshot.toDomain(networkRegistries),
            )
        }
        MahjongChannels.gameSnapshot.registerClientReceiver(json) { payload ->
            stateStore.applyGameSnapshot(
                Uuid.parse(payload.gameId),
                payload.snapshot.toDomain(networkRegistries),
            )
        }
        ClientPlayConnectionEvents.DISCONNECT.register { _, _ -> stateStore.clear() }
    }
}
