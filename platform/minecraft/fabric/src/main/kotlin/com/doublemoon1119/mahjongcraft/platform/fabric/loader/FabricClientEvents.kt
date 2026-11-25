package com.doublemoon1119.mahjongcraft.platform.fabric.loader

import com.doublemoon1119.mahjongcraft.platform.minecraft.mod.loader.ClientCommand
import com.doublemoon1119.mahjongcraft.platform.minecraft.mod.loader.ClientEvents
import com.doublemoon1119.mahjongcraft.platform.minecraft.mod.loader.HudBeforeChatHook
import com.doublemoon1119.mahjongcraft.platform.minecraft.mod.loader.WorldRenderFinishedHook
import com.mojang.blaze3d.vertex.PoseStack
import com.mojang.blaze3d.vertex.VertexFormat
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientEntityEvents
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents
import net.fabricmc.fabric.api.client.rendering.v1.CoreShaderRegistrationCallback
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderEvents
import net.fabricmc.fabric.api.event.player.UseEntityCallback
import net.fabricmc.fabric.api.event.player.UseItemCallback
import net.fabricmc.fabric.api.resource.ResourceManagerHelper
import net.fabricmc.fabric.api.resource.SimpleSynchronousResourceReloadListener
import net.minecraft.client.Camera
import net.minecraft.client.KeyMapping
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.GuiGraphics
import net.minecraft.client.multiplayer.ClientLevel
import net.minecraft.client.player.LocalPlayer
import net.minecraft.client.renderer.MultiBufferSource
import net.minecraft.client.renderer.ShaderInstance
import net.minecraft.resources.ResourceLocation
import net.minecraft.server.packs.PackType
import net.minecraft.server.packs.resources.ResourceManager
import net.minecraft.world.InteractionHand
import net.minecraft.world.InteractionResult
import net.minecraft.world.InteractionResultHolder
import net.minecraft.world.entity.Entity
import net.minecraft.world.item.ItemStack

/** 以 Fabric API 的用戶端事件實作 [ClientEvents]。 */
object FabricClientEvents : ClientEvents {
    override fun onRegisterClientCommand(command: ClientCommand) {
        ClientCommandRegistrationCallback.EVENT.register { dispatcher, _ ->
            dispatcher.register(command.build<FabricClientCommandSource>())
        }
    }

    override fun onClientTickEnd(listener: (Minecraft) -> Unit) {
        ClientTickEvents.END_CLIENT_TICK.register(listener)
    }

    override fun onClientJoin(listener: () -> Unit) {
        ClientPlayConnectionEvents.JOIN.register { _, _, _ -> listener() }
    }

    override fun onClientDisconnect(listener: () -> Unit) {
        ClientPlayConnectionEvents.DISCONNECT.register { _, _ -> listener() }
    }

    override fun onClientEntityUnload(listener: (Entity, ClientLevel) -> Unit) {
        ClientEntityEvents.ENTITY_UNLOAD.register(listener)
    }

    /** Fabric 在伺服器與用戶端都會觸發這個事件，這裡只轉交用戶端的那一次。 */
    override fun onClientUseEntity(listener: (LocalPlayer, ClientLevel, InteractionHand, Entity) -> InteractionResult) {
        UseEntityCallback.EVENT.register { player, world, hand, entity, _ ->
            if (player is LocalPlayer && world is ClientLevel) listener(player, world, hand, entity) else InteractionResult.PASS
        }
    }

    /** Fabric 在伺服器與用戶端都會觸發這個事件，這裡只轉交用戶端的那一次。 */
    override fun onClientUseItem(listener: (LocalPlayer, ClientLevel, InteractionHand) -> InteractionResultHolder<ItemStack>) {
        UseItemCallback.EVENT.register { player, world, hand ->
            if (player is LocalPlayer && world is ClientLevel) listener(player, world, hand) else InteractionResultHolder.pass(player.getItemInHand(hand))
        }
    }

    override fun onWorldRenderStart(listener: () -> Unit) {
        WorldRenderEvents.START.register { listener() }
    }

    /** Fabric API 沒有這個時間點的事件，由共用的 mixin 經 [WorldRenderFinishedHook] 通知。 */
    override fun onWorldRenderFinished(listener: () -> Unit) {
        WorldRenderFinishedHook.addListener(listener)
    }

    /** Fabric 以原版 `Gui.render` 繪製遊戲畫面，由共用的 mixin 經 [HudBeforeChatHook] 通知。 */
    override fun onHudRenderBeforeChat(listener: (GuiGraphics) -> Unit) {
        HudBeforeChatHook.addListener(listener)
    }

    override fun onWorldRenderBeforeDebug(listener: (PoseStack, MultiBufferSource, Camera) -> Unit) {
        WorldRenderEvents.BEFORE_DEBUG_RENDER.register { context ->
            val consumers = context.consumers() ?: return@register
            val matrices = context.matrixStack() ?: return@register
            listener(matrices, consumers, context.camera())
        }
    }

    override fun onRegisterCoreShader(id: ResourceLocation, format: VertexFormat, onLoaded: (ShaderInstance) -> Unit) {
        CoreShaderRegistrationCallback.EVENT.register { context -> context.register(id, format, onLoaded) }
    }

    override fun onClientResourceReload(id: ResourceLocation, listener: (ResourceManager) -> Unit) {
        ResourceManagerHelper.get(PackType.CLIENT_RESOURCES).registerReloadListener(
            object : SimpleSynchronousResourceReloadListener {
                override fun getFabricId(): ResourceLocation = id

                override fun onResourceManagerReload(manager: ResourceManager) {
                    listener(manager)
                }
            },
        )
    }

    override fun registerKeyMapping(mapping: KeyMapping): KeyMapping = KeyBindingHelper.registerKeyBinding(mapping)
}
