package com.doublemoon1119.mahjongcraft.platform.fabric.entity

import com.doublemoon1119.mahjongcraft.logic.base.Tile
import com.doublemoon1119.mahjongcraft.platform.fabric.registry.ModEntities
import com.doublemoon1119.mahjongcraft.platform.minecraft.tile.BuiltInMinecraftTileAppearances
import com.doublemoon1119.mahjongcraft.platform.minecraft.tile.UNKNOWN_TILE_ASSET_KEY
import net.minecraft.entity.Entity
import net.minecraft.entity.EntityType
import net.minecraft.entity.data.DataTracker
import net.minecraft.entity.data.TrackedDataHandlerRegistry
import net.minecraft.nbt.NbtCompound
import net.minecraft.world.World
import kotlin.uuid.Uuid

/**
 * 只有公開牌面的桌面展示副本，不屬於手牌、牌河或牌山，也不接受點牌、回收與推擠。
 *
 * 每個副本有自己的實體 UUID；公開材質與外觀直接同步，不能用正式牌張的 UUID 建立副本。
 * 位置及牌種是可重建資料，由桌面物件同步流程在載入後核對。
 *
 * @param type Minecraft 實體類型。
 * @param world 實體所屬世界。
 */
class TablePropTileEntity(
    type: EntityType<out TablePropTileEntity> = ModEntities.tablePropTile,
    world: World,
) : Entity(type, world) {
    /** 所屬桌子；未指派或保存資料無效時為 null。 */
    var managedTableId: Uuid? = null

    /** 規則給定的穩定物件 ID；不使用正式牌張身分。 */
    var placementId: String = ""

    /** 建立此展示物件的規則模組。 */
    var tablePropRuleModuleId: String = ""

    /** 伺服器用來核對內容的公開牌種，不從客戶端接收。 */
    var tile: Tile? = null

    /** 所有觀察者都可見的牌面資產。 */
    var assetKey: String
        get() = dataTracker[ASSET_KEY]
        set(value) = dataTracker.set(ASSET_KEY, value)

    /** 所屬規則選用的公開牌組外觀 ID。 */
    var appearanceId: String
        get() = dataTracker[APPEARANCE_ID]
        set(value) = dataTracker.set(APPEARANCE_ID, value)

    init {
        setNoGravity(true)
        noClip = true
    }

    /** 展示副本不阻擋物件。 */
    override fun isCollidable(): Boolean = false

    /** 展示副本不參與推擠。 */
    override fun isPushable(): Boolean = false

    /** 展示副本不能成為選牌或攻擊目標。 */
    override fun canHit(): Boolean = false

    /** 初始化只含公開資料的同步欄位。 */
    override fun initDataTracker() {
        dataTracker.startTracking(ASSET_KEY, UNKNOWN_TILE_ASSET_KEY)
        dataTracker.startTracking(APPEARANCE_ID, BuiltInMinecraftTileAppearances.DEFAULT.id)
    }

    /**
     * 保存可重建的物件身分與公開內容。
     *
     * @param nbt 接收資料的實體存檔。
     */
    override fun writeCustomDataToNbt(nbt: NbtCompound) {
        ManagedTableNbt.write(nbt, managedTableId)
        TablePropIdentityNbt.write(nbt, placementId, tablePropRuleModuleId)
        tile?.let { nbt.putString(NBT_KEY_TILE, TablePropTileCodec.encode(it)) }
        nbt.putString(NBT_KEY_ASSET_KEY, assetKey)
        nbt.putString(NBT_KEY_APPEARANCE_ID, appearanceId)
    }

    /**
     * 還原公開衍生資料；身分或牌種無法解碼時由下一次同步移除重建。
     *
     * @param nbt 已保存的實體資料。
     */
    override fun readCustomDataFromNbt(nbt: NbtCompound) {
        managedTableId = ManagedTableNbt.read(nbt)
        val identity = TablePropIdentityNbt.read(nbt)
        placementId = identity?.placementId.orEmpty()
        tablePropRuleModuleId = identity?.ruleModuleId.orEmpty()
        tile = TablePropTileCodec.decode(nbt.getString(NBT_KEY_TILE))
        assetKey = nbt.getString(NBT_KEY_ASSET_KEY).ifBlank { UNKNOWN_TILE_ASSET_KEY }
        appearanceId = nbt.getString(NBT_KEY_APPEARANCE_ID).ifBlank { BuiltInMinecraftTileAppearances.DEFAULT.id }
    }

    private companion object {
        /** 保存公開展示的牌種。 */
        const val NBT_KEY_TILE = "Tile"

        /** 保存已公開的牌面資產。 */
        const val NBT_KEY_ASSET_KEY = "AssetKey"

        /** 保存所屬規則選用的公開外觀。 */
        const val NBT_KEY_APPEARANCE_ID = "AppearanceId"

        /** 只同步已公開的牌面資產。 */
        val ASSET_KEY = DataTracker.registerData(TablePropTileEntity::class.java, TrackedDataHandlerRegistry.STRING)

        /** 同步完整牌組外觀 ID。 */
        val APPEARANCE_ID = DataTracker.registerData(TablePropTileEntity::class.java, TrackedDataHandlerRegistry.STRING)
    }
}
