package com.doublemoon1119.mahjongcraft.platform.fabric.entity

import com.doublemoon1119.mahjongcraft.logic.table.Wind
import com.doublemoon1119.mahjongcraft.platform.fabric.registry.ModEntities
import net.minecraft.entity.Entity
import net.minecraft.entity.EntityType
import net.minecraft.entity.data.DataTracker
import net.minecraft.entity.data.TrackedData
import net.minecraft.entity.data.TrackedDataHandlerRegistry
import net.minecraft.nbt.NbtCompound
import net.minecraft.world.World
import kotlin.uuid.Uuid

/**
 * 桌面中央局況顯示用的純視覺 entity——只同步組成畫面內容需要的原始數值（場風、場風內局數、本場數、
 * 牌山剩餘），不同步已經翻譯好的文字——翻譯必須在 client 端依各自語系解析，不能在 server 端就烘焙成
 * 固定語言的字串，見 `FabricMahjongRoundInfoPresenter`／`MahjongRoundInfoEntityRenderer` 的分工：
 * server 只決定「數值」，client renderer 才決定「怎麼翻譯成文字」。
 *
 * 每張桌子固定只有一個，不像牌／點棒有多個 UUID 各自代表獨立的牌局物件，因此不需要
 * `managedByGame`／`managedTableId` 以外的識別欄位——找既有 entity 時直接依 [managedTableId] 搜尋
 * 即可，見 `FabricMahjongRoundInfoPresenter`。
 */
