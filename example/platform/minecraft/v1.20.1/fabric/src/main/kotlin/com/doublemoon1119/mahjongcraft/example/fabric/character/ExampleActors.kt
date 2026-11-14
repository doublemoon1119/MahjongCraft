package com.doublemoon1119.mahjongcraft.example.fabric.character

import com.doublemoon1119.mahjongcraft.example.fabric.furniture.entity.ExampleCushionEntity
import com.doublemoon1119.mahjongcraft.example.minecraft.character.ExampleCharacterIds
import net.fabricmc.fabric.api.event.player.UseEntityCallback
import net.fabricmc.fabric.api.`object`.builder.v1.entity.FabricDefaultAttributeRegistry
import net.fabricmc.fabric.api.`object`.builder.v1.entity.FabricEntityTypeBuilder
import net.minecraft.entity.EntityDimensions
import net.minecraft.entity.EntityType
import net.minecraft.entity.LivingEntity
import net.minecraft.entity.SpawnGroup
import net.minecraft.item.Item
import net.minecraft.registry.Registries
import net.minecraft.registry.Registry
import net.minecraft.server.world.ServerWorld
import net.minecraft.util.ActionResult
import net.minecraft.util.Identifier

/** 範例自己的實體及建立物品，不依賴本體 debug 內容或 DI。 */
object ExampleActors {
    /** 可保存、由放置者擁有的人偶。 */
    lateinit var actorType: EntityType<ExampleActorEntity>
        private set

    /** 不保存的暫態代理，由角色框架管理生成及移除。 */
    lateinit var proxyType: EntityType<ExampleActorEntity>
        private set

    /** 建立人偶的物品，與其他範例物品共用創造分頁。 */
    lateinit var item: Item
        private set

    /** 註冊兩種原生類型與單一建立物品，客戶端 renderer 另由客戶端入口登記。 */
    fun register() {
        actorType = Registry.register(
            Registries.ENTITY_TYPE,
            Identifier(ExampleCharacterIds.ACTOR),
            FabricEntityTypeBuilder.create(SpawnGroup.MISC, ::ExampleActorEntity)
                .dimensions(EntityDimensions.fixed(0.5f, 1.975f)).trackRangeChunks(10).trackedUpdateRate(3).build(),
        )
        proxyType = Registry.register(
            Registries.ENTITY_TYPE,
            Identifier(ExampleCharacterIds.OWNED_ACTOR),
            FabricEntityTypeBuilder.create(SpawnGroup.MISC, ::ExampleActorEntity)
                .dimensions(EntityDimensions.fixed(0.5f, 1.975f)).trackRangeChunks(10).trackedUpdateRate(3).disableSaving().build(),
        )
        FabricDefaultAttributeRegistry.register(actorType, LivingEntity.createLivingAttributes())
        FabricDefaultAttributeRegistry.register(proxyType, LivingEntity.createLivingAttributes())
        item = Registry.register(
            Registries.ITEM,
            Identifier(ExampleCharacterIds.ACTOR),
            ExampleActorItem(Item.Settings().maxCount(16)),
        )
        UseEntityCallback.EVENT.register { player, world, hand, entity, _ ->
            val stack = player.getStackInHand(hand)
            if (entity !is ExampleCushionEntity || !stack.isOf(item)) return@register ActionResult.PASS
            if (world.isClient) return@register ActionResult.SUCCESS
            if (world !is ServerWorld) return@register ActionResult.FAIL
            if (ExampleActorPlacement.placeOnCushion(
                    world = world,
                    player = player,
                    stack = stack,
                    cushion = entity,
                )
            ) {
                ActionResult.CONSUME
            } else {
                ActionResult.FAIL
            }
        }
    }
}
