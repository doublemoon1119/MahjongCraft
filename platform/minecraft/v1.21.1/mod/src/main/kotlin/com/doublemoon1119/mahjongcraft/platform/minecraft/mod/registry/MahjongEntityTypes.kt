package com.doublemoon1119.mahjongcraft.platform.minecraft.mod.registry

import com.google.common.collect.ImmutableSet
import net.minecraft.world.entity.Entity
import net.minecraft.world.entity.EntityDimensions
import net.minecraft.world.entity.EntityType
import net.minecraft.world.entity.MobCategory
import net.minecraft.world.flag.FeatureFlags

/**
 * 建立 MahjongCraft 的實體型別。
 *
 * - 這些實體都屬於 [MobCategory.MISC]，不會著火，不會自然生成。
 * - 直接使用原版建構子，不用 [EntityType.Builder]。後者會替可存檔的型別查詢原版的存檔格式轉換資料，模組的實體沒有這份資料，
 *   每個型別都會在 log 留下一則錯誤。
 */
object MahjongEntityTypes {
    /** 區塊的邊長，以方塊為單位。 */
    private const val CHUNK_SIZE: Int = 16

    /**
     * 建立實體型別。
     *
     * @param factory 建立實體的函式。
     * @param width 碰撞箱寬度。
     * @param height 碰撞箱高度。
     * @param trackingRangeChunks 玩家在幾個區塊內會看到這個實體。
     * @param updateInterval 每幾個 tick 把位置與資料送給客戶端一次。
     * @param saveable 是否隨區塊寫入存檔。
     * @return 新的實體型別。
     */
    fun <T : Entity> create(
        factory: EntityType.EntityFactory<T>,
        width: Float,
        height: Float,
        trackingRangeChunks: Int,
        updateInterval: Int,
        saveable: Boolean = true,
    ): EntityType<T> = EntityType(
        factory,
        MobCategory.MISC,
        saveable,
        // 可以用 /summon 召喚。
        true,
        // 不會著火。
        true,
        // 可以在遠離玩家的位置生成；MISC 類別的原版預設。
        true,
        // 沒有額外免疫傷害的方塊。
        ImmutableSet.of(),
        EntityDimensions.fixed(width, height),
        // 生成檢查使用的尺寸比例；原版預設。
        1f,
        trackingRangeChunks,
        updateInterval,
        FeatureFlags.VANILLA_SET,
    )

    /**
     * 換算涵蓋指定方塊距離所需的區塊數，不足一個區塊的部分進位。
     *
     * @param blocks 追蹤距離，以方塊為單位。
     * @return 追蹤距離，以區塊為單位。
     */
    fun chunksCovering(blocks: Int): Int = (blocks + CHUNK_SIZE - 1) / CHUNK_SIZE
}
