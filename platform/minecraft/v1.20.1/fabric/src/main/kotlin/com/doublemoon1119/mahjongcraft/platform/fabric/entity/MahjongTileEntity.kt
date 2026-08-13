package com.doublemoon1119.mahjongcraft.platform.fabric.entity

import com.doublemoon1119.mahjongcraft.platform.fabric.item.MahjongTileItem
import com.doublemoon1119.mahjongcraft.platform.fabric.registry.ModEntities
import com.doublemoon1119.mahjongcraft.platform.fabric.registry.ModItems
import com.doublemoon1119.mahjongcraft.platform.minecraft.tile.UNKNOWN_TILE_ASSET_KEY
import com.doublemoon1119.mahjongcraft.platform.minecraft.tile.normalizedTileAssetKey
import net.minecraft.entity.Entity
import net.minecraft.entity.EntityDimensions
import net.minecraft.entity.EntityPose
import net.minecraft.entity.EntityType
import net.minecraft.entity.data.DataTracker
import net.minecraft.entity.data.TrackedData
import net.minecraft.entity.data.TrackedDataHandlerRegistry
import net.minecraft.entity.player.PlayerEntity
import net.minecraft.item.ItemStack
import net.minecraft.nbt.NbtCompound
import net.minecraft.sound.SoundEvents
import net.minecraft.util.ActionResult
import net.minecraft.util.Hand
import net.minecraft.world.World

/**
 * 可自由放置的麻將牌 entity。
 *
 * 目前只保存 Minecraft 呈現所需的牌面與姿態；正式牌局接線後，權威 [IdentifiedTile][com.doublemoon1119.mahjongcraft.logic.base.IdentifiedTile]
 * 的 ID 會直接使用此 entity 的 UUID，不在 entity 內複製麻將規則狀態。
 */
class MahjongTileEntity(
    type: EntityType<out MahjongTileEntity> = ModEntities.mahjongTile,
    world: World,
) : Entity(type, world) {
    /** 牌面素材 key；外部輸入會正規化為支援值或 `unknown`。 */
    var tileAssetKey: String
        get() = dataTracker[TILE_ASSET_KEY].normalizedTileAssetKey()
        set(value) = dataTracker.set(TILE_ASSET_KEY, value.normalizedTileAssetKey())

    /** 牌相對於表面的姿態；改變後立即更新 bounding box。 */
    var tilePose: MahjongTilePose
        get() = MahjongTilePose.fromOrdinalOrDefault(dataTracker[TILE_POSE])
        set(value) = dataTracker.set(TILE_POSE, value.ordinal)

    init {
        setNoGravity(true)
    }

    /** 麻將牌保留物理碰撞；可由 raycast 選取的能力由 [isAttackable] 另行保留。 */
    override fun isCollidable(): Boolean = true

    /** 麻將牌固定不動，不參與一般 entity 推擠。 */
    override fun isPushable(): Boolean = false

    /** 允許玩家視線 raycast 選取麻將牌，以執行右鍵互動與後續 HUD targeting。 */
    override fun canHit(): Boolean = !isRemoved

    /** 允許麻將牌成為攻擊目標；與控制視線選取的 [canHit] 分開處理。 */
    override fun isAttackable(): Boolean = !isRemoved

    /** 依直立或平放姿態提供實際尺寸。 */
    override fun getDimensions(pose: EntityPose): EntityDimensions = when (tilePose) {
        MahjongTilePose.STANDING -> EntityDimensions.fixed(TILE_WIDTH, TILE_HEIGHT)
        MahjongTilePose.FACE_UP, MahjongTilePose.FACE_DOWN -> EntityDimensions.fixed(TILE_HEIGHT, TILE_DEPTH)
    }

    /**
     * 姿態同步後立即重算 bounding box，並保留切換前的底面高度。
     *
     * [calculateDimensions] 在 entity 由直立縮成平放尺寸時可能調整位置；重新以原本底面定位可避免模型與
     * bounding box 一起浮離所放置的表面。Server 本地更新與 client tracked data 同步都經過此入口。
     */
    override fun onTrackedDataSet(data: TrackedData<*>) {
        super.onTrackedDataSet(data)
        if (data == TILE_POSE) {
            val bottomY = boundingBox.minY
            calculateDimensions()
            setPosition(x, bottomY, z)
        }
    }

    /** 非蹲下右鍵循環姿態；蹲下右鍵回收保留牌面的物品。 */
    override fun interact(player: PlayerEntity, hand: Hand): ActionResult {
        if (world.isClient) return ActionResult.SUCCESS

        if (player.isSneaking) {
            val stack = ItemStack(ModItems.MAHJONG_TILE)
            MahjongTileItem.writeTileAssetKey(stack, tileAssetKey)
            if (!player.inventory.insertStack(stack)) {
                player.dropItem(stack, false)
            }
            playSound(SoundEvents.ENTITY_ITEM_PICKUP, 1.0f, 1.0f)
            discard()
        } else {
            tilePose = tilePose.next()
        }
        return ActionResult.CONSUME
    }

    /** 初始化 client/server 同步的牌面與姿態。 */
    override fun initDataTracker() {
        dataTracker.startTracking(TILE_ASSET_KEY, UNKNOWN_TILE_ASSET_KEY)
        dataTracker.startTracking(TILE_POSE, MahjongTilePose.STANDING.ordinal)
    }

    /** 從世界存檔還原牌面與姿態，非法值使用安全預設。 */
    override fun readCustomDataFromNbt(nbt: NbtCompound) {
        tileAssetKey = nbt.getString(NBT_KEY_TILE)
        tilePose = MahjongTilePose.fromNameOrDefault(nbt.getString(NBT_KEY_POSE))
    }

    /** 將牌面與姿態寫入世界存檔。 */
    override fun writeCustomDataToNbt(nbt: NbtCompound) {
        nbt.putString(NBT_KEY_TILE, tileAssetKey)
        nbt.putString(NBT_KEY_POSE, tilePose.name)
    }

    companion object {
        /** 舊版驗證過的麻將牌世界縮放。 */
        const val TILE_SCALE = 0.15f

        /** 麻將牌世界寬度。 */
        const val TILE_WIDTH = 12.0f / 16.0f * TILE_SCALE

        /** 麻將牌世界高度。 */
        const val TILE_HEIGHT = 16.0f / 16.0f * TILE_SCALE

        /** 麻將牌世界深度。 */
        const val TILE_DEPTH = 8.0f / 16.0f * TILE_SCALE

        /** 牌面世界存檔 key。 */
        private const val NBT_KEY_TILE = "Tile"

        /** 姿態世界存檔 key。 */
        private const val NBT_KEY_POSE = "Pose"

        /** 同步牌面素材 key。 */
        private val TILE_ASSET_KEY: TrackedData<String> =
            DataTracker.registerData(MahjongTileEntity::class.java, TrackedDataHandlerRegistry.STRING)

        /** 同步姿態 ordinal；持久化仍使用名稱以避免 enum 重排影響存檔。 */
        private val TILE_POSE: TrackedData<Int> =
            DataTracker.registerData(MahjongTileEntity::class.java, TrackedDataHandlerRegistry.INTEGER)
    }
}
