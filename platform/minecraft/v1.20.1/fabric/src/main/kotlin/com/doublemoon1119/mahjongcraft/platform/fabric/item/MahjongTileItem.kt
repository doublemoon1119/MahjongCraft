package com.doublemoon1119.mahjongcraft.platform.fabric.item

import com.doublemoon1119.mahjongcraft.platform.fabric.entity.MahjongTileEntity
import com.doublemoon1119.mahjongcraft.platform.fabric.entity.MahjongTilePose
import com.doublemoon1119.mahjongcraft.platform.fabric.registry.ModSounds
import com.doublemoon1119.mahjongcraft.platform.minecraft.tile.MinecraftTileAssetRegistry
import com.doublemoon1119.mahjongcraft.platform.minecraft.tile.allTileAssetKeys
import com.doublemoon1119.mahjongcraft.platform.minecraft.tile.nextTileAssetKey
import net.minecraft.entity.player.PlayerEntity
import net.minecraft.item.Item
import net.minecraft.item.ItemStack
import net.minecraft.item.ItemUsageContext
import net.minecraft.server.world.ServerWorld
import net.minecraft.util.ActionResult
import net.minecraft.util.Hand
import net.minecraft.util.TypedActionResult
import net.minecraft.world.World
import org.koin.core.context.GlobalContext

/**
 * 麻將牌 item：單一 item 類型代表所有牌面，實際牌面由 NBT 的 [NBT_KEY_TILE] 字串決定
 * （對應 [allTileAssetKeys]）。
 *
 * 非蹲下右鍵循環切換牌面；蹲下對方塊右鍵則放置保留目前牌面的 [MahjongTileEntity]。
 */
class MahjongTileItem(settings: Settings) : Item(settings) {
    /** 非蹲下右鍵空氣時切換牌面；蹲下時交由方塊互動入口判斷是否放置。 */
    override fun use(world: World, user: PlayerEntity, hand: Hand): TypedActionResult<ItemStack> {
        val stack = user.getStackInHand(hand)
        if (user.isSneaking) return TypedActionResult.pass(stack)
        if (!world.isClient) advanceTileAssetKey(stack)
        return TypedActionResult.success(stack, world.isClient)
    }

    /** 非蹲下右鍵方塊切換牌面；蹲下時在命中點放置直立麻將牌 entity。 */
    override fun useOnBlock(context: ItemUsageContext): ActionResult {
        val player = context.player ?: return ActionResult.PASS
        if (context.world.isClient) return ActionResult.SUCCESS

        if (!player.isSneaking) {
            advanceTileAssetKey(context.stack)
            return ActionResult.CONSUME
        }

        val world = context.world as ServerWorld
        val entity = MahjongTileEntity(world = world).apply {
            tileAssetKey = readTileAssetKey(context.stack)
            tilePose = MahjongTilePose.STANDING
            val hitPos = context.hitPos
            refreshPositionAndAngles(hitPos.x, hitPos.y, hitPos.z, context.playerYaw + 180.0f, 0.0f)
        }
        val intersectsBlock = world.getBlockCollisions(entity, entity.boundingBox).iterator().hasNext()
        if (intersectsBlock || !world.spawnEntity(entity)) return ActionResult.FAIL

        if (!player.abilities.creativeMode) context.stack.decrement(1)
        entity.playSound(ModSounds.tileDiscardLand, 1.0f, 1.0f)
        return ActionResult.CONSUME
    }

    companion object {
        /** ItemStack 自訂資料內保存牌面 asset key 的名稱。 */
        const val NBT_KEY_TILE = MahjongTileItemStackData.NBT_KEY_TILE

        /** 讀取並正規化 item 保存的牌面；缺失值使用配方預設 `m1`，非法值回退為 `unknown`。 */
        fun readTileAssetKey(stack: ItemStack): String = MahjongTileItemStackData.read(stack)

        /** 寫入經正規化的牌面 asset key。 */
        fun writeTileAssetKey(stack: ItemStack, assetKey: String) = MahjongTileItemStackData.write(stack, assetKey)

        /** 將 item 循環至下一個牌面；無自訂資料的配方產物以目前顯示的 `m1` 為起點。 */
        fun advanceTileAssetKey(stack: ItemStack) {
            writeTileAssetKey(stack, readTileAssetKey(stack).nextTileAssetKey(GlobalContext.get().get<MinecraftTileAssetRegistry>()))
        }
    }
}