class MahjongRoundInfoEntity(
    type: EntityType<out MahjongRoundInfoEntity> = ModEntities.mahjongRoundInfo,
    world: World,
) : Entity(type, world) {
    /** 目前場風（圈風）。 */
    var prevalentWind: Wind
        get() = Wind.entries.getOrElse(dataTracker[PREVALENT_WIND_ORDINAL]) { Wind.EAST }
        set(value) = dataTracker.set(PREVALENT_WIND_ORDINAL, value.ordinal)

    /** 目前場風內的第幾局（`1` 起算）。 */
    var localRoundNumber: Int
        get() = dataTracker[LOCAL_ROUND_NUMBER]
        set(value) = dataTracker.set(LOCAL_ROUND_NUMBER, value)

    /** 目前本場數（連莊次數）。 */
    var comboCount: Int
        get() = dataTracker[COMBO_COUNT]
        set(value) = dataTracker.set(COMBO_COUNT, value)

    /** 牌山目前剩餘張數。 */
    var wallRemainingCount: Int
        get() = dataTracker[WALL_REMAINING_COUNT]
        set(value) = dataTracker.set(WALL_REMAINING_COUNT, value)

    /** 正式牌局所屬麻將桌；未指派時為 `null`。 */
    var managedTableId: Uuid?
        get() = dataTracker[MANAGED_TABLE_ID]
            .takeIf(String::isNotBlank)
            ?.let { encoded -> runCatching { Uuid.parse(encoded) }.getOrNull() }
        private set(value) = dataTracker.set(MANAGED_TABLE_ID, value?.toString().orEmpty())

    init {
        setNoGravity(true)
    }

    /**
     * 這個 entity 物件在記憶體裡第一次被 tick 到的 `world.time`；只在記憶體內，不寫進存檔，理由跟
     * [MahjongScoringStickEntity.firstTickWorldTime] 完全一致（避免世界重新載入一次補跑大量 tick 時
     * 誤判成早就該消失了）。
     */
    private var firstTickWorldTime: Long = Long.MIN_VALUE

    /**
     * fallback 自動清除——**不是主要清除路徑**。正常生命週期是跟牌牆同時生成、換局時由
     * `FabricMahjongRoundInfoPresenter.present()` 找到既有 entity 就地更新內容（不重新生成），或由
     * `FabricGamePresentationPublisher.clearPlayerAreas()`（回房間等情境）顯式清除；這裡只是意外情境
     * （伺服器崩潰、對局非正常結束導致沒有機會走到正常清除流程）的保險，門檻抓得遠大於正常一局遊戲
     * 時長，理由同 [MahjongScoringStickEntity.tick] 的同款設計——這個 entity 沒有自由放置模式（一律
     * 由牌局管理），不需要像點棒那樣額外判斷 `managedByGame`。
     */
    override fun tick() {
        super.tick()
        if (world.isClient) return
        if (firstTickWorldTime == Long.MIN_VALUE) firstTickWorldTime = world.time
        if (world.time - firstTickWorldTime >= FALLBACK_DESPAWN_AFTER_TICKS) {
            discard()
        }
    }

    /** 純視覺物件，不提供物理阻擋、不參與推擠、不能被互動選取。 */
    override fun isCollidable(): Boolean = false

    override fun isPushable(): Boolean = false

    override fun canHit(): Boolean = false

    /** 將 entity 標記為指定正式牌局桌子管理。 */
    fun assignToTable(tableId: Uuid) {
        check(!world.isClient) { "Managed round info display must be assigned by the server" }
        managedTableId = tableId
    }

    override fun initDataTracker() {
        dataTracker.startTracking(PREVALENT_WIND_ORDINAL, Wind.EAST.ordinal)
        dataTracker.startTracking(LOCAL_ROUND_NUMBER, 1)
        dataTracker.startTracking(COMBO_COUNT, 0)
        dataTracker.startTracking(WALL_REMAINING_COUNT, 0)
        dataTracker.startTracking(MANAGED_TABLE_ID, "")
    }

    override fun readCustomDataFromNbt(nbt: NbtCompound) {
        prevalentWind = Wind.entries.getOrElse(nbt.getInt(NBT_KEY_PREVALENT_WIND_ORDINAL)) { Wind.EAST }
        localRoundNumber = nbt.getInt(NBT_KEY_LOCAL_ROUND_NUMBER).coerceAtLeast(1)
        comboCount = nbt.getInt(NBT_KEY_COMBO_COUNT)
        wallRemainingCount = nbt.getInt(NBT_KEY_WALL_REMAINING_COUNT)
        managedTableId = nbt.getString(NBT_KEY_MANAGED_TABLE_ID)
            .takeIf(String::isNotBlank)
            ?.let { encoded -> runCatching { Uuid.parse(encoded) }.getOrNull() }
    }

    override fun writeCustomDataToNbt(nbt: NbtCompound) {
        nbt.putInt(NBT_KEY_PREVALENT_WIND_ORDINAL, prevalentWind.ordinal)
        nbt.putInt(NBT_KEY_LOCAL_ROUND_NUMBER, localRoundNumber)
        nbt.putInt(NBT_KEY_COMBO_COUNT, comboCount)
        nbt.putInt(NBT_KEY_WALL_REMAINING_COUNT, wallRemainingCount)
        managedTableId?.let { tableId -> nbt.putString(NBT_KEY_MANAGED_TABLE_ID, tableId.toString()) }
    }

    companion object {
        private const val NBT_KEY_PREVALENT_WIND_ORDINAL = "PrevalentWindOrdinal"
        private const val NBT_KEY_LOCAL_ROUND_NUMBER = "LocalRoundNumber"
        private const val NBT_KEY_COMBO_COUNT = "ComboCount"
        private const val NBT_KEY_WALL_REMAINING_COUNT = "WallRemainingCount"
        private const val NBT_KEY_MANAGED_TABLE_ID = "ManagedTableId"

        /**
         * fallback 自動清除門檻（見 [tick]）——1 小時份的 tick 數，遠大於正常一局遊戲時長，只用來
         * 兜底意外情境，不影響正常對局的局況顯示。
         */
        private const val FALLBACK_DESPAWN_AFTER_TICKS = 20L * 60L * 60L

        /** 同步目前場風 ordinal。 */
        private val PREVALENT_WIND_ORDINAL: TrackedData<Int> =
            DataTracker.registerData(MahjongRoundInfoEntity::class.java, TrackedDataHandlerRegistry.INTEGER)

        /** 同步目前場風內局數。 */
        private val LOCAL_ROUND_NUMBER: TrackedData<Int> =
            DataTracker.registerData(MahjongRoundInfoEntity::class.java, TrackedDataHandlerRegistry.INTEGER)

        /** 同步目前本場數。 */
        private val COMBO_COUNT: TrackedData<Int> =
            DataTracker.registerData(MahjongRoundInfoEntity::class.java, TrackedDataHandlerRegistry.INTEGER)

        /** 同步牌山目前剩餘張數。 */
        private val WALL_REMAINING_COUNT: TrackedData<Int> =
            DataTracker.registerData(MahjongRoundInfoEntity::class.java, TrackedDataHandlerRegistry.INTEGER)

        /** 同步所屬麻將桌 UUID；空字串表示尚未指派。 */
        private val MANAGED_TABLE_ID: TrackedData<String> =
            DataTracker.registerData(MahjongRoundInfoEntity::class.java, TrackedDataHandlerRegistry.STRING)
    }
}
