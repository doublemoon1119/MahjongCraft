package com.doublemoon1119.mahjongcraft.platform.fabric.entity

import com.doublemoon1119.mahjongcraft.platform.fabric.registry.ModEntities
import net.minecraft.entity.EntityType
import net.minecraft.world.World

/**
 * 承載胡牌與後續役種動畫的具體視覺 entity type；共用時間軸、目標追蹤與持久化由
 * [AnimatedVisualEffectEntity] 提供，client renderer 依 `effectKey` 選擇實際畫面。
 */
class WinCelebrationEffectEntity(
    type: EntityType<out WinCelebrationEffectEntity> = ModEntities.winCelebrationEffect,
    world: World,
) : AnimatedVisualEffectEntity(type, world) {
    companion object {
        /** 視錐剔除使用的非零寬度，涵蓋 renderer 的三叉戟與最大水平半徑。 */
        const val WIDTH: Float = 1.0f

        /** 視錐剔除使用的高度，涵蓋三叉戟完整落下路徑。 */
        const val HEIGHT: Float = 2.4f
    }
}
