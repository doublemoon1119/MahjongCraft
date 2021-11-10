package doublemoon.mahjongcraft

import doublemoon.mahjongcraft.client.ModConfig
import doublemoon.mahjongcraft.client.render.*
import doublemoon.mahjongcraft.network.CustomEntitySpawnS2CPacketHandler
import doublemoon.mahjongcraft.network.MahjongGamePacketHandler
import doublemoon.mahjongcraft.network.MahjongTablePacketHandler
import doublemoon.mahjongcraft.network.MahjongTileCodePacketHandler
import doublemoon.mahjongcraft.registry.BlockEntityTypeRegistry
import doublemoon.mahjongcraft.registry.EntityTypeRegistry
import doublemoon.mahjongcraft.registry.ItemRegistry
import doublemoon.mahjongcraft.scheduler.client.ClientScheduler
import me.shedaniel.autoconfig.AutoConfig
import me.shedaniel.autoconfig.serializer.GsonConfigSerializer
import net.fabricmc.api.ClientModInitializer
import net.fabricmc.api.EnvType
import net.fabricmc.api.Environment
import net.fabricmc.fabric.api.`object`.builder.v1.client.model.FabricModelPredicateProviderRegistry
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper
import net.fabricmc.fabric.api.client.rendering.v1.BlockEntityRendererRegistry
import net.fabricmc.fabric.api.client.rendering.v1.EntityRendererRegistry
import net.minecraft.client.MinecraftClient
import net.minecraft.client.item.UnclampedModelPredicateProvider
import net.minecraft.client.option.KeyBinding
import net.minecraft.client.util.InputUtil
import net.minecraft.client.world.ClientWorld
import net.minecraft.entity.LivingEntity
import net.minecraft.item.ItemStack
import net.minecraft.util.Identifier
import org.lwjgl.glfw.GLFW

@Environment(EnvType.CLIENT)
object MahjongCraftClient : ClientModInitializer {

    lateinit var config: ModConfig
    private val configKey: KeyBinding = KeyBindingHelper.registerKeyBinding(
        KeyBinding(
            "key.$MOD_ID.open_config_gui",
            InputUtil.Type.KEYSYM,
            GLFW.GLFW_KEY_SEMICOLON,
            "key.category.$MOD_ID.main"
        )
    )

    override fun onInitializeClient() {
        logger.info("Initializing client")
        ClientTickEvents.END_CLIENT_TICK.register(this::tick)
        ClientLifecycleEvents.CLIENT_STOPPING.register(ClientScheduler::onStopping)
        //Entity Renderer
        EntityRendererRegistry.register(EntityTypeRegistry.dice, ::DiceEntityRenderer)
        EntityRendererRegistry.register(EntityTypeRegistry.seat, ::SeatEntityRenderer)
        EntityRendererRegistry.register(EntityTypeRegistry.mahjongBot, ::MahjongBotEntityRenderer)
        EntityRendererRegistry.register(EntityTypeRegistry.mahjongScoringStick, ::MahjongScoringStickEntityRenderer)
        EntityRendererRegistry.register(EntityTypeRegistry.mahjongTile, ::MahjongTileEntityRenderer)
        //BlockEntity Renderer
        BlockEntityRendererRegistry.register(BlockEntityTypeRegistry.mahjongTable, ::MahjongTableBlockEntityRenderer)
        //Model Predicate Provider
        val modelPredicateProvider = object : UnclampedModelPredicateProvider {
            override fun unclampedCall(
                stack: ItemStack,
                world: ClientWorld?,
                entity: LivingEntity?,
                seed: Int
            ): Float = stack.damage.toFloat()

            //super.call() 使用了 MathHelper.clamp(), 導致 modelPredicate 的值限制在 0f~1f 之間, 這裡覆寫把 MathHelper.clamp() 拿掉
            override fun call(
                itemStack: ItemStack,
                clientWorld: ClientWorld?,
                livingEntity: LivingEntity?,
                i: Int
            ): Float = this.unclampedCall(itemStack, clientWorld, livingEntity, i)
        }
        FabricModelPredicateProviderRegistry.register(
            ItemRegistry.mahjongTile,
            Identifier("code"),
            modelPredicateProvider
        )
        FabricModelPredicateProviderRegistry.register(
            ItemRegistry.mahjongScoringStick,
            Identifier("code"),
            modelPredicateProvider
        )
        CustomEntitySpawnS2CPacketHandler.registerClient()
        MahjongTablePacketHandler.registerClient()
        MahjongGamePacketHandler.registerClient()
        MahjongTileCodePacketHandler.registerClient()
        AutoConfig.register(ModConfig::class.java, ::GsonConfigSerializer)
        config = AutoConfig.getConfigHolder(ModConfig::class.java).config
    }

    private fun tick(client: MinecraftClient) {
        if (configKey.wasPressed()) {
            client.setScreen(AutoConfig.getConfigScreen(ModConfig::class.java, null).get())
        }
        ClientScheduler.tick(client)
    }
}