package com.doublemoon1119.mahjongcraft.platform.fabric.entity

import com.doublemoon1119.mahjongcraft.platform.minecraft.animation.AnimationStep
import net.minecraft.entity.EntityType
import net.minecraft.nbt.NbtCompound
import net.minecraft.world.World

/** 沒有自訂瞬間 step 或運動內插、只使用共用動畫 step 的 entity 基底。 */
abstract class SimpleAnimatedMahjongEntity(
    type: EntityType<out SimpleAnimatedMahjongEntity>,
    world: World,
) : AnimatedMahjongEntity<Nothing>(type, world) {
    /** 此類 entity 不接受自訂瞬間 step。 */
    final override fun applyCustomStep(step: Nothing): Unit = error("Simple animated entities do not support custom steps")

    /** 此類 entity 不接受運動內插 step。 */
    final override fun applyPlayMotion(step: AnimationStep.PlayMotion, startGameTime: Long): Unit = error("Simple animated entities do not support motion steps")

    /** 此類 entity 不接受自訂 step，因此沒有可序列化內容。 */
    final override fun serializeCustomStep(step: Nothing, nbt: NbtCompound): Unit = error("Simple animated entities do not support custom steps")

    /** 此類 entity 不接受自訂 step，因此無法還原任何內容。 */
    final override fun deserializeCustomStep(nbt: NbtCompound): Nothing = error("Simple animated entities do not support custom steps")
}
