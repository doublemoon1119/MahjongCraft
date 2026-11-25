package com.doublemoon1119.mahjongcraft.platform.forge.loader

import com.doublemoon1119.mahjongcraft.platform.minecraft.mod.loader.ClientCommand
import com.doublemoon1119.mahjongcraft.platform.minecraft.mod.loader.ClientEvents
import com.doublemoon1119.mahjongcraft.platform.minecraft.mod.loader.WorldRenderFinishedHook
import com.mojang.blaze3d.vertex.PoseStack
import com.mojang.blaze3d.vertex.VertexFormat
import net.minecraft.client.Camera
import net.minecraft.client.KeyMapping
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.GuiGraphics
import net.minecraft.client.multiplayer.ClientLevel
import net.minecraft.client.player.LocalPlayer
import net.minecraft.client.renderer.MultiBufferSource
import net.minecraft.client.renderer.ShaderInstance
import net.minecraft.commands.CommandSourceStack
import net.minecraft.network.protocol.game.ServerboundInteractPacket
import net.minecraft.resources.ResourceLocation
import net.minecraft.server.packs.resources.ResourceManager
import net.minecraft.server.packs.resources.ResourceManagerReloadListener
import net.minecraft.world.InteractionHand
import net.minecraft.world.InteractionResult
import net.minecraft.world.InteractionResultHolder
import net.minecraft.world.entity.Entity
import net.minecraft.world.item.ItemStack
import net.minecraft.world.phys.EntityHitResult
import net.minecraftforge.client.event.ClientPlayerNetworkEvent
import net.minecraftforge.client.event.InputEvent
import net.minecraftforge.client.event.RegisterClientCommandsEvent
import net.minecraftforge.client.event.RegisterClientReloadListenersEvent
import net.minecraftforge.client.event.RegisterGuiOverlaysEvent
import net.minecraftforge.client.event.RegisterKeyMappingsEvent
import net.minecraftforge.client.event.RegisterShadersEvent
import net.minecraftforge.client.event.RenderLevelStageEvent
import net.minecraftforge.client.gui.overlay.VanillaGuiOverlay
import net.minecraftforge.common.MinecraftForge
import net.minecraftforge.event.TickEvent
import net.minecraftforge.event.entity.EntityLeaveLevelEvent
import net.minecraftforge.event.entity.player.PlayerInteractEvent
import net.minecraftforge.eventbus.api.Event
import net.minecraftforge.eventbus.api.EventPriority
import net.minecraftforge.eventbus.api.IEventBus
import java.util.concurrent.CopyOnWriteArrayList

/**
 * 以 Forge 的事件實作 [ClientEvents]。
 *
 * 按鍵、資源重載監聽、畫面疊加層與自訂著色器要在 Forge 的登記事件中加入，因此先保留，事件發生時才登記。世界畫完的時間點由共用的
 * mixin 經 [WorldRenderFinishedHook] 提供。
 */
object ForgeClientEvents : ClientEvents {
    /** 等待 Forge 按鍵登記事件的按鍵。 */
    private val pendingKeyMappings = mutableListOf<KeyMapping>()

    /** 等待 Forge 資源重載監聽登記事件的處理。 */
    private val pendingReloadListeners = mutableListOf<(ResourceManager) -> Unit>()

    /** 每次 Forge 著色器登記事件都要建立的自訂核心著色器。 */
    private val coreShaders = CopyOnWriteArrayList<CoreShader>()

    /**
     * 一個自訂核心著色器的登記內容。
     *
     * @property id 著色器的識別碼。
     * @property format 著色器接收的頂點格式。
     * @property onLoaded 收到建立好的著色器。
     */
    private class CoreShader(val id: ResourceLocation, val format: VertexFormat, val onLoaded: (ShaderInstance) -> Unit)

