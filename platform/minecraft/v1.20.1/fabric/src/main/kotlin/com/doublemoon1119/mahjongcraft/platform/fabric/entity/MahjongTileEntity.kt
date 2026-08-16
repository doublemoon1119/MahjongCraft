package com.doublemoon1119.mahjongcraft.platform.fabric.entity

import com.doublemoon1119.mahjongcraft.platform.fabric.item.MahjongTileItem
import com.doublemoon1119.mahjongcraft.platform.fabric.registry.ModEntities
import com.doublemoon1119.mahjongcraft.platform.fabric.registry.ModItems
import com.doublemoon1119.mahjongcraft.platform.minecraft.tile.MahjongTileDimensions
import com.doublemoon1119.mahjongcraft.platform.minecraft.tile.UNKNOWN_TILE_ASSET_KEY
import com.doublemoon1119.mahjongcraft.platform.minecraft.tile.nextTileAssetKey
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
import kotlin.uuid.Uuid

/**
 * 可自由放置的麻將牌 entity。
 *
 * 正式牌局接線後，權威 [IdentifiedTile][com.doublemoon1119.mahjongcraft.logic.base.IdentifiedTile] 的
 * ID 會直接使用此 entity 的 UUID，不在 entity 內複製麻將規則狀態；牌局管理中的 entity 由
 * [managedByGame]／[managedTableId] 區分自由放置模式，比照 [MahjongDiceEntity] 的雙態設計。
 */
