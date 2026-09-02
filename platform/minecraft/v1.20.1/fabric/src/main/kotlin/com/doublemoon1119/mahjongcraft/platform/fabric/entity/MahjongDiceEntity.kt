package com.doublemoon1119.mahjongcraft.platform.fabric.entity

import com.doublemoon1119.mahjongcraft.platform.fabric.registry.ModEntities
import com.doublemoon1119.mahjongcraft.platform.fabric.registry.ModItems
import com.doublemoon1119.mahjongcraft.platform.minecraft.animation.AnimationStep
import com.doublemoon1119.mahjongcraft.platform.minecraft.dice.DiceAnimationVector
import com.doublemoon1119.mahjongcraft.platform.minecraft.dice.DiceRollAnimationSpec
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

/** 可自由放置並保存朝上點數的麻將骰子 entity。 */
class MahjongDiceEntity(
    type: EntityType<out MahjongDiceEntity> = ModEntities.mahjongDice,
    world: World,
) : AnimatedMahjongEntity<Nothing>(type, world) {
    /** 目前朝上的點數；非法 tracked value 使用一點。 */
    var point: MahjongDicePoint
        get() = MahjongDicePoint.fromValueOrDefault(dataTracker[POINT])
        set(value) = dataTracker.set(POINT, value.value)

    /** 是否由正式牌局管理；管理中的骰子不接受測試互動。 */
    var managedByGame: Boolean
        get() = dataTracker[MANAGED_BY_GAME]
        set(value) = dataTracker.set(MANAGED_BY_GAME, value)

    /** 正式骰子所屬麻將桌；自由放置骰子為 null。 */
    var managedTableId: Uuid?
        get() = dataTracker[MANAGED_TABLE_ID]
            .takeIf(String::isNotBlank)
            ?.let { encoded -> runCatching { Uuid.parse(encoded) }.getOrNull() }
        private set(value) = dataTracker.set(MANAGED_TABLE_ID, value?.toString().orEmpty())

    /** 是否正在播放由伺服器啟動的投擲動畫。 */
    var rolling: Boolean
        get() = dataTracker[ROLLING]
        private set(value) = dataTracker.set(ROLLING, value)

    /** 用來確定性重建動畫路徑與翻滾軸的 seed。 */
    val animationSeed: Long
        get() = dataTracker[ANIMATION_SEED]

    /** 動畫開始時的 server game time。 */
    val animationStartGameTime: Long
        get() = dataTracker[ANIMATION_START_GAME_TIME]

    /** 玩家手部附近起點相對 entity 最終落點的向量。 */
    val animationStartOffset: DiceAnimationVector
        get() = DiceAnimationVector(
            x = dataTracker[ANIMATION_START_OFFSET_X].toDouble(),
            y = dataTracker[ANIMATION_START_OFFSET_Y].toDouble(),
            z = dataTracker[ANIMATION_START_OFFSET_Z].toDouble(),
        )

    init {
        setNoGravity(true)
    }

    /** 骰子只提供視線選取，不阻擋玩家或其他 entity。 */
    override fun isCollidable(): Boolean = false

    /** 骰子不參與一般 entity 推擠。 */
    override fun isPushable(): Boolean = false

    /** 允許 raycast、右鍵互動與後續 HUD targeting 選取骰子。 */
    override fun canHit(): Boolean = !isRemoved

    /** 允許自由放置骰子成為左鍵回收目標。 */
    override fun isAttackable(): Boolean = !isRemoved && !managedByGame && !rolling

    /** 右鍵自由放置骰子時循環朝上點數。 */
    override fun interact(player: PlayerEntity, hand: Hand): ActionResult {
        if (managedByGame || rolling) return ActionResult.PASS
        if (!world.isClient) point = point.next()
        return if (world.isClient) ActionResult.SUCCESS else ActionResult.CONSUME
    }

    /** 左鍵回收自由放置骰子；創造模式只移除 entity。 */
    override fun handleAttack(attacker: Entity): Boolean {
        val player = attacker as? PlayerEntity ?: return false
        if (managedByGame || rolling) return false
        if (!world.isClient) {
            if (!player.abilities.creativeMode) dropStack(ItemStack(ModItems.MAHJONG_DICE))
            playSound(SoundEvents.ENTITY_ITEM_FRAME_BREAK, 1.0f, 1.0f)
            discard()
        }
        return true
    }

    /**
     * 啟動一次已由伺服器決定結果的投擲動畫；[startDelayTicks] 是這次投擲該延遲多久才真正開始播放
     * （例如等牌牆掉落動畫播完），以動畫佇列的 [AnimationStep.WaitUntil] 表達，不再需要呼叫端另外用
     * `FabricTickMonotonicClock.scheduleAfter` 延遲呼叫這個方法本身——這個延遲本身現在會持久化，
     * 撐得過伺服器重啟，理由見 [AnimatedMahjongEntity] KDoc。
     * [sharedViewingEndGameTime] 可讓同一次投擲中不同 stagger 的骰子維持到相同絕對截止時間；未提供時
     * 沿用單顆骰子的自然截止時間。
     *
     * 動畫播完後額外接一段 [DiceRollAnimationSpec.EXTRA_VIEWING_TICKS] 的等待，讓佇列（[isAnimating]）
     * 在點數落定後繼續維持「還在忙」一小段時間，給玩家看清楚點數的緩衝——這段緩衝過去是
     * `TablePresentationBusyTracker` 自己另外加總的，現在改成掛在骰子自己的佇列尾端；佇列播完（含
     * 這段緩衝）也正是 [tick] 判斷該自我 [discard] 的時機點，見該方法 KDoc。
     */
    fun startRoll(
        finalPoint: MahjongDicePoint,
        seed: Long,
        startDelayTicks: Int,
        startOffset: DiceAnimationVector,
        sharedViewingEndGameTime: Long? = null,
    ) {
        check(!world.isClient) { "Dice rolls must be started by the server" }
        point = finalPoint
        dataTracker.set(ANIMATION_SEED, seed)
        dataTracker.set(ANIMATION_START_OFFSET_X, startOffset.x.toFloat())
        dataTracker.set(ANIMATION_START_OFFSET_Y, startOffset.y.toFloat())
        dataTracker.set(ANIMATION_START_OFFSET_Z, startOffset.z.toFloat())
        val rollStartGameTime = world.time + startDelayTicks
        val naturalViewingEndGameTime = rollStartGameTime + DiceRollAnimationSpec.DEFAULT_DURATION_TICKS + DiceRollAnimationSpec.EXTRA_VIEWING_TICKS
        val viewingEndGameTime = sharedViewingEndGameTime?.also { sharedEnd ->
            require(sharedEnd >= naturalViewingEndGameTime) { "Shared dice viewing end must not shorten the presentation" }
        } ?: naturalViewingEndGameTime
        enqueueAll(
            listOf(
                AnimationStep.SetInvisible(true),
                AnimationStep.WaitUntil(rollStartGameTime),
                AnimationStep.SetInvisible(false),
                AnimationStep.PlayMotion(
                    durationTicks = DiceRollAnimationSpec.DEFAULT_DURATION_TICKS,
                    arcHeight = 0.0,
                    startOffsetX = 0.0,
                    startOffsetY = 0.0,
                    startOffsetZ = 0.0,
                    startPoseRotationDegrees = 0.0f,
                    endPoseRotationDegrees = 0.0f,
                ),
                AnimationStep.WaitUntil(viewingEndGameTime),
            ),
        )
    }

    /** 骰子的實際投擲曲線由 client renderer 依 [animationSeed] 等欄位確定性重建，不需要 [AnimationStep.PlayMotion] 的拋物線／姿態旋轉參數。 */
    override fun applyPlayMotion(step: AnimationStep.PlayMotion, startGameTime: Long) {
        dataTracker.set(ANIMATION_START_GAME_TIME, startGameTime)
        rolling = true
    }

    /** 投擲動畫播完，實際邏輯位置維持落點不變（骰子動畫全程不移動真實座標，只有畫面位移）。 */
    override fun onPlayMotionCompleted() {
        rolling = false
    }

    /** 骰子沒有專屬瞬間動作，這三個方法永遠不會被呼叫（[Nothing] 沒有任何實例）。 */
    override fun applyCustomStep(step: Nothing) = step

    override fun serializeCustomStep(step: Nothing, nbt: NbtCompound) = step

    override fun deserializeCustomStep(nbt: NbtCompound): Nothing = error("MahjongDiceEntity never enqueues AnimationStep.Custom")

    /** 將骰子標記為指定正式牌局桌子管理。 */
    fun assignToTable(tableId: Uuid) {
        check(!world.isClient) { "Managed dice must be assigned by the server" }
        managedByGame = true
        managedTableId = tableId
    }

    /**
     * 管理中的骰子（`managedByGame`）動畫佇列播完（含 [startRoll] 尾端接的
     * [DiceRollAnimationSpec.EXTRA_VIEWING_TICKS] 觀看緩衝）就自我 [discard]，直接用
     * [AnimatedMahjongEntity.isAnimating]（佇列是否還有東西）判斷，不需要另外自己計算「該存在多久」。
     *
     * 過去用一個獨立的「entity 生成起算、固定 tick 數後消失」計時器（[DiceRollAnimationSpec] 相關
     * 常數），但骰子現在是立刻生成、把「等牌牆掉落動畫播完」這段等待折算成佇列最前面的
     * `AnimationStep.Wait` step（見 [startRoll]），生成時間點跟動畫真正開始播放的時間點已經不再
     * 相同——如果還是從生成那一刻起算固定 tick 數，那段等待會被誤算進骰子該存在的預算裡，導致動畫
     * （含尾端觀看緩衝）都還沒播完就被判定「時間到」提前消失，這是實際踩過的問題。改用「佇列是否
     * 還有東西」判斷不會有這個落差，也自動繼承佇列本身跨世界重新載入正確恢復的持久化保證，不需要
     * 再另外處理「世界重新載入時可能一次補跑一大段 tick」這種邊界情況。
     */
    override fun tick() {
        super.tick()
        if (world.isClient) return
        if (rolling && world.time - animationStartGameTime == FIRST_LANDING_TICK) {
            playSound(SoundEvents.ENTITY_ITEM_FRAME_PLACE, 1.0f, 1.0f)
        }
        if (managedByGame && !isAnimating) discard()
    }

    /** 初始化 client/server 同步的點數與管理狀態。 */
    override fun initDataTracker() {
        dataTracker.startTracking(POINT, MahjongDicePoint.ONE.value)
        dataTracker.startTracking(MANAGED_BY_GAME, false)
        dataTracker.startTracking(MANAGED_TABLE_ID, "")
        dataTracker.startTracking(ROLLING, false)
        dataTracker.startTracking(ANIMATION_SEED, 0L)
        dataTracker.startTracking(ANIMATION_START_GAME_TIME, 0L)
        dataTracker.startTracking(ANIMATION_START_OFFSET_X, 0.0f)
        dataTracker.startTracking(ANIMATION_START_OFFSET_Y, 0.0f)
        dataTracker.startTracking(ANIMATION_START_OFFSET_Z, 0.0f)
    }

    /**
     * 從世界存檔還原點數、管理狀態、動畫佇列與投擲動畫本身的參數，非法值使用安全預設。
     *
     * [ANIMATION_SEED]／[ANIMATION_START_OFFSET_X]／`Y`／`Z` 額外持久化——這幾個欄位是 [startRoll]
     * 在動畫佇列排定 [AnimationStep.PlayMotion] 之前就先設定好的骰子專屬投擲參數（骰子的投擲曲線由
     * client renderer 依 [animationSeed] 確定性重建，不是通用 [AnimationStep.PlayMotion] 拋物線／姿態
     * 旋轉那一套，見 [applyPlayMotion]），不屬於 [writeAnimationQueueToNbt] 涵蓋的佇列本身，需要另外
     * 持久化，否則世界重新載入後骰子動畫恢復播放時會用到歸零的預設值，畫面會跳掉。[ANIMATION_START_GAME_TIME]／
     * [ROLLING] 不需要另外持久化——[readAnimationQueueFromNbt] 若發現佇列裡有已經啟動中的
     * [AnimationStep.PlayMotion] 會自動重新呼叫 [applyPlayMotion] 補回這兩者，見該方法 KDoc。
     */
    override fun readCustomDataFromNbt(nbt: NbtCompound) {
        point = MahjongDicePoint.fromValueOrDefault(nbt.getInt(NBT_KEY_POINT))
        managedByGame = nbt.getBoolean(NBT_KEY_MANAGED_BY_GAME)
        managedTableId = nbt.getString(NBT_KEY_MANAGED_TABLE_ID)
            .takeIf(String::isNotBlank)
            ?.let { encoded -> runCatching { Uuid.parse(encoded) }.getOrNull() }
        dataTracker.set(ANIMATION_SEED, nbt.getLong(NBT_KEY_ANIMATION_SEED))
        dataTracker.set(ANIMATION_START_OFFSET_X, nbt.getFloat(NBT_KEY_ANIMATION_START_OFFSET_X))
        dataTracker.set(ANIMATION_START_OFFSET_Y, nbt.getFloat(NBT_KEY_ANIMATION_START_OFFSET_Y))
        dataTracker.set(ANIMATION_START_OFFSET_Z, nbt.getFloat(NBT_KEY_ANIMATION_START_OFFSET_Z))
        rolling = false
        readAnimationQueueFromNbt(nbt)
    }

    /** 將點數、管理狀態、動畫佇列與投擲動畫本身的參數寫入世界存檔，理由同 [readCustomDataFromNbt]。 */
    override fun writeCustomDataToNbt(nbt: NbtCompound) {
        nbt.putInt(NBT_KEY_POINT, point.value)
        nbt.putBoolean(NBT_KEY_MANAGED_BY_GAME, managedByGame)
        managedTableId?.let { tableId -> nbt.putString(NBT_KEY_MANAGED_TABLE_ID, tableId.toString()) }
        nbt.putLong(NBT_KEY_ANIMATION_SEED, animationSeed)
        nbt.putFloat(NBT_KEY_ANIMATION_START_OFFSET_X, dataTracker[ANIMATION_START_OFFSET_X])
        nbt.putFloat(NBT_KEY_ANIMATION_START_OFFSET_Y, dataTracker[ANIMATION_START_OFFSET_Y])
        nbt.putFloat(NBT_KEY_ANIMATION_START_OFFSET_Z, dataTracker[ANIMATION_START_OFFSET_Z])
        writeAnimationQueueToNbt(nbt)
    }

    companion object {
        /** 舊版驗證過的骰子世界邊長。 */
        const val SIZE = 0.125f

        /** 點數世界存檔 key。 */
        private const val NBT_KEY_POINT = "Point"

        /** 正式牌局管理狀態世界存檔 key。 */
        private const val NBT_KEY_MANAGED_BY_GAME = "ManagedByGame"

        /** 正式骰子所屬桌子 UUID 的世界存檔 key。 */
        private const val NBT_KEY_MANAGED_TABLE_ID = "ManagedTableId"

        /** 投擲動畫 seed 世界存檔 key。 */
        private const val NBT_KEY_ANIMATION_SEED = "AnimationSeed"

        /** 投擲動畫起點 X 偏移世界存檔 key。 */
        private const val NBT_KEY_ANIMATION_START_OFFSET_X = "AnimationStartOffsetX"

        /** 投擲動畫起點 Y 偏移世界存檔 key。 */
        private const val NBT_KEY_ANIMATION_START_OFFSET_Y = "AnimationStartOffsetY"

        /** 投擲動畫起點 Z 偏移世界存檔 key。 */
        private const val NBT_KEY_ANIMATION_START_OFFSET_Z = "AnimationStartOffsetZ"

        /** 拋物線第一次抵達落點的 server tick。 */
        private const val FIRST_LANDING_TICK = 17L

        /** 同步目前朝上的點數值。 */
        private val POINT: TrackedData<Int> =
            DataTracker.registerData(MahjongDiceEntity::class.java, TrackedDataHandlerRegistry.INTEGER)

        /** 同步是否由正式牌局管理。 */
        private val MANAGED_BY_GAME: TrackedData<Boolean> =
            DataTracker.registerData(MahjongDiceEntity::class.java, TrackedDataHandlerRegistry.BOOLEAN)

        /** 同步正式骰子所屬麻將桌 UUID；空字串表示自由放置。 */
        private val MANAGED_TABLE_ID: TrackedData<String> =
            DataTracker.registerData(MahjongDiceEntity::class.java, TrackedDataHandlerRegistry.STRING)

        /** 同步目前是否正在播放投擲動畫。 */
        private val ROLLING: TrackedData<Boolean> =
            DataTracker.registerData(MahjongDiceEntity::class.java, TrackedDataHandlerRegistry.BOOLEAN)

        /** 同步確定性動畫 seed。 */
        private val ANIMATION_SEED: TrackedData<Long> =
            DataTracker.registerData(MahjongDiceEntity::class.java, TrackedDataHandlerRegistry.LONG)

        /** 同步動畫開始的 server game time。 */
        private val ANIMATION_START_GAME_TIME: TrackedData<Long> =
            DataTracker.registerData(MahjongDiceEntity::class.java, TrackedDataHandlerRegistry.LONG)

        /** 同步動畫起點相對落點的 X 偏移。 */
        private val ANIMATION_START_OFFSET_X: TrackedData<Float> =
            DataTracker.registerData(MahjongDiceEntity::class.java, TrackedDataHandlerRegistry.FLOAT)

        /** 同步動畫起點相對落點的 Y 偏移。 */
        private val ANIMATION_START_OFFSET_Y: TrackedData<Float> =
            DataTracker.registerData(MahjongDiceEntity::class.java, TrackedDataHandlerRegistry.FLOAT)

        /** 同步動畫起點相對落點的 Z 偏移。 */
        private val ANIMATION_START_OFFSET_Z: TrackedData<Float> =
            DataTracker.registerData(MahjongDiceEntity::class.java, TrackedDataHandlerRegistry.FLOAT)
    }
}