    /** 等待 Forge 畫面疊加層登記事件、要畫在聊天欄之前的 HUD。 */
    private val pendingHudBeforeChat = mutableListOf<(GuiGraphics) -> Unit>()

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
        modBus.addListener(EventPriority.NORMAL, false, RegisterGuiOverlaysEvent::class.java) { event ->
            pendingHudBeforeChat.forEachIndexed { index, listener ->
                event.registerBelow(VanillaGuiOverlay.CHAT_PANEL.id(), "hud_before_chat_$index") { _, graphics, _, _, _ -> listener(graphics) }
            }
            pendingHudBeforeChat.clear()
        }
        // 每次資源重新載入都會重新建立著色器，登記內容因此保留，不在登記後清除。
        modBus.addListener(EventPriority.NORMAL, false, RegisterShadersEvent::class.java) { event ->
            coreShaders.forEach { shader ->
                event.registerShader(ShaderInstance(event.resourceProvider, shader.id, shader.format), shader.onLoaded)
            }
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

    /**
     * 在按下使用鍵、原版送出任何互動之前轉交準星指向的實體。Forge 的實體互動事件在原版把互動送到伺服器之後才發出，
     * 取消它攔不住伺服器端的處理。
     *
     * 監聽器的結果不是 pass 時取消這次按鍵：結果會消耗這次操作時只送出「互動實體上某個位置」，伺服器不會因此呼叫實體
     * 本身的互動。結果要求揮手時揮手。
     */
    override fun onClientUseEntity(listener: (LocalPlayer, ClientLevel, InteractionHand, Entity) -> InteractionResult) {
        listen<InputEvent.InteractionKeyMappingTriggered> { event ->
            if (!event.isUseItem) return@listen
            val client = Minecraft.getInstance()
            val hit = client.hitResult as? EntityHitResult ?: return@listen
            val player = client.player ?: return@listen
            val level = client.level ?: return@listen
            val entity = hit.entity
            if (!level.worldBorder.isWithinBounds(entity.blockPosition())) return@listen
            val result = listener(player, level, event.hand, entity)
            if (result == InteractionResult.PASS) return@listen
            if (result.consumesAction()) {
                val location = hit.location.subtract(entity.position())
                player.connection.send(ServerboundInteractPacket.createInteractionPacket(entity, player.isShiftKeyDown, event.hand, location))
            }
            event.setSwingHand(result.shouldSwing())
            event.isCanceled = true
        }
    }

    /** Forge 在伺服器與用戶端都會發出這個事件，這裡只轉交用戶端的那一次。監聽器的結果不是 pass 時取消事件並採用該結果。 */
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

    /** Forge 不以原版 `Gui.render` 繪製遊戲畫面，改以排在聊天欄下面的疊加層提供這個時間點。 */
    override fun onHudRenderBeforeChat(listener: (GuiGraphics) -> Unit) {
        pendingHudBeforeChat += listener
    }

    /** 以畫完方塊實體的階段作為除錯線條之前的時間點。線條寫入用戶端共用的緩衝區，由原版在之後送出。 */
    override fun onWorldRenderBeforeDebug(listener: (PoseStack, MultiBufferSource, Camera) -> Unit) {
        listen<RenderLevelStageEvent> { event ->
            if (event.stage == RenderLevelStageEvent.Stage.AFTER_BLOCK_ENTITIES) {
                listener(event.poseStack, Minecraft.getInstance().renderBuffers().bufferSource(), event.camera)
            }
        }
    }

    /** Forge 的資源重載監聽不需要識別碼，[id] 不使用。 */
    override fun onRegisterCoreShader(id: ResourceLocation, format: VertexFormat, onLoaded: (ShaderInstance) -> Unit) {
        coreShaders += CoreShader(id, format, onLoaded)
    }

    override fun onClientResourceReload(id: ResourceLocation, listener: (ResourceManager) -> Unit) {
        pendingReloadListeners += listener
    }

    override fun registerKeyMapping(mapping: KeyMapping): KeyMapping = mapping.also { pendingKeyMappings += it }

    /**
     * 在 Forge 的遊戲事件匯流排登記監聽器。不接收已被取消的事件。
     *
     * @param E 事件型別。
     * @param handler 收到事件時的處理。
     */
    private inline fun <reified E : Event> listen(crossinline handler: (E) -> Unit) {
        MinecraftForge.EVENT_BUS.addListener(EventPriority.NORMAL, false, E::class.java) { handler(it) }
    }
}
