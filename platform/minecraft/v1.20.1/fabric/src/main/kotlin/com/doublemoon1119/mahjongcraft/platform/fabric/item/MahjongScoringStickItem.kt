package com.doublemoon1119.mahjongcraft.platform.fabric.item

import com.doublemoon1119.mahjongcraft.platform.fabric.entity.MahjongScoringStickEntity
import com.doublemoon1119.mahjongcraft.platform.fabric.registry.ModSounds
import com.doublemoon1119.mahjongcraft.platform.minecraft.stick.MahjongScoringStickDenomination
import net.minecraft.server.level.ServerLevel
import net.minecraft.world.InteractionHand
import net.minecraft.world.InteractionResult
import net.minecraft.world.InteractionResultHolder
import net.minecraft.world.entity.player.Player
import net.minecraft.world.item.Item
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.context.UseOnContext
import net.minecraft.world.level.Level

/**
 * 麻將點棒 item：單一 item 類型代表所有面額，實際面額由 NBT 的 [NBT_KEY_DENOMINATION] 決定。
 *
 * 非蹲下右鍵循環切換面額；蹲下對方塊右鍵則放置保留目前面額的 [MahjongScoringStickEntity]。
 *
 * @param settings 原版物品設定。
 */
class MahjongScoringStickItem(settings: Properties) : Item(settings) {
    /**
     * 非蹲下右鍵空氣時切換面額；蹲下時交由方塊互動入口判斷是否放置。
     *
     * @param world 互動所在世界。
     * @param user 使用物品的玩家。
     * @param hand 玩家使用的手。
     */
    override fun use(world: Level, user: Player, hand: InteractionHand): InteractionResultHolder<ItemStack> {
        val stack = user.getItemInHand(hand)
        if (user.isShiftKeyDown) return InteractionResultHolder.pass(stack)
        if (!world.isClientSide) advanceDenomination(stack)
        return InteractionResultHolder.sidedSuccess(stack, world.isClientSide)
    }

    /**
     * 非蹲下右鍵方塊切換面額；蹲下時在命中點放置點棒 entity。
     *
     * @param context 方塊互動位置、玩家及物品堆疊。
     */
    override fun useOn(context: UseOnContext): InteractionResult {
        val player = context.player ?: return InteractionResult.PASS
        if (context.level.isClientSide) return InteractionResult.SUCCESS

        if (!player.isShiftKeyDown) {
            advanceDenomination(context.itemInHand)
            return InteractionResult.CONSUME
        }

        val world = context.level as ServerLevel
        val entity = MahjongScoringStickEntity(world = world).apply {
            denomination = readDenomination(context.itemInHand)
            val hitPos = context.clickLocation
            moveTo(hitPos.x, hitPos.y, hitPos.z, context.rotation + 180.0f, 0.0f)
        }
        val intersectsBlock = world.getBlockCollisions(entity, entity.boundingBox).iterator().hasNext()
        if (intersectsBlock || !world.addFreshEntity(entity)) return InteractionResult.FAIL

        if (!player.abilities.instabuild) context.itemInHand.shrink(1)
        entity.playSound(ModSounds.scoringStickPlace, 1.0f, 1.0f)
        return InteractionResult.CONSUME
    }

    companion object {
        /** ItemStack 自訂資料內保存面額的欄位名稱。 */
        const val NBT_KEY_DENOMINATION = MahjongScoringStickItemStackData.NBT_KEY_DENOMINATION

        /**
         * 讀取 item 保存的面額；缺失或非法值使用百分棒。
         *
         * @param stack 要讀取的點棒物品堆疊。
         * @return 堆疊保存的有效面額；缺失或非法值時為百分棒。
         */
        fun readDenomination(stack: ItemStack): MahjongScoringStickDenomination = MahjongScoringStickItemStackData.read(stack)

        /**
         * 以名稱寫入面額。
         *
         * @param stack 要更新的點棒物品堆疊。
         * @param denomination 要保存的面額。
         */
        fun writeDenomination(stack: ItemStack, denomination: MahjongScoringStickDenomination) = MahjongScoringStickItemStackData.write(stack, denomination)

        /**
         * 將 item 循環至下一個面額；無自訂資料的配方產物以目前顯示的百分棒為起點。
         *
         * @param stack 要更新的點棒物品堆疊。
         */
        fun advanceDenomination(stack: ItemStack) {
            writeDenomination(stack, readDenomination(stack).next())
        }
    }
}
