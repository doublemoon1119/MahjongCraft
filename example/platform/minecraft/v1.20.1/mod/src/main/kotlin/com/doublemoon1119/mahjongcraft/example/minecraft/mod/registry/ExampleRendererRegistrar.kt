package com.doublemoon1119.mahjongcraft.example.minecraft.mod.registry

import net.minecraft.client.renderer.entity.EntityRendererProvider
import net.minecraft.world.entity.Entity
import net.minecraft.world.entity.EntityType

/**
 * 範例實體繪製器的登記方式；每個載入器的範例模組各自提供。
 *
 * 與 [ExampleRegistrar] 相同，這個介面屬於範例，不是 MahjongCraft 的 API；只支援一個載入器的 mod 直接用該載入器的
 * 繪製器登記方式即可。
 */
interface ExampleRendererRegistrar {
    /**
     * 登記實體的繪製器。
     *
     * @param T 實體型別。
     * @param type 實體類型。
     * @param provider 建立繪製器的方式。
     */
    fun <T : Entity> register(type: EntityType<out T>, provider: EntityRendererProvider<T>)
}
