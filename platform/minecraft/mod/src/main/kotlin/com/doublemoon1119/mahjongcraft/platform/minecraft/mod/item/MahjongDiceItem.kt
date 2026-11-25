package com.doublemoon1119.mahjongcraft.platform.minecraft.mod.item

import com.doublemoon1119.mahjongcraft.platform.minecraft.dice.DiceAnimationVector
import com.doublemoon1119.mahjongcraft.platform.minecraft.mod.entity.MahjongDiceEntity
import com.doublemoon1119.mahjongcraft.platform.minecraft.mod.entity.MahjongDicePoint
import com.doublemoon1119.mahjongcraft.platform.minecraft.mod.registry.ModSounds
import net.minecraft.core.Direction
import net.minecraft.server.level.ServerLevel
import net.minecraft.world.InteractionResult
import net.minecraft.world.item.Item
import net.minecraft.world.item.context.UseOnContext

/**
 * 蹲下對方塊右鍵時放置自由測試用麻將骰子的 item。
 *
 * @param settings 原版物品設定。
 */
class MahjongDiceItem(settings: Properties) : Item(settings) {
    /**
     * 蹲下時精確放置骰子；普通右鍵上表面時由玩家手部附近向命中點投擲。
     *
     * @param context 方塊互動位置、玩家及物品堆疊。
     */
    override fun useOn(context: UseOnContext): InteractionResult {
        val player = context.player ?: return InteractionResult.PASS
        if (!player.isShiftKeyDown && context.clickedFace != Direction.UP) return InteractionResult.PASS
        if (context.level.isClientSide) return InteractionResult.SUCCESS

        val world = context.level as ServerLevel
        val hitPos = context.clickLocation
        val entity = MahjongDiceEntity(world = world).apply {
            point = MahjongDicePoint.ONE
            moveTo(hitPos.x, hitPos.y, hitPos.z, context.rotation + 180.0f, 0.0f)
            if (!player.isShiftKeyDown) {
                val visualStart = player.eyePosition.add(0.0, HAND_VERTICAL_OFFSET, 0.0)
                startRoll(
                    finalPoint = MahjongDicePoint.fromValueOrDefault(world.random.nextInt(6) + 1),
                    seed = world.random.nextLong(),
                    startDelayTicks = 0,
                    startOffset = DiceAnimationVector(
                        x = visualStart.x - hitPos.x,
                        y = visualStart.y - hitPos.y,
                        z = visualStart.z - hitPos.z,
                    ),
                )
            }
        }
        val intersectsBlock = world.getBlockCollisions(entity, entity.boundingBox).iterator().hasNext()
        if (intersectsBlock || !world.addFreshEntity(entity)) return InteractionResult.FAIL

        if (!player.abilities.instabuild) context.itemInHand.shrink(1)
        if (player.isShiftKeyDown) entity.playSound(ModSounds.diceLand, 1.0f, 1.0f)
        return InteractionResult.CONSUME
    }

    private companion object {
        /** 以眼睛下方約四分之一格近似手部的投擲起點。 */
        const val HAND_VERTICAL_OFFSET = -0.25
    }
}
