package doublemoon.mahjongcraft.item

import doublemoon.mahjongcraft.entity.MahjongTileEntity
import net.minecraft.component.DataComponentTypes
import net.minecraft.component.type.NbtComponent
import net.minecraft.entity.player.PlayerEntity
import net.minecraft.item.Item
import net.minecraft.item.ItemStack
import net.minecraft.item.ItemUsageContext
import net.minecraft.nbt.NbtCompound
import net.minecraft.server.world.ServerWorld
import net.minecraft.util.ActionResult
import net.minecraft.util.Hand
import net.minecraft.util.TypedActionResult
import net.minecraft.world.World

/**
 * 麻將牌物品
 *
 * 使用 NBT 之中的 "code" 來控制牌的外觀
 * */
class MahjongTile(settings: Settings) : Item(settings) {
    /**
     * 使用右鍵就會觸發,
     * 不會與 [useOnBlock] 一起觸發 (應該吧?),
     * 右鍵改變外觀
     * */
    override fun use(world: World, user: PlayerEntity, hand: Hand): TypedActionResult<ItemStack> {
        val itemStack = user.getStackInHand(hand)
        // 僅允許伺服端執行，如果是客戶端就直接返回
        if (user.world.isClient) return TypedActionResult.success(itemStack)

        if (!user.isSneaking) { // 沒有蹲下
            val nbt = itemStack.get(DataComponentTypes.CUSTOM_DATA)?.copyNbt() ?: NbtCompound()
            val tileCode = nbt.getInt("code")
            nbt.putInt("code", (tileCode + 1) % 38)
            itemStack.set(DataComponentTypes.CUSTOM_DATA, NbtComponent.of(nbt))
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
        // 僅允許伺服端執行，如果是客戶端就直接返回
        if (context.world.isClient) return ActionResult.SUCCESS

        val itemStack = context.stack
        val nbt = itemStack.get(DataComponentTypes.CUSTOM_DATA)?.copyNbt() ?: NbtCompound()
        val tileCode = nbt.getInt("code")

        return if (player.isSneaking) { // 有蹲下才能放置
            val world = context.world as ServerWorld
            MahjongTileEntity(world = world, code = tileCode).apply {
                val pos = context.hitPos
                refreshPositionAndAngles(pos.x, pos.y, pos.z, (context.playerYaw + 180f), 0f)
                world.spawnEntity(this)
            }
            if (!player.abilities.creativeMode) itemStack.decrement(1)
            ActionResult.CONSUME
        } else {  //沒蹲下, 改變牌的外觀
            nbt.putInt("code", (tileCode + 1) % 38)
            itemStack.set(DataComponentTypes.CUSTOM_DATA, NbtComponent.of(nbt))
            ActionResult.SUCCESS
        }
    }
}