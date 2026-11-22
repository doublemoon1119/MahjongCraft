package com.doublemoon1119.mahjongcraft.platform.fabric.loader

import com.doublemoon1119.mahjongcraft.platform.minecraft.mod.loader.ServerEvents
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerBlockEntityEvents
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerChunkEvents
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerEntityEvents
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents
import net.fabricmc.fabric.api.event.player.PlayerBlockBreakEvents
import net.fabricmc.fabric.api.event.player.UseEntityCallback
import net.fabricmc.fabric.api.networking.v1.EntityTrackingEvents
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents
import net.minecraft.core.BlockPos
import net.minecraft.server.MinecraftServer
import net.minecraft.server.level.ServerLevel
import net.minecraft.server.level.ServerPlayer
import net.minecraft.world.InteractionHand
import net.minecraft.world.InteractionResult
import net.minecraft.world.entity.Entity
import net.minecraft.world.level.block.entity.BlockEntity
import net.minecraft.world.level.block.state.BlockState
import net.minecraft.world.level.chunk.LevelChunk

/** 以 Fabric API 的事件實作 [ServerEvents]。 */
object FabricServerEvents : ServerEvents {
    override fun onServerStarted(listener: (MinecraftServer) -> Unit) {
        ServerLifecycleEvents.SERVER_STARTED.register(listener)
    }

    override fun onServerStopping(listener: (MinecraftServer) -> Unit) {
        ServerLifecycleEvents.SERVER_STOPPING.register(listener)
    }

    override fun onServerStopped(listener: (MinecraftServer) -> Unit) {
        ServerLifecycleEvents.SERVER_STOPPED.register(listener)
    }

    override fun onServerTickEnd(listener: (MinecraftServer) -> Unit) {
        ServerTickEvents.END_SERVER_TICK.register(listener)
    }

    override fun onPlayerJoin(listener: (ServerPlayer) -> Unit) {
        ServerPlayConnectionEvents.JOIN.register { handler, _, _ -> listener(handler.player) }
    }

    override fun onPlayerDisconnect(listener: (ServerPlayer) -> Unit) {
        ServerPlayConnectionEvents.DISCONNECT.register { handler, _ -> listener(handler.player) }
    }

    override fun onChunkLoad(listener: (ServerLevel, LevelChunk) -> Unit) {
        ServerChunkEvents.CHUNK_LOAD.register(listener)
    }

    override fun onChunkUnload(listener: (ServerLevel, LevelChunk) -> Unit) {
        ServerChunkEvents.CHUNK_UNLOAD.register(listener)
    }

    override fun onEntityLoad(listener: (Entity, ServerLevel) -> Unit) {
        ServerEntityEvents.ENTITY_LOAD.register(listener)
    }

    override fun onEntityUnload(listener: (Entity, ServerLevel) -> Unit) {
        ServerEntityEvents.ENTITY_UNLOAD.register(listener)
    }

    override fun onBlockEntityLoad(listener: (BlockEntity, ServerLevel) -> Unit) {
        ServerBlockEntityEvents.BLOCK_ENTITY_LOAD.register(listener)
    }

    override fun onStartTracking(listener: (Entity, ServerPlayer) -> Unit) {
        EntityTrackingEvents.START_TRACKING.register(listener)
    }

    override fun onStopTracking(listener: (Entity, ServerPlayer) -> Unit) {
        EntityTrackingEvents.STOP_TRACKING.register(listener)
    }

    override fun onBeforePlayerBreakBlock(listener: (ServerLevel, ServerPlayer, BlockPos, BlockState) -> Boolean) {
        PlayerBlockBreakEvents.BEFORE.register { world, player, pos, state, _ ->
            if (world is ServerLevel && player is ServerPlayer) listener(world, player, pos, state) else true
        }
    }

    override fun onAfterPlayerBreakBlock(listener: (ServerLevel, ServerPlayer, BlockPos, BlockState, BlockEntity?) -> Unit) {
        PlayerBlockBreakEvents.AFTER.register { world, player, pos, state, blockEntity ->
            if (world is ServerLevel && player is ServerPlayer) listener(world, player, pos, state, blockEntity)
        }
    }

    override fun onPlayerUseEntity(listener: (ServerPlayer, ServerLevel, InteractionHand, Entity) -> InteractionResult) {
        UseEntityCallback.EVENT.register { player, world, hand, entity, _ ->
            if (world is ServerLevel && player is ServerPlayer) listener(player, world, hand, entity) else InteractionResult.PASS
        }
    }
}
