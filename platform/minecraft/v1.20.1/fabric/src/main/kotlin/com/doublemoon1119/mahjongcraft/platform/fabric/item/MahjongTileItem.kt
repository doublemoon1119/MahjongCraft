package com.doublemoon1119.mahjongcraft.platform.fabric.item

import com.doublemoon1119.mahjongcraft.platform.fabric.entity.MahjongTileEntity
import com.doublemoon1119.mahjongcraft.platform.fabric.entity.MahjongTilePose
import com.doublemoon1119.mahjongcraft.platform.fabric.registry.ModSounds
import com.doublemoon1119.mahjongcraft.platform.minecraft.tile.MinecraftTileAssetRegistry
import com.doublemoon1119.mahjongcraft.platform.minecraft.tile.allTileAssetKeys
import com.doublemoon1119.mahjongcraft.platform.minecraft.tile.nextTileAssetKey
import net.minecraft.server.level.ServerLevel
import net.minecraft.world.InteractionHand
import net.minecraft.world.InteractionResult
import net.minecraft.world.InteractionResultHolder
import net.minecraft.world.entity.player.Player
import net.minecraft.world.item.Item
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.context.UseOnContext
import net.minecraft.world.level.Level
import org.koin.core.context.GlobalContext

/**
 * 麻將牌 item：單一 item 類型代表所有牌面，實際牌面由 NBT 的 [NBT_KEY_TILE] 字串決定
 * （對應 [allTileAssetKeys]）。
 *
 * 非蹲下右鍵循環切換牌面；蹲下對方塊右鍵則放置保留目前牌面的 [MahjongTileEntity]。
 *
 * @param settings 原版物品設定。
 */
class MahjongTileItem(settings: Properties) : Item(settings) {
    /**
     * 非蹲下右鍵空氣時切換牌面；蹲下時交由方塊互動入口判斷是否放置。
     *
     * @param world 互動所在世界。
     * @param user 使用物品的玩家。
     * @param hand 玩家使用的手。
     */
    override fun use(world: Level, user: Player, hand: InteractionHand): InteractionResultHolder<ItemStack> {
        val stack = user.getItemInHand(hand)
        if (user.isShiftKeyDown) return InteractionResultHolder.pass(stack)
        if (!world.isClientSide) advanceTileAssetKey(stack)
        return InteractionResultHolder.sidedSuccess(stack, world.isClientSide)
    }

    /**
     * 非蹲下右鍵方塊切換牌面；蹲下時在命中點放置直立麻將牌 entity。
     *
     * @param context 方塊互動位置、玩家及物品堆疊。
     */
    override fun useOn(context: UseOnContext): InteractionResult {
        val player = context.player ?: return InteractionResult.PASS
        if (context.level.isClientSide) return InteractionResult.SUCCESS

        if (!player.isShiftKeyDown) {
            advanceTileAssetKey(context.itemInHand)
            return InteractionResult.CONSUME
        }

        val world = context.level as ServerLevel
        val entity = MahjongTileEntity(world = world).apply {
            tileAssetKey = readTileAssetKey(context.itemInHand)
            tilePose = MahjongTilePose.STANDING
            val hitPos = context.clickLocation
            moveTo(hitPos.x, hitPos.y, hitPos.z, context.rotation + 180.0f, 0.0f)
        }
        val intersectsBlock = world.getBlockCollisions(entity, entity.boundingBox).iterator().hasNext()
        if (intersectsBlock || !world.addFreshEntity(entity)) return InteractionResult.FAIL

        if (!player.abilities.instabuild) context.itemInHand.shrink(1)
        entity.playSound(ModSounds.tileDiscardLand, 1.0f, 1.0f)
        return InteractionResult.CONSUME
    }

    companion object {
        /** ItemStack 自訂資料內保存牌面 asset key 的名稱。 */
        const val NBT_KEY_TILE = MahjongTileItemStackData.NBT_KEY_TILE

        /**
         * 讀取並正規化 item 保存的牌面；缺失值使用配方預設 `m1`，非法值回退為 `unknown`。
         *
         * @param stack 要讀取的麻將牌物品堆疊。
         * @return 正規化後的牌面 asset key。
         */
        fun readTileAssetKey(stack: ItemStack): String = MahjongTileItemStackData.read(stack)

        /**
         * 寫入經正規化的牌面 asset key。
         *
         * @param stack 要更新的麻將牌物品堆疊。
         * @param assetKey 要保存的牌面 key。
         */
        fun writeTileAssetKey(stack: ItemStack, assetKey: String) = MahjongTileItemStackData.write(stack, assetKey)

        /**
         * 將 item 循環至下一個牌面；無自訂資料的配方產物以目前顯示的 `m1` 為起點。
         *
         * @param stack 要更新的麻將牌物品堆疊。
         */
        fun advanceTileAssetKey(stack: ItemStack) {
            writeTileAssetKey(stack, readTileAssetKey(stack).nextTileAssetKey(GlobalContext.get().get<MinecraftTileAssetRegistry>()))
        }
    }
}
