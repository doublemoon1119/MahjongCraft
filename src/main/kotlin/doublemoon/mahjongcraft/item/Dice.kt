package doublemoon.mahjongcraft.item

import doublemoon.mahjongcraft.entity.DiceEntity
import net.minecraft.entity.player.PlayerEntity
import net.minecraft.item.Item
import net.minecraft.item.ItemStack
import net.minecraft.util.Hand
import net.minecraft.util.TypedActionResult
import net.minecraft.util.math.MathHelper
import net.minecraft.world.World

class Dice(settings: Settings) : Item(settings) {

    override fun use(world: World, user: PlayerEntity, hand: Hand): TypedActionResult<ItemStack> {
        val itemStack = user.getStackInHand(hand)
        if (world.isClient) return TypedActionResult.success(itemStack)
        val diceEntity = DiceEntity(
            world = world,
            pos = user.pos.add(0.0, 1.1, 0.0),
            yaw = user.headYaw
        ).apply {
            val sin = MathHelper.sin(yaw * 0.017453292F - 11)
            val cos = MathHelper.cos(yaw * 0.017453292F - 11)
            setVelocity(0.25 * cos, 0.2, 0.25 * sin)
        }
        world.spawnEntity(diceEntity)
        if (!user.abilities.creativeMode) itemStack.decrement(1)
        return TypedActionResult.success(itemStack)
    }
}