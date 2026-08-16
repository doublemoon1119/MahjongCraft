package com.doublemoon1119.mahjongcraft.platform.fabric.entity

import com.doublemoon1119.mahjongcraft.platform.fabric.registry.ModEntities
import com.doublemoon1119.mahjongcraft.platform.fabric.registry.ModItems
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
) : Entity(type, world) {
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

    /** 啟動一次已由伺服器決定結果的投擲動畫。 */
    fun startRoll(
        finalPoint: MahjongDicePoint,
        seed: Long,
        startGameTime: Long,
        startOffset: DiceAnimationVector,
    ) {
        check(!world.isClient) { "Dice rolls must be started by the server" }
        point = finalPoint
        dataTracker.set(ANIMATION_SEED, seed)
        dataTracker.set(ANIMATION_START_GAME_TIME, startGameTime)
        dataTracker.set(ANIMATION_START_OFFSET_X, startOffset.x.toFloat())
        dataTracker.set(ANIMATION_START_OFFSET_Y, startOffset.y.toFloat())
        dataTracker.set(ANIMATION_START_OFFSET_Z, startOffset.z.toFloat())
        rolling = true
    }

    /** 將骰子標記為指定正式牌局桌子管理。 */
    fun assignToTable(tableId: Uuid) {
        check(!world.isClient) { "Managed dice must be assigned by the server" }
        managedByGame = true
        managedTableId = tableId
    }

    /**
     * 由 server game time 結束動畫；client 只呈現同步狀態。
     *
     * 管理中的骰子（`managedByGame`）額外自行判斷是否該消失：`world.time` 是持久化、單調遞增的世界
     * 年齡，不會因伺服器重啟歸零，所以「動畫＋觀看時間的目標時長」用 `world.time - animationStartGameTime`
     * 就能算出來，不需要另外排計時器或在伺服器啟動時另外掃一輪——伺服器重啟後 entity 重新載入的第一
     * 個 tick，這個算式就會自然算出「早就超過該存在的時長了」，直接 [discard]，正常運行期間跟崩潰
     * 復原走的是同一段邏輯，不需要區分。玩家自由放置的裝飾骰子（`managedByGame == false`）不受影響，
     * 永久存在直到玩家自己回收。
     */
    override fun tick() {
        super.tick()
        if (world.isClient) return
        val elapsedTicks = world.time - animationStartGameTime
        if (rolling) {
            if (elapsedTicks == FIRST_LANDING_TICK) {
                playSound(SoundEvents.ENTITY_ITEM_FRAME_PLACE, 1.0f, 1.0f)
            }
            if (elapsedTicks >= DiceRollAnimationSpec.DEFAULT_DURATION_TICKS) {
                rolling = false
            }
        }
        if (managedByGame && elapsedTicks >= DESPAWN_AFTER_TICKS) {
            discard()
        }
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

    /** 從世界存檔還原點數及管理狀態；無效點數使用一點。 */
    override fun readCustomDataFromNbt(nbt: NbtCompound) {
        point = MahjongDicePoint.fromValueOrDefault(nbt.getInt(NBT_KEY_POINT))
        managedByGame = nbt.getBoolean(NBT_KEY_MANAGED_BY_GAME)
        managedTableId = nbt.getString(NBT_KEY_MANAGED_TABLE_ID)
            .takeIf(String::isNotBlank)
            ?.let { encoded -> runCatching { Uuid.parse(encoded) }.getOrNull() }
        rolling = false
    }

    /** 將點數及管理狀態寫入世界存檔。 */
    override fun writeCustomDataToNbt(nbt: NbtCompound) {
        nbt.putInt(NBT_KEY_POINT, point.value)
        nbt.putBoolean(NBT_KEY_MANAGED_BY_GAME, managedByGame)
        managedTableId?.let { tableId -> nbt.putString(NBT_KEY_MANAGED_TABLE_ID, tableId.toString()) }
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

        /** 拋物線第一次抵達落點的 server tick。 */
        private const val FIRST_LANDING_TICK = 17L

        /**
         * 管理中骰子從動畫開始算起，總共該存在的 tick 數；超過就自我 [discard]。額外觀看時間
         * （[DiceRollAnimationSpec.EXTRA_VIEWING_TICKS]）跟 `FabricGamePresentationPublisher` 算
         * `TablePresentationBusyTracker` 忙碌時長用的是同一個常數，避免兩處各寫一份相同數字。
         */
        private const val DESPAWN_AFTER_TICKS = DiceRollAnimationSpec.DEFAULT_DURATION_TICKS + DiceRollAnimationSpec.EXTRA_VIEWING_TICKS

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
