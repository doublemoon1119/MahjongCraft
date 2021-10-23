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
import net.fabricmc.fabric.api.client.rendereregistry.v1.BlockEntityRendererRegistry
import net.fabricmc.fabric.api.client.rendereregistry.v1.EntityRendererRegistry
import net.minecraft.client.MinecraftClient
import net.minecraft.client.option.KeyBinding
import net.minecraft.client.util.InputUtil
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
        with(EntityRendererRegistry.INSTANCE) {
            register(EntityTypeRegistry.dice, ::DiceEntityRenderer)
            register(EntityTypeRegistry.seat, ::SeatEntityRenderer)
            register(EntityTypeRegistry.mahjongBot, ::MahjongBotEntityRenderer)
            register(EntityTypeRegistry.mahjongScoringStick, ::MahjongScoringStickEntityRenderer)
            register(EntityTypeRegistry.mahjongTile, ::MahjongTileEntityRenderer)
        }
        with(BlockEntityRendererRegistry.INSTANCE) {
            register(BlockEntityTypeRegistry.mahjongTable, ::MahjongTableBlockEntityRenderer)
        }
        FabricModelPredicateProviderRegistry.register(
            ItemRegistry.mahjongTile,
            Identifier("code")
        ) { itemStack, _, _ -> itemStack.damage.toFloat() }
        FabricModelPredicateProviderRegistry.register(
            ItemRegistry.mahjongScoringStick,
            Identifier("code")
        ) { itemStack, _, _ -> itemStack.damage.toFloat() }
        CustomEntitySpawnS2CPacketHandler.registerClient()
        MahjongTablePacketHandler.registerClient()
        MahjongGamePacketHandler.registerClient()
        MahjongTileCodePacketHandler.registerClient()
        AutoConfig.register(ModConfig::class.java, ::GsonConfigSerializer)
        config = AutoConfig.getConfigHolder(ModConfig::class.java).config
    }

    private fun tick(client: MinecraftClient) {
        if (configKey.wasPressed()) {
            client.openScreen(AutoConfig.getConfigScreen(ModConfig::class.java, null).get())
        }
        ClientScheduler.tick(client)
    }
}