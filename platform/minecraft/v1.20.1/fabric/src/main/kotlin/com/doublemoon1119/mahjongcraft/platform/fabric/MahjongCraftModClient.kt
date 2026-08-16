package com.doublemoon1119.mahjongcraft.platform.fabric

import com.doublemoon1119.mahjongcraft.flow.client.game.ClientDecisionTimerStateStore
import com.doublemoon1119.mahjongcraft.flow.common.game.model.PlayerDecisionPhase
import com.doublemoon1119.mahjongcraft.flow.network.dto.command.toDomain
import com.doublemoon1119.mahjongcraft.flow.network.dto.message.PlayerDecisionPhaseDto
import com.doublemoon1119.mahjongcraft.flow.network.dto.rule.NetworkDtoRegistries
import com.doublemoon1119.mahjongcraft.flow.network.dto.snapshot.toDomain
import com.doublemoon1119.mahjongcraft.platform.fabric.client.game.buildRoundResultChatMessage
import com.doublemoon1119.mahjongcraft.platform.fabric.client.model.MahjongTileModelLoadingPlugin
import com.doublemoon1119.mahjongcraft.platform.fabric.client.render.MahjongDiceEntityRenderer
import com.doublemoon1119.mahjongcraft.platform.fabric.client.render.MahjongTileEntityRenderer
import com.doublemoon1119.mahjongcraft.platform.fabric.client.render.MahjongTileItemRenderer
import com.doublemoon1119.mahjongcraft.platform.fabric.client.room.FabricOpenConfigScreenCommand
import com.doublemoon1119.mahjongcraft.platform.fabric.client.state.ClientMahjongStateStore
import com.doublemoon1119.mahjongcraft.platform.fabric.network.MahjongChannels
import com.doublemoon1119.mahjongcraft.platform.fabric.registry.ModEntities
import com.doublemoon1119.mahjongcraft.platform.fabric.registry.ModItems
import com.doublemoon1119.mahjongcraft.platform.minecraft.tile.MinecraftTileAssetRegistry
import com.doublemoon1119.mahjongcraft.platform.minecraft.tile.TileDisplayNameRegistry
import com.doublemoon1119.mahjongcraft.platform.minecraft.tile.TileEmojiRegistry
import net.fabricmc.api.ClientModInitializer
import net.fabricmc.fabric.api.client.model.loading.v1.ModelLoadingPlugin
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents
import net.fabricmc.fabric.api.client.rendering.v1.BuiltinItemRendererRegistry
import net.fabricmc.fabric.api.client.rendering.v1.EntityRendererRegistry
import net.minecraft.client.MinecraftClient
import org.koin.core.context.GlobalContext
import kotlin.uuid.Uuid

class MahjongCraftModClient : ClientModInitializer {

    override fun onInitializeClient() {
        val koin = GlobalContext.get()

        ModelLoadingPlugin.register(MahjongTileModelLoadingPlugin)
        BuiltinItemRendererRegistry.INSTANCE.register(ModItems.MAHJONG_TILE, MahjongTileItemRenderer)
        koin.get<FabricOpenConfigScreenCommand>().register()

        val json = koin.get<kotlinx.serialization.json.Json>()
        val networkRegistries = koin.get<NetworkDtoRegistries>()
        val stateStore = koin.get<ClientMahjongStateStore>()
        val decisionTimerStore = koin.get<ClientDecisionTimerStateStore>()
        val tileDisplayNames = koin.get<TileDisplayNameRegistry>()
        val tileAssetRegistry = koin.get<MinecraftTileAssetRegistry>()
        val tileEmojiRegistry = koin.get<TileEmojiRegistry>()
        MahjongChannels.roomUpdate.registerClientReceiver(json, stateStore::apply)
        MahjongChannels.gameUpdate.registerClientReceiver(json) { payload ->
            val previousSnapshot = stateStore.gameSnapshot
            stateStore.apply(payload)
            val message = buildRoundResultChatMessage(
                action = payload.action.toDomain(networkRegistries),
                previousSnapshot = previousSnapshot,
                newSnapshot = stateStore.gameSnapshot ?: return@registerClientReceiver,
                displayNameRegistry = tileDisplayNames,
                tileAssetRegistry = tileAssetRegistry,
                tileEmojiRegistry = tileEmojiRegistry,
            ) ?: return@registerClientReceiver
            MinecraftClient.getInstance().player?.sendMessage(message)
        }
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
        EntityRendererRegistry.register(ModEntities.mahjongDice, ::MahjongDiceEntityRenderer)
        EntityRendererRegistry.register(ModEntities.mahjongTile, ::MahjongTileEntityRenderer)
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
