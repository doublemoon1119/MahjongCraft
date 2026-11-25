package com.doublemoon1119.mahjongcraft.example.fabric

import com.doublemoon1119.mahjongcraft.example.minecraft.mod.client.ExampleRenderers
import com.doublemoon1119.mahjongcraft.example.minecraft.mod.registry.ExampleRendererRegistrar
import com.doublemoon1119.mahjongcraft.platform.minecraft.mod.logging.mahjongCraftLogger
import net.fabricmc.api.ClientModInitializer
import net.fabricmc.fabric.api.client.rendering.v1.EntityRendererRegistry
import net.minecraft.client.renderer.entity.EntityRendererProvider
import net.minecraft.world.entity.Entity
import net.minecraft.world.entity.EntityType

/** 只在客戶端載入的範例入口，與專用伺服器的啟動流程分開。 */
class ExampleModClient : ClientModInitializer {
    /** 記錄客戶端入口的載入狀態。 */
    private val logger = mahjongCraftLogger(ExampleModClient::class)

    /** 在客戶端以 Fabric 的方式登記範例 renderer，不從伺服器入口載入繪圖類別。 */
    override fun onInitializeClient() {
        ExampleRenderers.register(FabricRendererRegistrar)
        logger.info("Example client initialized")
    }

    /** 呼叫當下就交給 Fabric renderer 登記表的 [ExampleRendererRegistrar]。 */
    private object FabricRendererRegistrar : ExampleRendererRegistrar {
        override fun <T : Entity> register(type: EntityType<out T>, provider: EntityRendererProvider<T>) {
            EntityRendererRegistry.register(type, provider)
        }
    }
}
