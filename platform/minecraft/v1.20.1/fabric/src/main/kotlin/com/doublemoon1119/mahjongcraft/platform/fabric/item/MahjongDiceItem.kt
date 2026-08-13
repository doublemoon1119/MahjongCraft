package com.doublemoon1119.mahjongcraft.platform.fabric.item

import com.doublemoon1119.mahjongcraft.platform.fabric.entity.MahjongDiceEntity
import com.doublemoon1119.mahjongcraft.platform.fabric.entity.MahjongDicePoint
import com.doublemoon1119.mahjongcraft.platform.minecraft.dice.DiceAnimationVector
import net.minecraft.item.Item
import net.minecraft.item.ItemUsageContext
import net.minecraft.server.world.ServerWorld
import net.minecraft.sound.SoundEvents
import net.minecraft.util.ActionResult
import net.minecraft.util.math.Direction

/** 蹲下對方塊右鍵時放置自由測試用麻將骰子的 item。 */
class MahjongDiceItem(settings: Settings) : Item(settings) {
    /** 蹲下時精確放置骰子；普通右鍵上表面時由玩家手部附近向命中點投擲。 */
    override fun useOnBlock(context: ItemUsageContext): ActionResult {
        val player = context.player ?: return ActionResult.PASS
        if (!player.isSneaking && context.side != Direction.UP) return ActionResult.PASS
        if (context.world.isClient) return ActionResult.SUCCESS

        val world = context.world as ServerWorld
        val hitPos = context.hitPos
        val entity = MahjongDiceEntity(world = world).apply {
            point = MahjongDicePoint.ONE
            refreshPositionAndAngles(hitPos.x, hitPos.y, hitPos.z, context.playerYaw + 180.0f, 0.0f)
            if (!player.isSneaking) {
                val visualStart = player.eyePos.add(0.0, HAND_VERTICAL_OFFSET, 0.0)
                startRoll(
                    finalPoint = MahjongDicePoint.fromValueOrDefault(world.random.nextInt(6) + 1),
                    seed = world.random.nextLong(),
                    startGameTime = world.time,
                    startOffset = DiceAnimationVector(
                        x = visualStart.x - hitPos.x,
                        y = visualStart.y - hitPos.y,
                        z = visualStart.z - hitPos.z,
                    ),
                )
            }
        }
        val intersectsBlock = world.getBlockCollisions(entity, entity.boundingBox).iterator().hasNext()
        if (intersectsBlock || !world.spawnEntity(entity)) return ActionResult.FAIL

        if (!player.abilities.creativeMode) context.stack.decrement(1)
        if (player.isSneaking) entity.playSound(SoundEvents.ENTITY_ITEM_FRAME_PLACE, 1.0f, 1.0f)
        return ActionResult.CONSUME
    }

    private companion object {
        /** 以眼睛下方約四分之一格近似手部的投擲起點。 */
        const val HAND_VERTICAL_OFFSET = -0.25
    }
}
