package com.doublemoon1119.mahjongcraft.platform.forge.loader

import com.doublemoon1119.mahjongcraft.platform.minecraft.mod.loader.ClientCommand
import com.doublemoon1119.mahjongcraft.platform.minecraft.mod.loader.ClientEvents
import com.doublemoon1119.mahjongcraft.platform.minecraft.mod.loader.WorldRenderFinishedHook
import com.mojang.blaze3d.vertex.PoseStack
import net.minecraft.client.Camera
import net.minecraft.client.KeyMapping
import net.minecraft.client.Minecraft
import net.minecraft.client.multiplayer.ClientLevel
import net.minecraft.client.player.LocalPlayer
import net.minecraft.client.renderer.MultiBufferSource
import net.minecraft.commands.CommandSourceStack
import net.minecraft.resources.ResourceLocation
import net.minecraft.server.packs.resources.ResourceManager
import net.minecraft.server.packs.resources.ResourceManagerReloadListener
import net.minecraft.world.InteractionHand
import net.minecraft.world.InteractionResult
import net.minecraft.world.InteractionResultHolder
import net.minecraft.world.entity.Entity
import net.minecraft.world.item.ItemStack
import net.minecraftforge.client.event.ClientPlayerNetworkEvent
import net.minecraftforge.client.event.RegisterClientCommandsEvent
import net.minecraftforge.client.event.RegisterClientReloadListenersEvent
import net.minecraftforge.client.event.RegisterKeyMappingsEvent
import net.minecraftforge.client.event.RenderLevelStageEvent
import net.minecraftforge.common.MinecraftForge
import net.minecraftforge.event.TickEvent
import net.minecraftforge.event.entity.EntityLeaveLevelEvent
import net.minecraftforge.event.entity.player.PlayerInteractEvent
import net.minecraftforge.eventbus.api.Event
import net.minecraftforge.eventbus.api.EventPriority
import net.minecraftforge.eventbus.api.IEventBus

/**
 * 以 Forge 的事件實作 [ClientEvents]。
 *
 * 按鍵與資源重載監聽要在 Forge 的登記事件中加入，因此先保留，事件發生時才登記；世界畫完的時間點由共用的 mixin 經
 * [WorldRenderFinishedHook] 提供。
 */
object ForgeClientEvents : ClientEvents {
    /** 等待 Forge 按鍵登記事件的按鍵。 */
    private val pendingKeyMappings = mutableListOf<KeyMapping>()

    /** 等待 Forge 資源重載監聽登記事件的處理。 */
    private val pendingReloadListeners = mutableListOf<(ResourceManager) -> Unit>()

    /**
     * 接上本 mod 的事件匯流排，在 Forge 的登記事件中加入保留的按鍵與資源重載監聽。
     *
     * @param modBus 本 mod 的事件匯流排。
     */
    fun attach(modBus: IEventBus) {
        modBus.addListener(EventPriority.NORMAL, false, RegisterKeyMappingsEvent::class.java) { event ->
            pendingKeyMappings.forEach(event::register)
            pendingKeyMappings.clear()
        }
        modBus.addListener(EventPriority.NORMAL, false, RegisterClientReloadListenersEvent::class.java) { event ->
            pendingReloadListeners.forEach { listener -> event.registerReloadListener(ResourceManagerReloadListener { listener(it) }) }
            pendingReloadListeners.clear()
        }
    }

    override fun onRegisterClientCommand(command: ClientCommand) {
        listen<RegisterClientCommandsEvent> { it.dispatcher.register(command.build<CommandSourceStack>()) }
    }

    override fun onClientTickEnd(listener: (Minecraft) -> Unit) {
        listen<TickEvent.ClientTickEvent> { if (it.phase == TickEvent.Phase.END) listener(Minecraft.getInstance()) }
    }

    override fun onClientJoin(listener: () -> Unit) {
        listen<ClientPlayerNetworkEvent.LoggingIn> { listener() }
    }

    override fun onClientDisconnect(listener: () -> Unit) {
        listen<ClientPlayerNetworkEvent.LoggingOut> { listener() }
    }

    override fun onClientEntityUnload(listener: (Entity, ClientLevel) -> Unit) {
        listen<EntityLeaveLevelEvent> { event -> (event.level as? ClientLevel)?.let { listener(event.entity, it) } }
    }

    /** Forge 在伺服器與用戶端都會發出這個事件，這裡只轉交用戶端的那一次；監聽器的結果不是 pass 時取消事件並採用該結果。 */
    override fun onClientUseEntity(listener: (LocalPlayer, ClientLevel, InteractionHand, Entity) -> InteractionResult) {
        listen<PlayerInteractEvent.EntityInteract> { event ->
            val level = event.level as? ClientLevel ?: return@listen
            val player = event.entity as? LocalPlayer ?: return@listen
            val result = listener(player, level, event.hand, event.target)
            if (result != InteractionResult.PASS) {
                event.cancellationResult = result
                event.isCanceled = true
            }
        }
    }

    /** Forge 在伺服器與用戶端都會發出這個事件，這裡只轉交用戶端的那一次；監聽器的結果不是 pass 時取消事件並採用該結果。 */
    override fun onClientUseItem(listener: (LocalPlayer, ClientLevel, InteractionHand) -> InteractionResultHolder<ItemStack>) {
        listen<PlayerInteractEvent.RightClickItem> { event ->
            val level = event.level as? ClientLevel ?: return@listen
            val player = event.entity as? LocalPlayer ?: return@listen
            val result = listener(player, level, event.hand).result
            if (result != InteractionResult.PASS) {
                event.cancellationResult = result
                event.isCanceled = true
            }
        }
    }

    /** 以畫完天空的階段作為世界開始繪製的時間點，這時世界中的物件都還沒畫。 */
    override fun onWorldRenderStart(listener: () -> Unit) {
        listen<RenderLevelStageEvent> { if (it.stage == RenderLevelStageEvent.Stage.AFTER_SKY) listener() }
    }

    override fun onWorldRenderFinished(listener: () -> Unit) {
        WorldRenderFinishedHook.addListener(listener)
    }

    /** 以畫完方塊實體的階段作為除錯線條之前的時間點；線條寫入用戶端共用的緩衝區，由原版在之後送出。 */
    override fun onWorldRenderBeforeDebug(listener: (PoseStack, MultiBufferSource, Camera) -> Unit) {
        listen<RenderLevelStageEvent> { event ->
            if (event.stage == RenderLevelStageEvent.Stage.AFTER_BLOCK_ENTITIES) {
                listener(event.poseStack, Minecraft.getInstance().renderBuffers().bufferSource(), event.camera)
            }
        }
    }

    /** Forge 的資源重載監聽不需要識別碼，[id] 不使用。 */
    override fun onClientResourceReload(id: ResourceLocation, listener: (ResourceManager) -> Unit) {
        pendingReloadListeners += listener
    }

    override fun registerKeyMapping(mapping: KeyMapping): KeyMapping = mapping.also { pendingKeyMappings += it }

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
