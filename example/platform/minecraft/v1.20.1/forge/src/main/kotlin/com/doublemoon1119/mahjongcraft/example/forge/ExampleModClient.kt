package com.doublemoon1119.mahjongcraft.example.forge

import com.doublemoon1119.mahjongcraft.example.minecraft.mod.client.ExampleRenderers
import com.doublemoon1119.mahjongcraft.example.minecraft.mod.registry.ExampleRendererRegistrar
import com.doublemoon1119.mahjongcraft.platform.minecraft.mod.logging.mahjongCraftLogger
import net.minecraft.client.renderer.entity.EntityRendererProvider
import net.minecraft.world.entity.Entity
import net.minecraft.world.entity.EntityType
import net.minecraftforge.client.event.EntityRenderersEvent
import net.minecraftforge.eventbus.api.EventPriority
import net.minecraftforge.eventbus.api.IEventBus

/** 只在客戶端執行的範例初始化，由 [ExampleMod] 在客戶端呼叫，專用伺服器不載入繪圖類別。 */
internal object ExampleModClient {
    /** 記錄客戶端初始化的載入狀態。 */
    private val logger = mahjongCraftLogger(ExampleModClient::class)

    /**
     * 在 Forge 的繪製器登記事件中登記範例 renderer。
     *
     * @param modBus 範例 mod 的事件匯流排。
     */
    fun initialize(modBus: IEventBus) {
        modBus.addListener(EventPriority.NORMAL, false, EntityRenderersEvent.RegisterRenderers::class.java) { event ->
            ExampleRenderers.register(EventRendererRegistrar(event))
        }
        logger.info("Example client initialized")
    }

    /**
     * 交給 Forge 繪製器登記事件的 [ExampleRendererRegistrar]。
     *
     * @property event 正在進行的繪製器登記事件。
     */
    private class EventRendererRegistrar(private val event: EntityRenderersEvent.RegisterRenderers) : ExampleRendererRegistrar {
        override fun <T : Entity> register(type: EntityType<out T>, provider: EntityRendererProvider<T>) {
            event.registerEntityRenderer(type, provider)
        }
    }
}
