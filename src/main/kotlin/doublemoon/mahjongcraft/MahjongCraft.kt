package doublemoon.mahjongcraft

import doublemoon.mahjongcraft.network.MahjongGamePacketHandler
import doublemoon.mahjongcraft.network.MahjongTablePacketHandler
import doublemoon.mahjongcraft.network.MahjongTileCodePacketHandler
import doublemoon.mahjongcraft.registry.*
import doublemoon.mahjongcraft.scheduler.server.ServerScheduler
import net.fabricmc.api.ModInitializer
import net.fabricmc.fabric.api.client.itemgroup.FabricItemGroupBuilder
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents
import net.minecraft.item.ItemGroup
import net.minecraft.util.Identifier
import org.apache.logging.log4j.LogManager
import org.apache.logging.log4j.Logger

const val MOD_ID = "mahjongcraft"
val itemGroup: ItemGroup =
    FabricItemGroupBuilder.build(Identifier(MOD_ID, "group")) { ItemRegistry.mahjongTile.defaultStack }
val logger: Logger = LogManager.getLogger()  //取得 logger

fun id(path: String): Identifier = Identifier(MOD_ID, path)

object MahjongCraft : ModInitializer {

    override fun onInitialize() {
        ItemRegistry.register()
        EntityTypeRegistry.register()
        BlockRegistry.register()
        BlockEntityTypeRegistry.register()
        SoundRegistry.register()
        ServerTickEvents.END_SERVER_TICK.register(ServerScheduler::tick)
        ServerLifecycleEvents.SERVER_STOPPING.register(ServerScheduler::onStopping)
        MahjongTablePacketHandler.registerServer()
        MahjongGamePacketHandler.registerServer()
        MahjongTileCodePacketHandler.registerServer()
    }
}