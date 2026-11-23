package com.doublemoon1119.mahjongcraft.example.minecraft.mod.character

import com.doublemoon1119.mahjongcraft.example.minecraft.character.ExampleCharacterIds
import com.doublemoon1119.mahjongcraft.example.minecraft.mod.furniture.entity.ExampleCushionEntity
import com.doublemoon1119.mahjongcraft.example.minecraft.mod.registry.ExampleEntityTypes
import com.doublemoon1119.mahjongcraft.example.minecraft.mod.registry.ExampleRegistrar
import net.minecraft.core.registries.Registries
import net.minecraft.resources.ResourceLocation
import net.minecraft.server.level.ServerLevel
import net.minecraft.world.InteractionHand
import net.minecraft.world.InteractionResult
import net.minecraft.world.entity.Entity
import net.minecraft.world.entity.EntityType
import net.minecraft.world.entity.LivingEntity
import net.minecraft.world.entity.ai.attributes.AttributeSupplier
import net.minecraft.world.entity.player.Player
import net.minecraft.world.item.Item
import net.minecraft.world.level.Level

/**
 * 範例自己的實體及建立物品，不依賴本體 debug 內容或 DI。
 *
 * 實體的預設屬性與「拿人偶物品對坐墊按右鍵」的處理在這裡定義，由各載入器接到自己的屬性登記與互動事件。
 */
object ExampleActors {
    /** 可保存、由放置者擁有的人偶；寫入登記表後可用。 */
    lateinit var actorType: EntityType<ExampleActorEntity>
        private set

    /** 不保存的暫態代理，由角色框架管理生成及移除；寫入登記表後可用。 */
    lateinit var proxyType: EntityType<ExampleActorEntity>
        private set

    /** 建立人偶的物品，與其他範例物品共用創造分頁；寫入登記表後可用。 */
    lateinit var item: Item
        private set

    /**
     * 登記兩種原生類型與單一建立物品；客戶端 renderer 另由客戶端入口登記。
     *
     * @param registrar 載入器的登記方式。
     */
    fun register(registrar: ExampleRegistrar) {
        registrar.register(
            registry = Registries.ENTITY_TYPE,
            id = ResourceLocation(ExampleCharacterIds.ACTOR),
            factory = { actorEntityType(saveable = true) },
            onRegistered = { actorType = it },
        )
        registrar.register(
            registry = Registries.ENTITY_TYPE,
            id = ResourceLocation(ExampleCharacterIds.OWNED_ACTOR),
            factory = { actorEntityType(saveable = false) },
            onRegistered = { proxyType = it },
        )
        registrar.register(
            registry = Registries.ITEM,
            id = ResourceLocation(ExampleCharacterIds.ACTOR),
            factory = { ExampleActorItem(Item.Properties().stacksTo(16)) },
            onRegistered = { item = it },
        )
    }

    /**
     * 兩種人偶實體的預設屬性；生物實體一定要有，由各載入器的屬性登記加入。
     *
     * @return 依實體類型排列的預設屬性。
     */
    fun defaultAttributes(): Map<EntityType<out LivingEntity>, AttributeSupplier> = listOf(actorType, proxyType)
        .associateWith { LivingEntity.createLivingAttributes().build() }

    /**
     * 拿著人偶物品對坐墊按右鍵時，讓人偶坐上坐墊；其他互動交給原本的處理。
     *
     * @param player 互動的玩家。
     * @param level 互動發生的世界。
     * @param hand 使用的手。
     * @param target 被互動的實體。
     * @return 不是人偶物品對坐墊時為 [InteractionResult.PASS]；用戶端為 success；伺服器端放置成功時為 consume，失敗時為 fail。
     */
    fun useOnCushion(player: Player, level: Level, hand: InteractionHand, target: Entity): InteractionResult {
        val stack = player.getItemInHand(hand)
        if (target !is ExampleCushionEntity || !stack.`is`(item)) return InteractionResult.PASS
        if (level.isClientSide) return InteractionResult.SUCCESS
        if (level !is ServerLevel) return InteractionResult.FAIL
        val placed = ExampleActorPlacement.placeOnCushion(world = level, player = player, stack = stack, cushion = target)
        return if (placed) InteractionResult.CONSUME else InteractionResult.FAIL
    }

    /**
     * 建立人偶的實體類型。
     *
     * @param saveable 是否隨世界保存；角色框架管理的暫態代理不保存。
     * @return 尚未登記的實體類型。
     */
    private fun actorEntityType(saveable: Boolean): EntityType<ExampleActorEntity> = ExampleEntityTypes.create(
        factory = ::ExampleActorEntity,
        width = 0.5f,
        height = 1.975f,
        trackingRangeChunks = 10,
        updateInterval = 3,
        saveable = saveable,
    )
}
