package com.doublemoon1119.mahjongcraft.platform.neoforge.loader

import com.doublemoon1119.mahjongcraft.platform.minecraft.mod.loader.ServerEvents
import com.mojang.brigadier.CommandDispatcher
import net.minecraft.commands.CommandSourceStack
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
import net.neoforged.bus.api.Event
import net.neoforged.bus.api.EventPriority
import net.neoforged.neoforge.common.NeoForge
import net.neoforged.neoforge.event.RegisterCommandsEvent
import net.neoforged.neoforge.event.entity.EntityLeaveLevelEvent
import net.neoforged.neoforge.event.entity.player.PlayerEvent
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent
import net.neoforged.neoforge.event.level.BlockEvent
import net.neoforged.neoforge.event.level.ChunkEvent
import net.neoforged.neoforge.event.server.ServerStartedEvent
import net.neoforged.neoforge.event.server.ServerStoppedEvent
import net.neoforged.neoforge.event.server.ServerStoppingEvent
import net.neoforged.neoforge.event.tick.ServerTickEvent
import java.util.concurrent.CopyOnWriteArrayList

/**
 * 以 NeoForge 的事件匯流排實作 [ServerEvents]。
 *
 * NeoForge 沒有對應事件的三種時機由 NeoForge 專用的 mixin 呼叫這裡通知：原版開始追蹤加入世界的實體之後、區塊記錄方塊實體之後、
 * 玩家破壞的方塊確實移除之後。
 */
object NeoForgeServerEvents : ServerEvents {
    /** 實體載入時的處理。 */
    private val entityLoadListeners = CopyOnWriteArrayList<(Entity, ServerLevel) -> Unit>()

    /** 方塊實體載入時的處理。 */
    private val blockEntityLoadListeners = CopyOnWriteArrayList<(BlockEntity, ServerLevel) -> Unit>()

    /** 玩家破壞的方塊移除後的處理。 */
    private val afterBreakListeners = CopyOnWriteArrayList<(ServerLevel, ServerPlayer, BlockPos, BlockState, BlockEntity?) -> Unit>()

    override fun onServerStarted(listener: (MinecraftServer) -> Unit) {
        listen<ServerStartedEvent> { listener(it.server) }
    }

    override fun onServerStopping(listener: (MinecraftServer) -> Unit) {
        listen<ServerStoppingEvent> { listener(it.server) }
    }

    override fun onServerStopped(listener: (MinecraftServer) -> Unit) {
        listen<ServerStoppedEvent> { listener(it.server) }
    }

    override fun onServerTickStart(listener: (MinecraftServer) -> Unit) {
        listen<ServerTickEvent.Pre> { listener(it.server) }
    }

    override fun onServerTickEnd(listener: (MinecraftServer) -> Unit) {
        listen<ServerTickEvent.Post> { listener(it.server) }
    }

    override fun onPlayerJoin(listener: (ServerPlayer) -> Unit) {
        listen<PlayerEvent.PlayerLoggedInEvent> { event -> (event.entity as? ServerPlayer)?.let(listener) }
    }

    override fun onPlayerDisconnect(listener: (ServerPlayer) -> Unit) {
        listen<PlayerEvent.PlayerLoggedOutEvent> { event -> (event.entity as? ServerPlayer)?.let(listener) }
    }

    override fun onChunkLoad(listener: (ServerLevel, LevelChunk) -> Unit) {
        listen<ChunkEvent.Load> { event ->
            val level = event.level as? ServerLevel ?: return@listen
            val chunk = event.chunk as? LevelChunk ?: return@listen
            listener(level, chunk)
        }
    }

    override fun onChunkUnload(listener: (ServerLevel, LevelChunk) -> Unit) {
        listen<ChunkEvent.Unload> { event ->
            val level = event.level as? ServerLevel ?: return@listen
            val chunk = event.chunk as? LevelChunk ?: return@listen
            listener(level, chunk)
        }
    }

    override fun onEntityLoad(listener: (Entity, ServerLevel) -> Unit) {
        entityLoadListeners += listener
    }

    override fun onEntityUnload(listener: (Entity, ServerLevel) -> Unit) {
        listen<EntityLeaveLevelEvent> { event -> (event.level as? ServerLevel)?.let { listener(event.entity, it) } }
    }

    override fun onBlockEntityLoad(listener: (BlockEntity, ServerLevel) -> Unit) {
        blockEntityLoadListeners += listener
    }

