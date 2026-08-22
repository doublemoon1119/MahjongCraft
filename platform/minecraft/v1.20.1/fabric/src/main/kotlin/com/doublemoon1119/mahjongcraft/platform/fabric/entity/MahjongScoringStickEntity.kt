package com.doublemoon1119.mahjongcraft.platform.fabric.entity

import com.doublemoon1119.mahjongcraft.platform.fabric.item.MahjongScoringStickItem
import com.doublemoon1119.mahjongcraft.platform.fabric.registry.ModEntities
import com.doublemoon1119.mahjongcraft.platform.fabric.registry.ModItems
import com.doublemoon1119.mahjongcraft.platform.minecraft.animation.AnimationStep
import com.doublemoon1119.mahjongcraft.platform.minecraft.dice.DiceAnimationVector
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
) : AnimatedMahjongEntity<Nothing>(type, world) {
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

    /** 是否正在播放由伺服器啟動的掉落動畫，理由同 [MahjongTileEntity.animating]。 */
    var animating: Boolean
        get() = dataTracker[ANIMATING]
        private set(value) = dataTracker.set(ANIMATING, value)

    /** 動畫開始時的 server game time。 */
    val animationStartGameTime: Long
        get() = dataTracker[ANIMATION_START_GAME_TIME]

    /** 動畫總長度，以 server ticks 表示。 */
    val animationDurationTicks: Int
        get() = dataTracker[ANIMATION_DURATION_TICKS]

    /** 拋物線最高額外高度；點棒掉落固定為純直落，恆為 `0`。 */
    val animationArcHeight: Double
        get() = dataTracker[ANIMATION_ARC_HEIGHT].toDouble()

    /** 動畫起點相對 entity 最終位置的向量。 */
    val animationStartOffset: DiceAnimationVector
        get() = DiceAnimationVector(
            x = dataTracker[ANIMATION_START_OFFSET_X].toDouble(),
            y = dataTracker[ANIMATION_START_OFFSET_Y].toDouble(),
            z = dataTracker[ANIMATION_START_OFFSET_Z].toDouble(),
        )

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

    /**
     * 排定落下動畫，供積棒／立直棒的 presenter 共用——呼叫前應已經 `refreshPositionAndAngles` 到最終
     * 落點，真實座標全程不變，只有 render 端的視覺位移從 [DROP_HEIGHT] 內插回 `0`，手法比照
     * `FabricMahjongTileWallPresenter.startWallDropAnimations` 牌牆掉落動畫（已在最終位置、`startOffsetY`
     * 為正值即可，不需要像寶牌揭示動畫那樣額外 `Teleport` 到半空起點——那是因為寶牌指示牌揭示前已經
     * 停在牌牆位置，需要先「真的」抬起來才能翻面；積棒是全新生成的 entity，從一開始就能直接以視覺
     * 位移表達「從上方落下」，不需要移動真實座標）。
     */
    fun enqueueDropAnimation() {
        check(!world.isClient) { "Scoring stick drop animation must be started by the server" }
        enqueue(
            AnimationStep.PlayMotion(
                durationTicks = DROP_DURATION_TICKS,
                arcHeight = 0.0,
                startOffsetX = 0.0,
                startOffsetY = DROP_HEIGHT,
                startOffsetZ = 0.0,
                startPoseRotationDegrees = 0.0f,
                endPoseRotationDegrees = 0.0f,
            ),
        )
    }

    /** 依 [AnimationStep.PlayMotion] 同步既有的 render 用 tracked data 欄位，理由同 [MahjongTileEntity.applyPlayMotion]。 */
    override fun applyPlayMotion(step: AnimationStep.PlayMotion, startGameTime: Long) {
        dataTracker.set(ANIMATION_START_GAME_TIME, startGameTime)
        dataTracker.set(ANIMATION_DURATION_TICKS, step.durationTicks)
        dataTracker.set(ANIMATION_ARC_HEIGHT, step.arcHeight.toFloat())
        dataTracker.set(ANIMATION_START_OFFSET_X, step.startOffsetX.toFloat())
        dataTracker.set(ANIMATION_START_OFFSET_Y, step.startOffsetY.toFloat())
        dataTracker.set(ANIMATION_START_OFFSET_Z, step.startOffsetZ.toFloat())
        animating = true
    }

    /** 動畫播完，render 端不再需要內插，實際邏輯位置早已是 [enqueueDropAnimation] 呼叫前設定好的終點。 */
    override fun onPlayMotionCompleted() {
        animating = false
    }

    /** 點棒沒有專屬瞬間動作，這三個方法永遠不會被呼叫（[Nothing] 沒有任何實例）。 */
    override fun applyCustomStep(step: Nothing) = step

    override fun serializeCustomStep(step: Nothing, nbt: NbtCompound) = step

    override fun deserializeCustomStep(nbt: NbtCompound): Nothing = error("MahjongScoringStickEntity never enqueues AnimationStep.Custom")

    /** 建立保留目前面額的點棒物品。 */
    private fun asItemStack(): ItemStack = ItemStack(ModItems.MAHJONG_SCORING_STICK).also {
        MahjongScoringStickItem.writeDenomination(it, denomination)
    }

    /** 初始化 client/server 同步的面額、管理狀態與動畫欄位。 */
    override fun initDataTracker() {
        dataTracker.startTracking(DENOMINATION, MahjongScoringStickDenomination.P100.ordinal)
        dataTracker.startTracking(MANAGED_BY_GAME, false)
        dataTracker.startTracking(MANAGED_TABLE_ID, "")
        dataTracker.startTracking(ANIMATING, false)
        dataTracker.startTracking(ANIMATION_START_GAME_TIME, 0L)
        dataTracker.startTracking(ANIMATION_DURATION_TICKS, 1)
        dataTracker.startTracking(ANIMATION_ARC_HEIGHT, 0.0f)
        dataTracker.startTracking(ANIMATION_START_OFFSET_X, 0.0f)
        dataTracker.startTracking(ANIMATION_START_OFFSET_Y, 0.0f)
        dataTracker.startTracking(ANIMATION_START_OFFSET_Z, 0.0f)
    }

    /**
     * 從世界存檔還原面額、管理狀態與動畫佇列，非法值使用安全預設。動畫佇列一併還原理由同
     * [MahjongTileEntity.readCustomDataFromNbt]。
     */
    override fun readCustomDataFromNbt(nbt: NbtCompound) {
        denomination = MahjongScoringStickDenomination.fromOrdinalOrDefault(nbt.getInt(NBT_KEY_DENOMINATION))
        managedByGame = nbt.getBoolean(NBT_KEY_MANAGED_BY_GAME)
        managedTableId = nbt.getString(NBT_KEY_MANAGED_TABLE_ID)
            .takeIf(String::isNotBlank)
            ?.let { encoded -> runCatching { Uuid.parse(encoded) }.getOrNull() }
        readAnimationQueueFromNbt(nbt)
    }

    /** 將面額、管理狀態與動畫佇列寫入世界存檔。 */
    override fun writeCustomDataToNbt(nbt: NbtCompound) {
        nbt.putInt(NBT_KEY_DENOMINATION, denomination.ordinal)
        nbt.putBoolean(NBT_KEY_MANAGED_BY_GAME, managedByGame)
        managedTableId?.let { tableId -> nbt.putString(NBT_KEY_MANAGED_TABLE_ID, tableId.toString()) }
        writeAnimationQueueToNbt(nbt)
    }

    companion object {
        /** 點棒世界寬度；與 [MahjongScoringStickDimensions] 共用同一組數值來源。 */
        val STICK_WIDTH = MahjongScoringStickDimensions.STICK_WIDTH.toFloat()

        /** 點棒世界高度（厚度）。 */
        val STICK_HEIGHT = MahjongScoringStickDimensions.STICK_HEIGHT.toFloat()

        /** 點棒世界深度。 */
        val STICK_DEPTH = MahjongScoringStickDimensions.STICK_DEPTH.toFloat()

        /** 點棒掉落動畫的半空起點高度，相對落點的世界 Y 偏移。 */
        const val DROP_HEIGHT: Double = 0.4

        /** 點棒掉落動畫的時長。 */
        const val DROP_DURATION_TICKS: Int = 6

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

        /** 同步是否正在播放掉落動畫。 */
        private val ANIMATING: TrackedData<Boolean> =
            DataTracker.registerData(MahjongScoringStickEntity::class.java, TrackedDataHandlerRegistry.BOOLEAN)

        /** 同步動畫開始的 server game time。 */
        private val ANIMATION_START_GAME_TIME: TrackedData<Long> =
            DataTracker.registerData(MahjongScoringStickEntity::class.java, TrackedDataHandlerRegistry.LONG)

        /** 同步動畫總長度（ticks）。 */
        private val ANIMATION_DURATION_TICKS: TrackedData<Int> =
            DataTracker.registerData(MahjongScoringStickEntity::class.java, TrackedDataHandlerRegistry.INTEGER)

        /** 同步動畫拋物線最高額外高度。 */
        private val ANIMATION_ARC_HEIGHT: TrackedData<Float> =
            DataTracker.registerData(MahjongScoringStickEntity::class.java, TrackedDataHandlerRegistry.FLOAT)

        /** 同步動畫起點相對終點的 X 偏移。 */
        private val ANIMATION_START_OFFSET_X: TrackedData<Float> =
            DataTracker.registerData(MahjongScoringStickEntity::class.java, TrackedDataHandlerRegistry.FLOAT)

        /** 同步動畫起點相對終點的 Y 偏移。 */
        private val ANIMATION_START_OFFSET_Y: TrackedData<Float> =
            DataTracker.registerData(MahjongScoringStickEntity::class.java, TrackedDataHandlerRegistry.FLOAT)

        /** 同步動畫起點相對終點的 Z 偏移。 */
        private val ANIMATION_START_OFFSET_Z: TrackedData<Float> =
            DataTracker.registerData(MahjongScoringStickEntity::class.java, TrackedDataHandlerRegistry.FLOAT)
    }
}
