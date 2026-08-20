package com.doublemoon1119.mahjongcraft.platform.fabric.entity

import com.doublemoon1119.mahjongcraft.platform.fabric.item.MahjongScoringStickItem
import com.doublemoon1119.mahjongcraft.platform.fabric.registry.ModEntities
import com.doublemoon1119.mahjongcraft.platform.fabric.registry.ModItems
import com.doublemoon1119.mahjongcraft.platform.minecraft.stick.MahjongScoringStickDimensions
import net.minecraft.entity.Entity
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
 * 可自由放置的麻將點棒 entity。
 *
 * 正式牌局接線後代表立直棒（千分棒，放在牌河前方）與積棒（百分棒，放在莊家副露右側），管理中的
 * entity 由 [managedByGame]／[managedTableId] 區分自由放置模式，比照 [MahjongTileEntity] 的雙態設計。
 */
class MahjongScoringStickEntity(
    type: EntityType<out MahjongScoringStickEntity> = ModEntities.mahjongScoringStick,
    world: World,
) : Entity(type, world) {
    /** 目前的點棒面額。 */
    var denomination: MahjongScoringStickDenomination
        get() = MahjongScoringStickDenomination.fromOrdinalOrDefault(dataTracker[DENOMINATION])
        set(value) = dataTracker.set(DENOMINATION, value.ordinal)

    /** 是否由正式牌局管理；管理中的點棒不接受自由放置互動。 */
    var managedByGame: Boolean
        get() = dataTracker[MANAGED_BY_GAME]
        set(value) = dataTracker.set(MANAGED_BY_GAME, value)

    /** 正式牌局所屬麻將桌；自由放置點棒為 null。 */
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
     * `MahjongDiceEntity.firstTickWorldTime` 完全一致（避免世界重新載入一次補跑大量 tick 時誤判成
     * 早就該消失了）。
     */
    private var firstTickWorldTime: Long = Long.MIN_VALUE

    /**
     * 管理中積棒的 fallback 自動清除——**不是主要清除路徑**。積棒的正常生命週期是跟牌牆同時生成、
     * 每次換局由 `FabricMahjongScoringStickPresenter.present()` 在新積棒生成成功後才刪除舊的，或由
     * 對局結束的顯式清除觸發；這裡只是意外情境（伺服器崩潰、對局非正常結束導致沒有機會走到正常清除
     * 流程）的保險，門檻抓得遠大於正常一局遊戲時長，不能像 `MahjongDiceEntity` 那樣抓短動畫時長——
     * 積棒沒有「動畫播完」的概念，太短的門檻會在正常對局進行中就把還在使用的積棒清掉。
     */
    override fun tick() {
        super.tick()
        if (world.isClient) return
        if (firstTickWorldTime == Long.MIN_VALUE) firstTickWorldTime = world.time
        if (managedByGame && world.time - firstTickWorldTime >= FALLBACK_DESPAWN_AFTER_TICKS) {
            discard()
        }
    }

    /** 點棒扁平輕薄，不提供物理阻擋，僅供視線選取。 */
    override fun isCollidable(): Boolean = false

    /** 點棒不參與一般 entity 推擠。 */
    override fun isPushable(): Boolean = false

    /** 允許玩家視線 raycast 選取點棒，以執行右鍵互動。 */
    override fun canHit(): Boolean = !isRemoved

    /** 允許自由放置點棒成為左鍵回收目標。 */
    override fun isAttackable(): Boolean = !isRemoved && !managedByGame

    /** 右鍵自由放置點棒時循環面額；管理中的點棒不接受此互動。 */
    override fun interact(player: PlayerEntity, hand: Hand): ActionResult {
        if (managedByGame) return ActionResult.PASS
        if (world.isClient) return ActionResult.SUCCESS
        denomination = denomination.next()
        return ActionResult.CONSUME
    }

    /** 左鍵回收自由放置點棒；創造模式只移除 entity。管理中的點棒不能被回收。 */
    override fun handleAttack(attacker: Entity): Boolean {
        if (managedByGame) return false
        val player = attacker as? PlayerEntity ?: return false
        if (!world.isClient) {
            if (!player.abilities.creativeMode) dropStack(asItemStack())
            playSound(SoundEvents.ENTITY_ITEM_FRAME_BREAK, 1.0f, 1.0f)
            discard()
        }
        return true
    }

    /** 將點棒標記為指定正式牌局桌子管理。 */
    fun assignToTable(tableId: Uuid) {
        check(!world.isClient) { "Managed scoring sticks must be assigned by the server" }
        managedByGame = true
        managedTableId = tableId
    }

    /** 建立保留目前面額的點棒物品。 */
    private fun asItemStack(): ItemStack = ItemStack(ModItems.MAHJONG_SCORING_STICK).also {
        MahjongScoringStickItem.writeDenomination(it, denomination)
    }

    /** 初始化 client/server 同步的面額與管理狀態。 */
    override fun initDataTracker() {
        dataTracker.startTracking(DENOMINATION, MahjongScoringStickDenomination.P100.ordinal)
        dataTracker.startTracking(MANAGED_BY_GAME, false)
        dataTracker.startTracking(MANAGED_TABLE_ID, "")
    }

    /** 從世界存檔還原面額與管理狀態；非法值使用安全預設。 */
    override fun readCustomDataFromNbt(nbt: NbtCompound) {
        denomination = MahjongScoringStickDenomination.fromOrdinalOrDefault(nbt.getInt(NBT_KEY_DENOMINATION))
        managedByGame = nbt.getBoolean(NBT_KEY_MANAGED_BY_GAME)
        managedTableId = nbt.getString(NBT_KEY_MANAGED_TABLE_ID)
            .takeIf(String::isNotBlank)
            ?.let { encoded -> runCatching { Uuid.parse(encoded) }.getOrNull() }
    }

    /** 將面額與管理狀態寫入世界存檔。 */
    override fun writeCustomDataToNbt(nbt: NbtCompound) {
        nbt.putInt(NBT_KEY_DENOMINATION, denomination.ordinal)
        nbt.putBoolean(NBT_KEY_MANAGED_BY_GAME, managedByGame)
        managedTableId?.let { tableId -> nbt.putString(NBT_KEY_MANAGED_TABLE_ID, tableId.toString()) }
    }

    companion object {
        /** 點棒世界寬度；與 [MahjongScoringStickDimensions] 共用同一組數值來源。 */
        val STICK_WIDTH = MahjongScoringStickDimensions.STICK_WIDTH.toFloat()

        /** 點棒世界高度（厚度）。 */
        val STICK_HEIGHT = MahjongScoringStickDimensions.STICK_HEIGHT.toFloat()

        /** 點棒世界深度。 */
        val STICK_DEPTH = MahjongScoringStickDimensions.STICK_DEPTH.toFloat()

        /** 面額世界存檔 key。 */
        private const val NBT_KEY_DENOMINATION = "Denomination"

        /** 正式牌局管理狀態世界存檔 key。 */
        private const val NBT_KEY_MANAGED_BY_GAME = "ManagedByGame"

        /** 正式點棒所屬桌子 UUID 的世界存檔 key。 */
        private const val NBT_KEY_MANAGED_TABLE_ID = "ManagedTableId"

        /**
         * 管理中積棒的 fallback 自動清除門檻（見 [tick]）——1 小時份的 tick 數，遠大於正常一局遊戲
         * 時長，只用來兜底意外情境，不影響正常對局的積棒顯示。
         */
        private const val FALLBACK_DESPAWN_AFTER_TICKS = 20L * 60L * 60L

        /** 同步目前面額 ordinal。 */
        private val DENOMINATION: TrackedData<Int> =
            DataTracker.registerData(MahjongScoringStickEntity::class.java, TrackedDataHandlerRegistry.INTEGER)

        /** 同步是否由正式牌局管理。 */
        private val MANAGED_BY_GAME: TrackedData<Boolean> =
            DataTracker.registerData(MahjongScoringStickEntity::class.java, TrackedDataHandlerRegistry.BOOLEAN)

        /** 同步正式點棒所屬麻將桌 UUID；空字串表示自由放置。 */
        private val MANAGED_TABLE_ID: TrackedData<String> =
            DataTracker.registerData(MahjongScoringStickEntity::class.java, TrackedDataHandlerRegistry.STRING)
    }
}
