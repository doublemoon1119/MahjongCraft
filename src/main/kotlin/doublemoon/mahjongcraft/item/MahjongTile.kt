package doublemoon.mahjongcraft.item

import doublemoon.mahjongcraft.entity.MahjongTileEntity
import net.minecraft.entity.player.PlayerEntity
import net.minecraft.item.Item
import net.minecraft.item.ItemStack
import net.minecraft.item.ItemUsageContext
import net.minecraft.server.world.ServerWorld
import net.minecraft.util.ActionResult
import net.minecraft.util.Hand
import net.minecraft.util.TypedActionResult
import net.minecraft.world.World

/**
 * 麻將牌物品
 * */
class MahjongTile(settings: Settings) : Item(settings) {
    /**
     * 使用右鍵就會觸發,
     * 不會與 [useOnBlock] 一起觸發 (應該吧?),
     * 右鍵改變外觀
     * */
    override fun use(world: World, user: PlayerEntity, hand: Hand): TypedActionResult<ItemStack> {
        val itemStack = user.getStackInHand(hand)
        if (user.world.isClient) return TypedActionResult.success(itemStack)
        //如果是在客戶端觸發, 直接當作完成, 不會進行以下操作
        val tileCode = itemStack.damage
        if (!user.isSneaking) { //沒有蹲下
            itemStack.damage = (tileCode + 1) % 38
            return TypedActionResult.success(itemStack)
        }
        return TypedActionResult.pass(itemStack)
    }

    /**
     * 對方塊互動觸發,
     * 蹲下右鍵放置, 右鍵改變外觀
     * */
    override fun useOnBlock(context: ItemUsageContext): ActionResult {
        val player = context.player ?: return ActionResult.PASS
        if (context.world.isClient) return ActionResult.SUCCESS
        //如果是在客戶端觸發, 直接當作完成, 不會進行以下操作
        val itemStack = context.stack
        val tileCode = itemStack.damage
        return if (player.isSneaking) { //有蹲下才能放置
            val world = context.world as ServerWorld
            MahjongTileEntity(world = world, code = tileCode).apply {
                val pos = context.hitPos
                refreshPositionAndAngles(pos.x, pos.y, pos.z, (context.playerYaw + 180f), 0f)
                world.spawnEntity(this)
            }
            if (!player.abilities.creativeMode) itemStack.decrement(1)
            ActionResult.CONSUME
        } else {  //沒蹲下, 改變牌的外觀
            itemStack.damage = (tileCode + 1) % 38
            ActionResult.SUCCESS
        }
    }
}