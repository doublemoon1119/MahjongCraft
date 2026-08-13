package com.doublemoon1119.mahjongcraft.platform.fabric.item

import com.doublemoon1119.mahjongcraft.platform.fabric.entity.MahjongTileEntity
import com.doublemoon1119.mahjongcraft.platform.fabric.entity.MahjongTilePose
import com.doublemoon1119.mahjongcraft.platform.minecraft.tile.nextTileAssetKey
import com.doublemoon1119.mahjongcraft.platform.minecraft.tile.normalizedTileAssetKey
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
 * 麻將牌 item：單一 item 類型代表所有牌面，實際牌面由 NBT 的 [NBT_KEY_TILE] 字串決定
 * （對應 [com.doublemoon1119.mahjongcraft.platform.minecraft.tile.ALL_RIICHI_TILE_ASSET_KEYS]）。
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
        return ActionResult.CONSUME
    }

    companion object {
        /** ItemStack 自訂資料內保存牌面 asset key 的名稱。 */
        const val NBT_KEY_TILE = "tile"

        /** 讀取並正規化 item 保存的牌面；缺失或非法值回退為 `unknown`。 */
        fun readTileAssetKey(stack: ItemStack): String = stack.nbt
            ?.getString(NBT_KEY_TILE)
            .normalizedTileAssetKey()

        /** 寫入經正規化的牌面 asset key。 */
        fun writeTileAssetKey(stack: ItemStack, assetKey: String) {
            stack.orCreateNbt.putString(NBT_KEY_TILE, assetKey.normalizedTileAssetKey())
        }

        /** 將 item 循環至下一個牌面，非法或缺失值從 `m1` 開始。 */
        fun advanceTileAssetKey(stack: ItemStack) {
            val storedKey = stack.nbt?.getString(NBT_KEY_TILE)
            writeTileAssetKey(stack, storedKey.nextTileAssetKey())
        }
    }
}
