package com.doublemoon1119.mahjongcraft.example.minecraft.mod.furniture

import com.doublemoon1119.mahjongcraft.example.minecraft.furniture.ExampleFurnitureIds
import com.doublemoon1119.mahjongcraft.example.minecraft.mod.furniture.entity.ExampleChairSeatEntity
import com.doublemoon1119.mahjongcraft.example.minecraft.mod.furniture.entity.ExampleCushionEntity
import com.doublemoon1119.mahjongcraft.example.minecraft.mod.registry.ExampleEntityTypes
import com.doublemoon1119.mahjongcraft.example.minecraft.mod.registry.ExampleRegistrar
import com.doublemoon1119.mahjongcraft.example.minecraft.mod.vanilla.resourceId
import net.minecraft.core.registries.Registries
import net.minecraft.world.entity.EntityType

/** 範例家具 entity type 的集中註冊點。 */
object ExampleFurnitureEntities {
    /** 方塊座位共用的隱形支撐；有乘客時隨原生資料保存，空座自行清理。 */
    lateinit var chairSeat: EntityType<ExampleChairSeatEntity>
        private set

    /** 確認範例 entity type 是否已完成原生註冊。 */
    fun isRegistered(): Boolean = ::chairSeat.isInitialized && ::cushion.isInitialized

    /** 可保存的坐墊 entity；寫入登記表後可用。 */
    lateinit var cushion: EntityType<ExampleCushionEntity>
        private set

    /**
     * 登記範例家具的兩種 entity type；兩者都不會自然生成。
     *
     * @param registrar 載入器的登記方式。
     */
    fun register(registrar: ExampleRegistrar) {
        registrar.register(
            registry = Registries.ENTITY_TYPE,
            id = resourceId(ExampleFurnitureIds.CHAIR_SEAT),
            factory = {
                ExampleEntityTypes.create(
                    factory = ::ExampleChairSeatEntity,
                    width = 0.25f,
                    height = 0.25f,
                    trackingRangeChunks = 10,
                    updateInterval = 10,
                    fireImmune = true,
                )
            },
            onRegistered = { chairSeat = it },
        )
        registrar.register(
            registry = Registries.ENTITY_TYPE,
            id = resourceId(ExampleFurnitureIds.CUSHION),
            factory = {
                ExampleEntityTypes.create(
                    factory = ::ExampleCushionEntity,
                    width = 0.75f,
                    height = 0.2f,
                    trackingRangeChunks = 10,
                    updateInterval = 10,
                    fireImmune = true,
                )
            },
            onRegistered = { cushion = it },
        )
    }
}
