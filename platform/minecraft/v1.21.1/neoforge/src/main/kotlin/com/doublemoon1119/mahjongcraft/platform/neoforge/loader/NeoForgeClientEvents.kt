package com.doublemoon1119.mahjongcraft.platform.neoforge.loader

import com.doublemoon1119.mahjongcraft.platform.minecraft.mod.loader.ClientCommand
import com.doublemoon1119.mahjongcraft.platform.minecraft.mod.loader.ClientEvents
import com.doublemoon1119.mahjongcraft.platform.minecraft.mod.loader.HudBeforeChatHook
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
import net.neoforged.bus.api.Event
import net.neoforged.bus.api.EventPriority
import net.neoforged.bus.api.IEventBus
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent
import net.neoforged.neoforge.client.event.ClientTickEvent
import net.neoforged.neoforge.client.event.InputEvent
import net.neoforged.neoforge.client.event.RegisterClientCommandsEvent
import net.neoforged.neoforge.client.event.RegisterClientReloadListenersEvent
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent
import net.neoforged.neoforge.client.event.RegisterShadersEvent
import net.neoforged.neoforge.client.event.RenderLevelStageEvent
import net.neoforged.neoforge.common.NeoForge
import net.neoforged.neoforge.event.entity.EntityLeaveLevelEvent
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent
import java.util.concurrent.CopyOnWriteArrayList

/**
 * 以 NeoForge 的事件匯流排實作 [ClientEvents]。
 *
 * - 按鍵、著色器與重載監聽在本 mod 的事件匯流排上登記，交出的項目先保留到對應事件。
 * - 聊天欄之前的畫面與世界繪製結束兩個時間點沒有 NeoForge 事件，由共用的 mixin 通知。
 */
object NeoForgeClientEvents : ClientEvents {
    /** 等待按鍵登記事件的按鍵。 */
    private val pendingKeyMappings = mutableListOf<KeyMapping>()

    /** 等待重載監聽登記事件的監聽。 */
    private val pendingReloadListeners = mutableListOf<(ResourceManager) -> Unit>()

    /** 每次資源重載都要重新建立的核心著色器。 */
    private val coreShaders = CopyOnWriteArrayList<CoreShader>()

    /**
     * 一個核心著色器的登記。
     *
     * @property id 著色器 ID。
     * @property format 頂點格式。
     * @property onLoaded 著色器建立後的處理。
     */
    private class CoreShader(val id: ResourceLocation, val format: VertexFormat, val onLoaded: (ShaderInstance) -> Unit)

    /**
     * 接上本 mod 的事件匯流排。
     *
     * @param modBus 本 mod 的事件匯流排。
     */
    fun attach(modBus: IEventBus) {
        modBus.addListener(EventPriority.NORMAL, false, RegisterKeyMappingsEvent::class.java) { event ->
            pendingKeyMappings.forEach(event::register)
            pendingKeyMappings.clear()
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
        listen<ClientTickEvent.Post> { listener(Minecraft.getInstance()) }
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
     * 原版在 NeoForge 發出用戶端實體互動事件之前，就把右鍵點實體送到伺服器，取消事件擋不住伺服器處理。
     *
     * 因此在按下使用鍵、原版送出任何東西之前就交給監聽，與 Fabric 相同。監聽處理了點擊時，只送出點在實體某個位置上的互動，
     * 伺服器不會執行實體本身的互動。
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

    override fun onWorldRenderStart(listener: () -> Unit) {
        listen<RenderLevelStageEvent> { if (it.stage == RenderLevelStageEvent.Stage.AFTER_SKY) listener() }
    }

    /** NeoForge 沒有這個時間點的事件，由共用的 mixin 經 [WorldRenderFinishedHook] 通知。 */
    override fun onWorldRenderFinished(listener: () -> Unit) {
        WorldRenderFinishedHook.addListener(listener)
    }

    /** NeoForge 的聊天欄圖層仍由原版畫聊天欄的方法繪製，由共用的 mixin 經 [HudBeforeChatHook] 通知。 */
    override fun onHudRenderBeforeChat(listener: (GuiGraphics) -> Unit) {
        HudBeforeChatHook.addListener(listener)
    }

    override fun onWorldRenderBeforeDebug(listener: (PoseStack, MultiBufferSource, Camera) -> Unit) {
        listen<RenderLevelStageEvent> { event ->
            if (event.stage == RenderLevelStageEvent.Stage.AFTER_BLOCK_ENTITIES) {
                listener(event.poseStack, Minecraft.getInstance().renderBuffers().bufferSource(), event.camera)
            }
        }
    }

    override fun onRegisterCoreShader(id: ResourceLocation, format: VertexFormat, onLoaded: (ShaderInstance) -> Unit) {
        coreShaders += CoreShader(id, format, onLoaded)
    }

    override fun onClientResourceReload(id: ResourceLocation, listener: (ResourceManager) -> Unit) {
        pendingReloadListeners += listener
    }

    override fun registerKeyMapping(mapping: KeyMapping): KeyMapping = mapping.also { pendingKeyMappings += it }

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