    override fun onStartTracking(listener: (Entity, ServerPlayer) -> Unit) {
        listen<PlayerEvent.StartTracking> { event -> (event.entity as? ServerPlayer)?.let { listener(event.target, it) } }
    }

    override fun onStopTracking(listener: (Entity, ServerPlayer) -> Unit) {
        listen<PlayerEvent.StopTracking> { event -> (event.entity as? ServerPlayer)?.let { listener(event.target, it) } }
    }

    override fun onBeforePlayerBreakBlock(listener: (ServerLevel, ServerPlayer, BlockPos, BlockState) -> Boolean) {
        listen<BlockEvent.BreakEvent> { event ->
            val level = event.level as? ServerLevel ?: return@listen
            val player = event.player as? ServerPlayer ?: return@listen
            if (!listener(level, player, event.pos, event.state)) event.isCanceled = true
        }
    }

    override fun onAfterPlayerBreakBlock(listener: (ServerLevel, ServerPlayer, BlockPos, BlockState, BlockEntity?) -> Unit) {
        afterBreakListeners += listener
    }

    override fun onRegisterCommands(listener: (CommandDispatcher<CommandSourceStack>) -> Unit) {
        listen<RegisterCommandsEvent> { listener(it.dispatcher) }
    }

    override fun onPlayerUseEntity(listener: (ServerPlayer, ServerLevel, InteractionHand, Entity) -> InteractionResult) {
        listen<PlayerInteractEvent.EntityInteract> { event ->
            if (forwardEntityInteraction(event, event.target, listener)) event.isCanceled = true
        }
        listen<PlayerInteractEvent.EntityInteractSpecific> { event ->
            if (forwardEntityInteraction(event, event.target, listener)) event.isCanceled = true
        }
    }

    /**
     * 把實體互動交給處理，處理結果不是略過時設定互動結果。
     *
     * @param event 實體互動事件。
     * @param target 互動的實體。
     * @param listener 實體互動的處理。
     * @return 處理攔下了互動、事件要取消時為 true。
     */
    private fun forwardEntityInteraction(
        event: PlayerInteractEvent,
        target: Entity,
        listener: (ServerPlayer, ServerLevel, InteractionHand, Entity) -> InteractionResult,
    ): Boolean {
        val level = event.level as? ServerLevel ?: return false
        val player = event.entity as? ServerPlayer ?: return false
        val result = listener(player, level, event.hand, target)
        if (result == InteractionResult.PASS) return false
        when (event) {
            is PlayerInteractEvent.EntityInteract -> event.cancellationResult = result
            is PlayerInteractEvent.EntityInteractSpecific -> event.cancellationResult = result
        }
        return true
    }

    /**
     * 通知實體載入。只由 NeoForge 專用的 mixin 呼叫。
     *
     * @param entity 載入的實體。
     * @param level 實體所在的世界。
     */
    @JvmStatic
    fun fireEntityLoad(entity: Entity, level: ServerLevel) {
        entityLoadListeners.forEach { it(entity, level) }
    }

    /**
     * 通知方塊實體載入。只由 NeoForge 專用的 mixin 呼叫。
     *
     * @param blockEntity 載入的方塊實體。
     * @param level 方塊實體所在的世界。
     */
    @JvmStatic
    fun fireBlockEntityLoad(blockEntity: BlockEntity, level: ServerLevel) {
        blockEntityLoadListeners.forEach { it(blockEntity, level) }
    }

    /**
     * 通知玩家破壞的方塊已移除。只由 NeoForge 專用的 mixin 呼叫。
     *
     * @param level 方塊所在的世界。
     * @param player 破壞方塊的玩家。
     * @param pos 方塊位置。
     * @param state 移除前的方塊狀態。
     * @param blockEntity 移除前的方塊實體。沒有時為 null。
     */
    @JvmStatic
    fun fireAfterPlayerBreakBlock(level: ServerLevel, player: ServerPlayer, pos: BlockPos, state: BlockState, blockEntity: BlockEntity?) {
        afterBreakListeners.forEach { it(level, player, pos, state, blockEntity) }
    }

    /**
     * 在 NeoForge 的遊戲事件匯流排上接收一種事件。
     *
     * @param E 事件型別。
     * @param handler 事件的處理。
     */
    private inline fun <reified E : Event> listen(crossinline handler: (E) -> Unit) {
        NeoForge.EVENT_BUS.addListener(EventPriority.NORMAL, false, E::class.java) { handler(it) }
    }
}
