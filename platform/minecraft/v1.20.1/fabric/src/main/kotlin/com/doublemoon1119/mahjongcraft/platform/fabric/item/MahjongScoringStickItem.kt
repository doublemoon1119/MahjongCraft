package com.doublemoon1119.mahjongcraft.platform.fabric.item

import com.doublemoon1119.mahjongcraft.platform.fabric.entity.MahjongScoringStickDenomination
import com.doublemoon1119.mahjongcraft.platform.fabric.entity.MahjongScoringStickEntity
import com.doublemoon1119.mahjongcraft.platform.fabric.registry.ModSounds
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
 * 麻將點棒 item：單一 item 類型代表所有面額，實際面額由 NBT 的 [NBT_KEY_DENOMINATION] 決定。
 *
 * 非蹲下右鍵循環切換面額；蹲下對方塊右鍵則放置保留目前面額的 [MahjongScoringStickEntity]。
 */
class MahjongScoringStickItem(settings: Settings) : Item(settings) {
    /** 非蹲下右鍵空氣時切換面額；蹲下時交由方塊互動入口判斷是否放置。 */
    override fun use(world: World, user: PlayerEntity, hand: Hand): TypedActionResult<ItemStack> {
        val stack = user.getStackInHand(hand)
        if (user.isSneaking) return TypedActionResult.pass(stack)
        if (!world.isClient) advanceDenomination(stack)
        return TypedActionResult.success(stack, world.isClient)
    }

    /** 非蹲下右鍵方塊切換面額；蹲下時在命中點放置點棒 entity。 */
    override fun useOnBlock(context: ItemUsageContext): ActionResult {
        val player = context.player ?: return ActionResult.PASS
        if (context.world.isClient) return ActionResult.SUCCESS

        if (!player.isSneaking) {
            advanceDenomination(context.stack)
            return ActionResult.CONSUME
        }

        val world = context.world as ServerWorld
        val entity = MahjongScoringStickEntity(world = world).apply {
            denomination = readDenomination(context.stack)
            val hitPos = context.hitPos
            refreshPositionAndAngles(hitPos.x, hitPos.y, hitPos.z, context.playerYaw + 180.0f, 0.0f)
        }
        val intersectsBlock = world.getBlockCollisions(entity, entity.boundingBox).iterator().hasNext()
        if (intersectsBlock || !world.spawnEntity(entity)) return ActionResult.FAIL

        if (!player.abilities.creativeMode) context.stack.decrement(1)
        entity.playSound(ModSounds.scoringStickPlace, 1.0f, 1.0f)
        return ActionResult.CONSUME
    }

    companion object {
        /** ItemStack 自訂資料內保存面額 ordinal 的名稱。 */
        const val NBT_KEY_DENOMINATION = "denomination"

        /** 讀取 item 保存的面額；缺失或非法值使用百分棒。 */
        fun readDenomination(stack: ItemStack): MahjongScoringStickDenomination {
            val storedOrdinal = stack.nbt?.takeIf { it.contains(NBT_KEY_DENOMINATION) }?.getInt(NBT_KEY_DENOMINATION)
            return MahjongScoringStickDenomination.fromOrdinalOrDefault(storedOrdinal ?: 0)
        }

        /** 寫入面額 ordinal。 */
        fun writeDenomination(stack: ItemStack, denomination: MahjongScoringStickDenomination) {
            stack.orCreateNbt.putInt(NBT_KEY_DENOMINATION, denomination.ordinal)
        }

        /** 將 item 循環至下一個面額；無自訂資料的配方產物以目前顯示的百分棒為起點。 */
        fun advanceDenomination(stack: ItemStack) {
            writeDenomination(stack, readDenomination(stack).next())
        }
    }
}
