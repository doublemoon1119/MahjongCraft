package com.doublemoon1119.mahjongcraft.platform.fabric.entity

import com.doublemoon1119.mahjongcraft.platform.minecraft.animation.AnimationStep
import net.minecraft.entity.EntityType
import net.minecraft.nbt.NbtCompound
import net.minecraft.world.World

/**
 * 沒有自訂瞬間 step 或運動內插、只使用共用動畫 step 的實體基底。
 *
 * @param type 此實體的種類。
 * @param world 此實體所屬的世界。
 */
abstract class SimpleAnimatedMahjongEntity(
    type: EntityType<out SimpleAnimatedMahjongEntity>,
    world: World,
) : AnimatedMahjongEntity<Nothing>(type, world) {
    /**
     * 此類實體不接受自訂瞬間 step。
     *
     * @param step 此實體不支援的自訂動畫步驟。
     */
    final override fun applyCustomStep(step: Nothing): Unit = error("Simple animated entities do not support custom steps")

    /**
     * 此類實體不接受運動內插 step。
     *
     * @param step 此實體不支援的運動內插動畫步驟。
     * @param startGameTime 動畫步驟預定開始的絕對遊戲時間。
     */
    final override fun applyPlayMotion(step: AnimationStep.PlayMotion, startGameTime: Long): Unit = error("Simple animated entities do not support motion steps")

    /**
     * 此類實體不接受自訂步驟，因此沒有可序列化內容。
     *
     * @param step 此實體不支援的自訂動畫步驟。
     * @param nbt 接收步驟資料的 NBT 複合標籤。
     */
    final override fun serializeCustomStep(step: Nothing, nbt: NbtCompound): Unit = error("Simple animated entities do not support custom steps")

    /**
     * 此類實體不接受自訂步驟，因此無法從 NBT 還原任何內容。
     *
     * @param nbt 含有待還原步驟資料的 NBT 複合標籤。
     */
    final override fun deserializeCustomStep(nbt: NbtCompound): Nothing = error("Simple animated entities do not support custom steps")
}
