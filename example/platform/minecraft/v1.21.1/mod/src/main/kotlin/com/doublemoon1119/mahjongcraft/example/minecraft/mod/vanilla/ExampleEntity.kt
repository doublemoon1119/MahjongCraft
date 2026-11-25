package com.doublemoon1119.mahjongcraft.example.minecraft.mod.vanilla

import net.minecraft.network.syncher.SynchedEntityData
import net.minecraft.world.entity.Entity
import net.minecraft.world.entity.EntityType
import net.minecraft.world.level.Level

/**
 * 範例實體的基底：範例的實體都不需要同步到用戶端的資料，各版本宣告同步資料的原版方法由這個類別處理。
 *
 * @param type 實體類型。
 * @param world 實體所屬世界。
 */
abstract class ExampleEntity(type: EntityType<*>, world: Level) : Entity(type, world) {
    final override fun defineSynchedData(builder: SynchedEntityData.Builder) = Unit
}
