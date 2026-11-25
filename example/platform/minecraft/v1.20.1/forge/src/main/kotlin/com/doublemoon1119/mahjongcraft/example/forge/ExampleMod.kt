package com.doublemoon1119.mahjongcraft.example.forge

import com.doublemoon1119.mahjongcraft.example.ExampleExtensionIds
import com.doublemoon1119.mahjongcraft.example.minecraft.mod.character.ExampleActors
import com.doublemoon1119.mahjongcraft.example.minecraft.mod.event.ExampleGameEventChat
import com.doublemoon1119.mahjongcraft.example.minecraft.mod.furniture.ExampleFurniture
import com.doublemoon1119.mahjongcraft.example.minecraft.mod.registry.ExampleItemGroups
import com.doublemoon1119.mahjongcraft.example.minecraft.mod.table.ExampleHandRevealChipEntities
import com.doublemoon1119.mahjongcraft.platform.minecraft.mod.logging.mahjongCraftLogger
import net.minecraft.world.InteractionResult
import net.minecraftforge.common.MinecraftForge
import net.minecraftforge.event.entity.EntityAttributeCreationEvent
import net.minecraftforge.event.entity.player.PlayerInteractEvent
import net.minecraftforge.eventbus.api.EventPriority
import net.minecraftforge.fml.common.Mod
import net.minecraftforge.fml.loading.FMLEnvironment
import thedarkcolour.kotlinforforge.forge.MOD_BUS

/**
 * 範例 mod 的 Forge 啟動入口，不建立第二份 MahjongCraft 依賴注入容器。
 *
 * 範例內容在共用程式中列出，這裡交給 Forge 的登記事件寫入，並接上生物屬性與互動事件。擴充由 `mods.toml` 宣告，交給本體發現。
 */
@Mod(ExampleExtensionIds.MOD_ID)
object ExampleMod {
    /** 範例 mod 載入狀態的記錄器。 */
    private val logger = mahjongCraftLogger(ExampleMod::class)

    init {
        val registrar = ForgeExampleRegistrar(ExampleExtensionIds.MOD_ID)
        ExampleFurniture.register(registrar)
        ExampleActors.register(registrar)
        ExampleHandRevealChipEntities.register(registrar)
        ExampleItemGroups.register(registrar)
        registrar.attach(MOD_BUS)
        MOD_BUS.addListener(EventPriority.NORMAL, false, EntityAttributeCreationEvent::class.java) { event ->
            ExampleActors.defaultAttributes().forEach { (type, attributes) -> event.put(type, attributes) }
        }
        MinecraftForge.EVENT_BUS.addListener(EventPriority.NORMAL, false, PlayerInteractEvent.EntityInteract::class.java, ::useOnCushion)
        ExampleGameEventChat.register()
        if (FMLEnvironment.dist.isClient) ExampleModClient.initialize(MOD_BUS)
        logger.info("Example mod loaded: {}", ExampleExtensionIds.MOD_ID)
    }

    /**
     * 拿人偶物品對坐墊按右鍵時讓人偶坐上坐墊，並取消原本的互動。其他互動不處理。
     *
     * @param event 玩家對實體的互動。
     */
    private fun useOnCushion(event: PlayerInteractEvent.EntityInteract) {
        val result = ExampleActors.useOnCushion(event.entity, event.level, event.hand, event.target)
        if (result == InteractionResult.PASS) return
        event.cancellationResult = result
        event.isCanceled = true
    }
}
