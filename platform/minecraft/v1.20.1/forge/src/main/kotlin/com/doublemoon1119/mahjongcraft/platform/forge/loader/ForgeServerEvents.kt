package com.doublemoon1119.mahjongcraft.platform.forge.loader

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
import net.minecraftforge.common.MinecraftForge
import net.minecraftforge.event.RegisterCommandsEvent
import net.minecraftforge.event.TickEvent
import net.minecraftforge.event.entity.EntityLeaveLevelEvent
import net.minecraftforge.event.entity.player.PlayerEvent
import net.minecraftforge.event.entity.player.PlayerInteractEvent
import net.minecraftforge.event.level.BlockEvent
import net.minecraftforge.event.level.ChunkEvent
import net.minecraftforge.event.server.ServerStartedEvent
import net.minecraftforge.event.server.ServerStoppedEvent
import net.minecraftforge.event.server.ServerStoppingEvent
import net.minecraftforge.eventbus.api.Event
import net.minecraftforge.eventbus.api.EventPriority
import java.util.concurrent.CopyOnWriteArrayList

/**
 * 以 Forge 的事件實作 [ServerEvents]。
 *
 * Forge 沒有「實體加入世界之後」、「方塊實體載入」與「玩家破壞方塊之後」的事件，由 Forge 專用的 mixin 補上：原版開始
 * 追蹤實體之後呼叫 [fireEntityLoad]，區塊記錄方塊實體之後呼叫 [fireBlockEntityLoad]，玩家破壞的方塊確實被移除之後呼叫
 * [fireAfterPlayerBreakBlock]。
 */
object ForgeServerEvents : ServerEvents {
    /** 實體加入世界之後的監聽器。 */
    private val entityLoadListeners = CopyOnWriteArrayList<(Entity, ServerLevel) -> Unit>()

    /** 方塊實體載入的監聽器。 */
    private val blockEntityLoadListeners = CopyOnWriteArrayList<(BlockEntity, ServerLevel) -> Unit>()

    /** 玩家破壞方塊之後的監聽器。 */
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
        listen<TickEvent.ServerTickEvent> { if (it.phase == TickEvent.Phase.START) listener(it.server) }
    }

    override fun onServerTickEnd(listener: (MinecraftServer) -> Unit) {
        listen<TickEvent.ServerTickEvent> { if (it.phase == TickEvent.Phase.END) listener(it.server) }
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

    /** 監聽器回傳 false 時取消事件；已被取消的破壞不會再詢問。 */
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

    /** 只轉交伺服器端那一次；監聽器回傳 [InteractionResult.PASS] 以外的結果時取消事件並採用該結果。 */
    override fun onPlayerUseEntity(listener: (ServerPlayer, ServerLevel, InteractionHand, Entity) -> InteractionResult) {
        listen<PlayerInteractEvent.EntityInteract> { event ->
            val level = event.level as? ServerLevel ?: return@listen
            val player = event.entity as? ServerPlayer ?: return@listen
            val result = listener(player, level, event.hand, event.target)
            if (result != InteractionResult.PASS) {
                event.cancellationResult = result
                event.isCanceled = true
            }
        }
    }

    /**
     * 通知實體已加入伺服器世界；只由 mixin 在原版開始追蹤實體之後呼叫。
     *
     * @param entity 加入的實體。
     * @param level 實體所在的世界。
     */
    @JvmStatic
    fun fireEntityLoad(entity: Entity, level: ServerLevel) {
        entityLoadListeners.forEach { it(entity, level) }
    }

    /**
     * 通知方塊實體已放進伺服器世界的區塊；只由 mixin 在區塊記錄方塊實體之後呼叫。
     *
     * @param blockEntity 載入的方塊實體。
     * @param level 方塊實體所在的世界。
     */
    @JvmStatic
    fun fireBlockEntityLoad(blockEntity: BlockEntity, level: ServerLevel) {
        blockEntityLoadListeners.forEach { it(blockEntity, level) }
    }

    /**
     * 通知玩家已破壞方塊；只由 mixin 在方塊確實被移除之後呼叫。
     *
     * @param level 方塊所在的世界。
     * @param player 破壞方塊的玩家。
     * @param pos 方塊位置。
     * @param state 破壞前的方塊狀態。
     * @param blockEntity 破壞前的方塊實體；沒有時為 null。
     */
    @JvmStatic
    fun fireAfterPlayerBreakBlock(level: ServerLevel, player: ServerPlayer, pos: BlockPos, state: BlockState, blockEntity: BlockEntity?) {
        afterBreakListeners.forEach { it(level, player, pos, state, blockEntity) }
    }

    /**
     * 在 Forge 的遊戲事件匯流排登記監聽器；不接收已被取消的事件。
     *
     * @param E 事件型別。
     * @param handler 收到事件時的處理。
     */
    private inline fun <reified E : Event> listen(crossinline handler: (E) -> Unit) {
        MinecraftForge.EVENT_BUS.addListener(EventPriority.NORMAL, false, E::class.java) { handler(it) }
    }
}