class MahjongTileEntity(
    type: EntityType<out MahjongTileEntity> = ModEntities.mahjongTile,
    world: World,
) : Entity(type, world) {
    /**
     * 牌面素材 key；外部輸入會正規化為支援值或 `unknown`。
     *
     * 牌局管理中的 entity（[managedByGame] 為 `true`）恆定為 [UNKNOWN_TILE_ASSET_KEY]，不寫入真正
     * 牌面——tracked data 會廣播給所有 tracking 到這個 entity 的 client，沒辦法只給特定玩家看到真牌
     * 面，真正牌面完全交給 client 端依 `TableStateSnapshot` 的可見性規則另外呈現。
     */
    var tileAssetKey: String
        get() = dataTracker[TILE_ASSET_KEY].normalizedTileAssetKey()
        set(value) {
            val normalized = if (managedByGame) UNKNOWN_TILE_ASSET_KEY else value.normalizedTileAssetKey()
            dataTracker.set(TILE_ASSET_KEY, normalized)
        }

    /** 牌相對於表面的姿態；改變後立即更新 bounding box。 */
    var tilePose: MahjongTilePose
        get() = MahjongTilePose.fromOrdinalOrDefault(dataTracker[TILE_POSE])
        set(value) = dataTracker.set(TILE_POSE, value.ordinal)

    /** 是否阻擋玩家及其他非麻將牌 entity；由目前 server config 同步，不寫入世界存檔。 */
    var physicalCollisionEnabled: Boolean
        get() = dataTracker[PHYSICAL_COLLISION_ENABLED]
        set(value) = dataTracker.set(PHYSICAL_COLLISION_ENABLED, value)

    /** 是否由正式牌局管理；管理中的牌不接受自由放置互動。 */
    var managedByGame: Boolean
        get() = dataTracker[MANAGED_BY_GAME]
        set(value) = dataTracker.set(MANAGED_BY_GAME, value)

    /** 正式牌局所屬麻將桌；自由放置牌為 null。 */
    var managedTableId: Uuid?
        get() = dataTracker[MANAGED_TABLE_ID]
            .takeIf(String::isNotBlank)
            ?.let { encoded -> runCatching { Uuid.parse(encoded) }.getOrNull() }
        private set(value) = dataTracker.set(MANAGED_TABLE_ID, value?.toString().orEmpty())

    init {
        setNoGravity(true)
    }

    /** 將牌標記為指定正式牌局桌子管理；[tileAssetKey] 的 setter 會因此自動鎖定為 [UNKNOWN_TILE_ASSET_KEY]。 */
    fun assignToTable(tableId: Uuid) {
        check(!world.isClient) { "Managed tiles must be assigned by the server" }
        managedByGame = true
        managedTableId = tableId
    }

    /** 依 server policy 決定是否提供物理阻擋；raycast 選取能力由 [canHit] 獨立保留。 */
    override fun isCollidable(): Boolean = physicalCollisionEnabled

    /** 麻將牌彼此永遠不產生物理碰撞，其他 entity 則依目前 server policy 與原版規則處理。 */
    override fun collidesWith(other: Entity): Boolean = physicalCollisionEnabled &&
        other !is MahjongTileEntity &&
        super.collidesWith(other)

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

    /** 普通右鍵循環牌面；蹲下右鍵循環姿態。牌局管理中的牌不接受此互動。 */
    override fun interact(player: PlayerEntity, hand: Hand): ActionResult {
        if (managedByGame) return ActionResult.PASS
        if (world.isClient) return ActionResult.SUCCESS

        if (player.isSneaking) {
            tilePose = tilePose.next()
        } else {
            tileAssetKey = tileAssetKey.nextTileAssetKey()
        }
        return ActionResult.CONSUME
    }

    /** 玩家左鍵攻擊時回收牌張；生存模式掉落保留牌面的物品，創造模式只移除 entity。牌局管理中的牌不能被回收。 */
    override fun handleAttack(attacker: Entity): Boolean {
        if (managedByGame) return false
        val player = attacker as? PlayerEntity ?: return false
        if (!world.isClient) {
            if (!player.abilities.creativeMode) {
                dropStack(asItemStack())
            }
            playSound(SoundEvents.ENTITY_ITEM_FRAME_BREAK, 1.0f, 1.0f)
            discard()
        }
        return true
    }

    /** 建立保留目前牌面 asset key 的麻將牌物品。 */
    private fun asItemStack(): ItemStack = ItemStack(ModItems.MAHJONG_TILE).also {
        MahjongTileItem.writeTileAssetKey(it, tileAssetKey)
    }

    /** 初始化 client/server 同步的牌面、姿態與管理狀態。 */
    override fun initDataTracker() {
        dataTracker.startTracking(TILE_ASSET_KEY, UNKNOWN_TILE_ASSET_KEY)
        dataTracker.startTracking(TILE_POSE, MahjongTilePose.STANDING.ordinal)
        dataTracker.startTracking(PHYSICAL_COLLISION_ENABLED, true)
        dataTracker.startTracking(MANAGED_BY_GAME, false)
        dataTracker.startTracking(MANAGED_TABLE_ID, "")
    }

    /** 從世界存檔還原牌面、姿態與管理狀態，非法值使用安全預設。 */
    override fun readCustomDataFromNbt(nbt: NbtCompound) {
        managedByGame = nbt.getBoolean(NBT_KEY_MANAGED_BY_GAME)
        managedTableId = nbt.getString(NBT_KEY_MANAGED_TABLE_ID)
            .takeIf(String::isNotBlank)
            ?.let { encoded -> runCatching { Uuid.parse(encoded) }.getOrNull() }
        tileAssetKey = nbt.getString(NBT_KEY_TILE)
        tilePose = MahjongTilePose.fromNameOrDefault(nbt.getString(NBT_KEY_POSE))
    }

    /** 將牌面、姿態與管理狀態寫入世界存檔。 */
    override fun writeCustomDataToNbt(nbt: NbtCompound) {
        nbt.putString(NBT_KEY_TILE, tileAssetKey)
        nbt.putString(NBT_KEY_POSE, tilePose.name)
        nbt.putBoolean(NBT_KEY_MANAGED_BY_GAME, managedByGame)
        managedTableId?.let { tableId -> nbt.putString(NBT_KEY_MANAGED_TABLE_ID, tableId.toString()) }
    }

    companion object {
        /** 麻將牌世界寬度；與 [MahjongTileTableLayout][com.doublemoon1119.mahjongcraft.platform.minecraft.tile.MahjongTileTableLayout] 共用同一組數值來源。 */
        val TILE_WIDTH = MahjongTileDimensions.TILE_WIDTH.toFloat()

        /** 麻將牌世界高度。 */
        val TILE_HEIGHT = MahjongTileDimensions.TILE_HEIGHT.toFloat()

        /** 麻將牌世界深度。 */
        val TILE_DEPTH = MahjongTileDimensions.TILE_DEPTH.toFloat()

        /** 牌面世界存檔 key。 */
        private const val NBT_KEY_TILE = "Tile"

        /** 姿態世界存檔 key。 */
        private const val NBT_KEY_POSE = "Pose"

        /** 正式牌局管理狀態世界存檔 key。 */
        private const val NBT_KEY_MANAGED_BY_GAME = "ManagedByGame"

        /** 正式牌局所屬桌子 UUID 的世界存檔 key。 */
        private const val NBT_KEY_MANAGED_TABLE_ID = "ManagedTableId"

        /** 同步牌面素材 key。 */
        private val TILE_ASSET_KEY: TrackedData<String> =
            DataTracker.registerData(MahjongTileEntity::class.java, TrackedDataHandlerRegistry.STRING)

        /** 同步姿態 ordinal；持久化仍使用名稱以避免 enum 重排影響存檔。 */
        private val TILE_POSE: TrackedData<Int> =
            DataTracker.registerData(MahjongTileEntity::class.java, TrackedDataHandlerRegistry.INTEGER)

        /** 同步目前 server policy 決定的物理碰撞開關。 */
        private val PHYSICAL_COLLISION_ENABLED: TrackedData<Boolean> =
            DataTracker.registerData(MahjongTileEntity::class.java, TrackedDataHandlerRegistry.BOOLEAN)

        /** 同步是否由正式牌局管理。 */
        private val MANAGED_BY_GAME: TrackedData<Boolean> =
            DataTracker.registerData(MahjongTileEntity::class.java, TrackedDataHandlerRegistry.BOOLEAN)

        /** 同步正式牌局所屬麻將桌 UUID；空字串表示自由放置。 */
        private val MANAGED_TABLE_ID: TrackedData<String> =
            DataTracker.registerData(MahjongTileEntity::class.java, TrackedDataHandlerRegistry.STRING)
    }
}
