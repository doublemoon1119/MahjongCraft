package com.doublemoon1119.mahjongcraft.example.fabric.character

import com.doublemoon1119.mahjongcraft.example.fabric.furniture.entity.ExampleCushionEntity
import com.doublemoon1119.mahjongcraft.example.minecraft.character.ExampleCharacterIds
import net.fabricmc.fabric.api.event.player.UseEntityCallback
import net.fabricmc.fabric.api.`object`.builder.v1.entity.FabricDefaultAttributeRegistry
import net.fabricmc.fabric.api.`object`.builder.v1.entity.FabricEntityTypeBuilder
import net.minecraft.core.Registry
import net.minecraft.core.registries.BuiltInRegistries
import net.minecraft.resources.ResourceLocation
import net.minecraft.server.level.ServerLevel
import net.minecraft.world.InteractionResult
import net.minecraft.world.entity.EntityDimensions
import net.minecraft.world.entity.EntityType
import net.minecraft.world.entity.LivingEntity
import net.minecraft.world.entity.MobCategory
import net.minecraft.world.item.Item

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
            BuiltInRegistries.ENTITY_TYPE,
            ResourceLocation(ExampleCharacterIds.ACTOR),
            FabricEntityTypeBuilder.create(MobCategory.MISC, ::ExampleActorEntity)
                .dimensions(EntityDimensions.fixed(0.5f, 1.975f)).trackRangeChunks(10).trackedUpdateRate(3).build(),
        )
        proxyType = Registry.register(
            BuiltInRegistries.ENTITY_TYPE,
            ResourceLocation(ExampleCharacterIds.OWNED_ACTOR),
            FabricEntityTypeBuilder.create(MobCategory.MISC, ::ExampleActorEntity)
                .dimensions(EntityDimensions.fixed(0.5f, 1.975f)).trackRangeChunks(10).trackedUpdateRate(3).disableSaving().build(),
        )
        FabricDefaultAttributeRegistry.register(actorType, LivingEntity.createLivingAttributes())
        FabricDefaultAttributeRegistry.register(proxyType, LivingEntity.createLivingAttributes())
        item = Registry.register(
            BuiltInRegistries.ITEM,
            ResourceLocation(ExampleCharacterIds.ACTOR),
            ExampleActorItem(Item.Properties().stacksTo(16)),
        )
        UseEntityCallback.EVENT.register { player, world, hand, entity, _ ->
            val stack = player.getItemInHand(hand)
            if (entity !is ExampleCushionEntity || !stack.`is`(item)) return@register InteractionResult.PASS
            if (world.isClientSide) return@register InteractionResult.SUCCESS
            if (world !is ServerLevel) return@register InteractionResult.FAIL
            if (ExampleActorPlacement.placeOnCushion(
                    world = world,
                    player = player,
                    stack = stack,
                    cushion = entity,
                )
            ) {
                InteractionResult.CONSUME
            } else {
                InteractionResult.FAIL
            }
        }
    }
}
