package com.doublemoon1119.mahjongcraft.platform.fabric

import com.doublemoon1119.mahjongcraft.flow.client.game.ClientDecisionTimerStateStore
import com.doublemoon1119.mahjongcraft.flow.common.game.model.PlayerDecisionPhase
import com.doublemoon1119.mahjongcraft.flow.network.dto.message.PlayerDecisionPhaseDto
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
        val koin = GlobalContext.get()

        ModelPredicateProviderRegistry.register(ModItems.MAHJONG_TILE, Identifier("tile")) { stack, _, _, _ ->
            val storedKey = stack.nbt?.getString(MahjongTileItem.NBT_KEY_TILE)
            val index = storedKey?.let { ALL_RIICHI_TILE_ASSET_KEYS.indexOf(it) } ?: -1
            (if (index >= 0) index else 0) / 100f
        }

        val json = koin.get<kotlinx.serialization.json.Json>()
        val networkRegistries = koin.get<NetworkDtoRegistries>()
        val stateStore = koin.get<ClientMahjongStateStore>()
        val decisionTimerStore = koin.get<ClientDecisionTimerStateStore>()
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
        MahjongChannels.decisionTimerUpdate.registerClientReceiver(json) { payload ->
            val gameId = Uuid.parse(payload.gameId)
            val status = payload.status
            if (status == null) {
                decisionTimerStore.stop(gameId)
            } else {
                decisionTimerStore.apply(
                    gameId = gameId,
                    phase = status.phase.toDomain(),
                    baseRemainingMillis = status.baseRemainingMillis,
                    reserveRemainingMillis = status.reserveRemainingMillis,
                )
            }
        }
        ClientPlayConnectionEvents.DISCONNECT.register { _, _ ->
            stateStore.clear()
            decisionTimerStore.clear()
        }
    }
}

/** 將網路決策階段還原成 flow common 型別。 */
private fun PlayerDecisionPhaseDto.toDomain(): PlayerDecisionPhase = when (this) {
    PlayerDecisionPhaseDto.OWN_TURN -> PlayerDecisionPhase.OWN_TURN
    PlayerDecisionPhaseDto.DISCARD_REACTION -> PlayerDecisionPhase.DISCARD_REACTION
    PlayerDecisionPhaseDto.CHANKAN_REACTION -> PlayerDecisionPhase.CHANKAN_REACTION
}
