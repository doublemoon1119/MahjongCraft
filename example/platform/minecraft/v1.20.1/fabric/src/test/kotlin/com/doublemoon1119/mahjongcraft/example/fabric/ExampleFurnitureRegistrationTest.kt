package com.doublemoon1119.mahjongcraft.example.fabric

import com.doublemoon1119.mahjongcraft.example.fabric.furniture.ExampleFurnitureIds
import com.doublemoon1119.mahjongcraft.platform.fabric.api.seating.FabricSeatProviderRegistry
import com.doublemoon1119.mahjongcraft.platform.minecraft.api.seating.RegisteredSeatProvider
import net.minecraft.entity.Entity
import net.minecraft.server.world.ServerWorld
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotSame

/** 驗證範例家具在原生內容初始化前仍能建立座位 provider 描述。 */
class ExampleFurnitureRegistrationTest {
    /** provider 建立不會讀取方塊或 entity registry，且每次建立自己的 provider 實例。 */
    @Test
    fun `seat providers can be created before native registration`() {
        val firstRegistry = CapturingRegistry()
        val secondRegistry = CapturingRegistry()
        ExampleFabricMahjongExtension().registerSeatProviders(firstRegistry)
        ExampleFabricMahjongExtension().registerSeatProviders(secondRegistry)
        val first = firstRegistry.definitions
        val second = secondRegistry.definitions

        assertEquals(
            listOf(ExampleFurnitureIds.CHAIR, ExampleFurnitureIds.CUSHION),
            first.map { it.id },
        )
        assertNotSame(first[0].provider, second[0].provider)
        assertNotSame(first[1].provider, second[1].provider)
    }

    /** 只收集正式 extension 回呼的登記結果，不執行原生家具初始化。 */
    private class CapturingRegistry : FabricSeatProviderRegistry {
        /** 本次回呼登記的完整 provider 描述。 */
        val definitions = mutableListOf<RegisteredSeatProvider<ServerWorld, Entity>>()

        /** 已收集的識別碼快照。 */
        override val registrationKeys: Set<String>
            get() = definitions.map { it.id }.toSet()

        /** 測試接收者在登記期間維持開放。 */
        override val isFrozen: Boolean = false

        /** 收集提供者，交由測試核對身分與數量。 */
        override fun register(definition: RegisteredSeatProvider<ServerWorld, Entity>) {
            definitions += definition
        }
    }
}
