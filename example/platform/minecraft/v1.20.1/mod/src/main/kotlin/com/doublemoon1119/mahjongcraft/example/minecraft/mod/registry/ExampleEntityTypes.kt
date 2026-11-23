package com.doublemoon1119.mahjongcraft.example.minecraft.mod.registry

import com.google.common.collect.ImmutableSet
import net.minecraft.world.entity.Entity
import net.minecraft.world.entity.EntityDimensions
import net.minecraft.world.entity.EntityType
import net.minecraft.world.entity.MobCategory
import net.minecraft.world.flag.FeatureFlags

/**
 * 以原版建構子建立範例的實體類型，各載入器都能使用。
 *
 * 不使用原版的 `EntityType.Builder`：它的 `build` 會依 ID 查詢資料修正器，範例的實體沒有資料修正器，開發環境中會因此失敗。
 */
internal object ExampleEntityTypes {
    /**
     * 建立一種範例實體類型。
     *
     * @param T 實體型別。
     * @param factory 建立實體的方式。
     * @param width 碰撞箱寬度。
     * @param height 碰撞箱高度。
     * @param trackingRangeChunks 玩家在多少區塊內會收到這個實體。
     * @param updateInterval 每幾個 tick 同步一次位置。
     * @param saveable 是否隨世界保存。
     * @param fireImmune 是否不會著火。
     * @return 尚未登記的實體類型。
     */
    fun <T : Entity> create(
        factory: EntityType.EntityFactory<T>,
        width: Float,
        height: Float,
        trackingRangeChunks: Int,
        updateInterval: Int,
        saveable: Boolean = true,
        fireImmune: Boolean = false,
    ): EntityType<T> = EntityType(
        factory,
        MobCategory.MISC,
        saveable,
        // 可以用 /summon 召喚。
        true,
        fireImmune,
        // 可以在遠離玩家的位置生成；MISC 類別的原版預設。
        true,
        // 沒有額外免疫傷害的方塊。
        ImmutableSet.of(),
        EntityDimensions.fixed(width, height),
        trackingRangeChunks,
        updateInterval,
        FeatureFlags.VANILLA_SET,
    )
}
