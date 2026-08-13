package com.doublemoon1119.mahjongcraft.platform.fabric.item

import com.doublemoon1119.mahjongcraft.platform.fabric.entity.MahjongDiceEntity
import com.doublemoon1119.mahjongcraft.platform.fabric.entity.MahjongDicePoint
import net.minecraft.item.Item
import net.minecraft.item.ItemUsageContext
import net.minecraft.server.world.ServerWorld
import net.minecraft.sound.SoundEvents
import net.minecraft.util.ActionResult

/** 蹲下對方塊右鍵時放置自由測試用麻將骰子的 item。 */
class MahjongDiceItem(settings: Settings) : Item(settings) {
    /** 在命中點放置一點朝上且不提供物理阻擋的骰子 entity。 */
    override fun useOnBlock(context: ItemUsageContext): ActionResult {
        val player = context.player ?: return ActionResult.PASS
        if (!player.isSneaking) return ActionResult.PASS
        if (context.world.isClient) return ActionResult.SUCCESS

        val world = context.world as ServerWorld
        val entity = MahjongDiceEntity(world = world).apply {
            point = MahjongDicePoint.ONE
            val hitPos = context.hitPos
            refreshPositionAndAngles(hitPos.x, hitPos.y, hitPos.z, context.playerYaw + 180.0f, 0.0f)
        }
        val intersectsBlock = world.getBlockCollisions(entity, entity.boundingBox).iterator().hasNext()
        if (intersectsBlock || !world.spawnEntity(entity)) return ActionResult.FAIL

        if (!player.abilities.creativeMode) context.stack.decrement(1)
        entity.playSound(SoundEvents.ENTITY_ITEM_FRAME_PLACE, 1.0f, 1.0f)
        return ActionResult.CONSUME
    }
}
