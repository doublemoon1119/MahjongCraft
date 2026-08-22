package com.doublemoon1119.mahjongcraft.platform.fabric.entity

import com.doublemoon1119.mahjongcraft.platform.minecraft.animation.AnimationQueueDriver
import com.doublemoon1119.mahjongcraft.platform.minecraft.animation.AnimationStep
import net.minecraft.entity.Entity
import net.minecraft.entity.EntityType
import net.minecraft.nbt.NbtCompound
import net.minecraft.nbt.NbtElement
import net.minecraft.nbt.NbtList
import net.minecraft.network.packet.s2c.play.EntityPositionS2CPacket
import net.minecraft.server.world.ServerWorld
import net.minecraft.world.World

/**
 * 動畫佇列共用的驅動基底：把動畫播放任務（含期間的等待）持久化掛在 entity 自己身上，透過既有的
 * `tick()` 自行繼續播放，而不是靠純記憶體的 JVM closure 串接多階段延遲——後者在 server 關閉時會遺失
 * 「接下來該做什麼」的資訊，這正是「動畫播放中離開世界，牌卡在半空」這個 bug 的根因。
 *
 * 「這個 entity 是否正在播動畫」＝[isAnimating]＝「佇列是否還有東西」，不需要另外一個獨立旗標；
 * 子類別（[MahjongTileEntity]／[MahjongDiceEntity]）的 `tick()` 只需要呼叫 `super.tick()`，佇列的
 * 進度處理已經包含在裡面。
 *
 * @param C 這個實體類型專屬的瞬間動作型別（[AnimationStep.Custom] 攜帶的資料），沒有專屬動作的實體
 *   類型用 [Nothing]。
 */
abstract class AnimatedMahjongEntity<C>(
    type: EntityType<out AnimatedMahjongEntity<C>>,
    world: World,
) : Entity(type, world) {
    private val queue: ArrayDeque<AnimationStep<C>> = ArrayDeque()
    private var activeStepEndGameTime: Long? = null
    private var pendingTrackerResyncGameTime: Long? = null

    /** 佇列是否還有未播完的 step；`false` 代表這個 entity 目前完全靜止。 */
    val isAnimating: Boolean
        get() = queue.isNotEmpty()

    /** 依序附加 [steps] 到佇列尾端；只能在 server 端呼叫。 */
    fun enqueueAll(steps: List<AnimationStep<C>>) {
        check(!world.isClient) { "Animation steps must be enqueued by the server" }
        queue.addAll(steps)
    }

    /** 附加單一 step，等同 `enqueueAll(listOf(step))`。 */
    fun enqueue(step: AnimationStep<C>) = enqueueAll(listOf(step))

    /** 套用 [AnimationStep.Custom] 攜帶的實體專屬瞬間動作。 */
    protected abstract fun applyCustomStep(step: C)

    /**
     * 啟動一段 [AnimationStep.PlayMotion]：子類別在這裡同步既有的 render 用 tracked data 欄位（含
     * [startGameTime]，通常直接寫進「動畫開始的 game time」那個 tracked field），通常也包含把自己的
     * 「是否正在播動畫」旗標設成 `true`（配合 [onPlayMotionCompleted] 在該 step 播完時設回 `false`）
     * ——「該不該先隱形等到真正輪到才出現」這類需求一律交給 [AnimationStep.SetInvisible]（已經是既有、
     * 驗證過的機制），不透過提前設定「未來才到期」的動畫起點時間表達。
     *
     * [startGameTime] 不是永遠等於呼叫當下的 `world.time`：這段動畫第一次啟動時確實是
     * `world.time`，但世界重新載入後、佇列發現最前面剛好是一個「已經啟動中」的 [AnimationStep.PlayMotion]
     * 時（[readAnimationQueueFromNbt] 用 `activeStepEndGameTime - durationTicks` 反推出真正的起始
     * 時間），會用還原出的原始起始時間重新呼叫一次，讓 render 端算出的經過時間正確接續在動畫播到一半
     * 的進度，而不是把動畫重播一次或直接跳到終點——這是實際踩過的問題：只持久化佇列本身（剩哪些
     * step、到期時間）不夠，render 端用來內插的欄位（例如「動畫開始的 game time」）如果沒有同步還原，
     * 世界重新載入後會讀到預設值，導致正在播到一半的動畫視覺上直接跳到終點。
     */
    protected abstract fun applyPlayMotion(step: AnimationStep.PlayMotion, startGameTime: Long)

    /** [AnimationStep.PlayMotion] 的 [AnimationStep.PlayMotion.durationTicks] 到期、即將被移出佇列時呼叫。 */
    protected open fun onPlayMotionCompleted() {}

    /** 序列化 [AnimationStep.Custom] 攜帶的實體專屬資料，供 [writeAnimationQueueToNbt] 使用。 */
    protected abstract fun serializeCustomStep(step: C, nbt: NbtCompound)

    /** 還原 [AnimationStep.Custom] 攜帶的實體專屬資料，供 [readAnimationQueueFromNbt] 使用。 */
    protected abstract fun deserializeCustomStep(nbt: NbtCompound): C

    /**
     * 依序處理佇列最前面的 step：瞬間動作（[AnimationStep.Teleport]／[AnimationStep.SetInvisible]／
     * [AnimationStep.Custom]）連續處理到下一個計時 step 為止，計時 step（[AnimationStep.WaitUntil]／
     * [AnimationStep.PlayMotion]）未到期就停在原地、等下一個 tick 再檢查——同一個 tick 內可以連續
     * 吃掉好幾個瞬間 step，銜接「隱形＋傳送同一瞬間發生」這類設計。
     *
     * [activeStepEndGameTime] 存絕對 `world.time`，不是剩餘 tick 數——`world.time` 本身是持久化、
     * 跨存讀檔正確恢復的單調計數器，重新載入後從中斷點繼續判斷即可，不需要額外的補丁欄位。
     */
    override fun tick() {
        super.tick()
        if (world.isClient) return
        drainAnimationQueue()
        resyncTrackerIfDue()
    }

    /**
     * 到期／推進的判斷邏輯本身（哪些 step 該套用、佇列該推進到哪裡）委派給不依賴 Minecraft API 的
     * [AnimationQueueDriver]，這裡只負責依判斷結果執行真正的副作用（改座標／改隱形／改姿態／同步
     * render 用欄位）並寫回佇列狀態——讓佇列的到期／推進邏輯可以脫離 `Entity`／`World` 獨立測試，見
     * [AnimationQueueDriver] KDoc。
     */
    private fun drainAnimationQueue() {
        val result = AnimationQueueDriver.tick(queue, activeStepEndGameTime, world.time)
        result.appliedInstantSteps.forEach { step ->
            when (step) {
                is AnimationStep.Teleport -> {
                    refreshPositionAndAngles(step.x, step.y, step.z, step.yaw, 0.0f)
                    pendingTrackerResyncGameTime = world.time + TRACKER_RESYNC_DELAY_TICKS
                }

                is AnimationStep.SetInvisible -> isInvisible = step.invisible
                is AnimationStep.Custom -> applyCustomStep(step.step)
                is AnimationStep.WaitUntil, is AnimationStep.PlayMotion ->
                    error("AnimationQueueDriver must never report a WaitUntil/PlayMotion step as an instant step")
            }
        }
        result.startedPlayMotion?.let { step -> applyPlayMotion(step, startGameTime = world.time) }
        if (result.completedPlayMotion) onPlayMotionCompleted()
        queue.clear()
        queue.addAll(result.remainingQueue)
        activeStepEndGameTime = result.activeStepEndGameTime
    }

    /**
     * `AnimationStep.Teleport` 改的是真實座標，但 `EntityTrackerEntry` 對每個 entity 只維護一份
     * 「所有追蹤中 client 共用」的相對位移基準（`trackedPos`），且這份基準只在牠自己的 `tick()`
     * （依 `trackedUpdateRate` 節流）裡才會追上最新座標。如果一個新 client 在基準還沒追上之前開始
     * 追蹤這個 entity（最典型的情境：世界剛重新載入、佇列一恢復就立刻執行 `Teleport`，幾乎與新玩家
     * 開始追蹤同時發生），該 client 會先透過 spawn packet 拿到正確座標，但下一次基準追上時算出的
     * 「相對位移」會疊加在這個已經正確的座標上，讓畫面（含 hitbox）永久停在一個偏移過的錯誤位置，
     * 直到重新連線重新收到一次 spawn packet 為止——這正是「動畫播放中重進世界，牌偶爾卡在半空」這個
     * 已修 bug 修好後，仍會極少數重現的殘留幽靈 entity 問題的根因。
     *
     * 延遲（[TRACKER_RESYNC_DELAY_TICKS]，需大於 entity type 註冊的 `trackedUpdateRate`）是為了確保
     * `EntityTrackerEntry` 自己的基準已經追上這次 `Teleport` 之後，再主動廣播一次完整定位封包
     * （[EntityPositionS2CPacket]，不是相對位移）覆蓋掉任何可能已經算歪的中間狀態，讓所有追蹤中的
     * client 都收斂回真實座標。
     */
    private fun resyncTrackerIfDue() {
        val dueAt = pendingTrackerResyncGameTime ?: return
        if (world.time < dueAt) return
        pendingTrackerResyncGameTime = null
        (world as ServerWorld).chunkManager.sendToNearbyPlayers(this, EntityPositionS2CPacket(this))
    }

    /** 把目前佇列與計時進度寫進世界存檔，供子類別的 `writeCustomDataToNbt` 呼叫。 */
    protected fun writeAnimationQueueToNbt(nbt: NbtCompound) {
        val stepList = NbtList()
        queue.forEach { step -> stepList.add(serializeStep(step)) }
        nbt.put(NBT_KEY_QUEUE, stepList)
        nbt.putLong(NBT_KEY_ACTIVE_STEP_END_GAME_TIME, activeStepEndGameTime ?: NO_ACTIVE_STEP)
    }

    /**
     * 從世界存檔還原佇列與計時進度，供子類別的 `readCustomDataFromNbt` 呼叫。
     *
     * 若佇列最前面剛好是一個「已經啟動中」的 [AnimationStep.PlayMotion]（[activeStepEndGameTime] 不是
     * `null`，代表存檔當下這段動畫已經開始播放、還沒播完），額外用 `activeStepEndGameTime -
     * durationTicks` 反推出真正的起始 game time，重新呼叫一次 [applyPlayMotion] 補回 render 端用來
     * 內插的欄位——這些欄位只存在 tracked data、沒有另外寫進世界存檔，世界重新載入後預設會被重置，
     * 若不補回，畫面會直接跳到這段動畫的終點，而不是接續播完剩下的進度，理由見 [applyPlayMotion]
     * KDoc。
     */
    protected fun readAnimationQueueFromNbt(nbt: NbtCompound) {
        queue.clear()
        val stepList = nbt.getList(NBT_KEY_QUEUE, NbtElement.COMPOUND_TYPE.toInt())
        stepList.forEach { element -> queue.addLast(deserializeStep(element as NbtCompound)) }
        val storedEndGameTime = nbt.getLong(NBT_KEY_ACTIVE_STEP_END_GAME_TIME)
        activeStepEndGameTime = storedEndGameTime.takeIf { it != NO_ACTIVE_STEP }

        val activeMotion = queue.firstOrNull() as? AnimationStep.PlayMotion
        val resumedEndGameTime = activeStepEndGameTime
        if (activeMotion != null && resumedEndGameTime != null) {
            applyPlayMotion(activeMotion, startGameTime = resumedEndGameTime - activeMotion.durationTicks)
        }
    }

    private fun serializeStep(step: AnimationStep<C>): NbtCompound {
        val stepNbt = NbtCompound()
        when (step) {
            is AnimationStep.WaitUntil -> {
                stepNbt.putString(NBT_KEY_TYPE, TYPE_WAIT_UNTIL)
                stepNbt.putLong(NBT_KEY_GAME_TIME, step.gameTime)
            }

            is AnimationStep.Teleport -> {
                stepNbt.putString(NBT_KEY_TYPE, TYPE_TELEPORT)
                stepNbt.putDouble(NBT_KEY_X, step.x)
                stepNbt.putDouble(NBT_KEY_Y, step.y)
                stepNbt.putDouble(NBT_KEY_Z, step.z)
                stepNbt.putFloat(NBT_KEY_YAW, step.yaw)
            }

            is AnimationStep.SetInvisible -> {
                stepNbt.putString(NBT_KEY_TYPE, TYPE_SET_INVISIBLE)
                stepNbt.putBoolean(NBT_KEY_INVISIBLE, step.invisible)
            }

            is AnimationStep.PlayMotion -> {
                stepNbt.putString(NBT_KEY_TYPE, TYPE_PLAY_MOTION)
                stepNbt.putInt(NBT_KEY_DURATION_TICKS, step.durationTicks)
                stepNbt.putDouble(NBT_KEY_ARC_HEIGHT, step.arcHeight)
                stepNbt.putDouble(NBT_KEY_START_OFFSET_X, step.startOffsetX)
                stepNbt.putDouble(NBT_KEY_START_OFFSET_Y, step.startOffsetY)
                stepNbt.putDouble(NBT_KEY_START_OFFSET_Z, step.startOffsetZ)
                stepNbt.putFloat(NBT_KEY_START_POSE_ROTATION, step.startPoseRotationDegrees)
                stepNbt.putFloat(NBT_KEY_END_POSE_ROTATION, step.endPoseRotationDegrees)
                stepNbt.putBoolean(NBT_KEY_EASE_ROTATION, step.easeRotation)
            }

            is AnimationStep.Custom -> {
                stepNbt.putString(NBT_KEY_TYPE, TYPE_CUSTOM)
                serializeCustomStep(step.step, stepNbt)
            }
        }
        return stepNbt
    }

    private fun deserializeStep(stepNbt: NbtCompound): AnimationStep<C> = when (stepNbt.getString(NBT_KEY_TYPE)) {
        TYPE_WAIT_UNTIL -> AnimationStep.WaitUntil(stepNbt.getLong(NBT_KEY_GAME_TIME))
        TYPE_TELEPORT -> AnimationStep.Teleport(
            x = stepNbt.getDouble(NBT_KEY_X),
            y = stepNbt.getDouble(NBT_KEY_Y),
            z = stepNbt.getDouble(NBT_KEY_Z),
            yaw = stepNbt.getFloat(NBT_KEY_YAW),
        )

        TYPE_SET_INVISIBLE -> AnimationStep.SetInvisible(stepNbt.getBoolean(NBT_KEY_INVISIBLE))

        TYPE_PLAY_MOTION -> AnimationStep.PlayMotion(
            durationTicks = stepNbt.getInt(NBT_KEY_DURATION_TICKS),
            arcHeight = stepNbt.getDouble(NBT_KEY_ARC_HEIGHT),
            startOffsetX = stepNbt.getDouble(NBT_KEY_START_OFFSET_X),
            startOffsetY = stepNbt.getDouble(NBT_KEY_START_OFFSET_Y),
            startOffsetZ = stepNbt.getDouble(NBT_KEY_START_OFFSET_Z),
            startPoseRotationDegrees = stepNbt.getFloat(NBT_KEY_START_POSE_ROTATION),
            endPoseRotationDegrees = stepNbt.getFloat(NBT_KEY_END_POSE_ROTATION),
            easeRotation = stepNbt.getBoolean(NBT_KEY_EASE_ROTATION),
        )

        TYPE_CUSTOM -> AnimationStep.Custom(deserializeCustomStep(stepNbt))
        else -> AnimationStep.WaitUntil(0L)
    }

    private companion object {
        /** 必須大於 entity type 註冊時的 `trackedUpdateRate`（目前是 `10`），理由見 [resyncTrackerIfDue]。 */
        const val TRACKER_RESYNC_DELAY_TICKS = 12L

        const val NBT_KEY_QUEUE = "AnimationQueue"
        const val NBT_KEY_ACTIVE_STEP_END_GAME_TIME = "AnimationActiveStepEndGameTime"
        const val NO_ACTIVE_STEP = -1L

        const val NBT_KEY_TYPE = "Type"
        const val TYPE_WAIT_UNTIL = "WaitUntil"
        const val TYPE_TELEPORT = "Teleport"
        const val TYPE_SET_INVISIBLE = "SetInvisible"
        const val TYPE_PLAY_MOTION = "PlayMotion"
        const val TYPE_CUSTOM = "Custom"

        const val NBT_KEY_GAME_TIME = "GameTime"
        const val NBT_KEY_X = "X"
        const val NBT_KEY_Y = "Y"
        const val NBT_KEY_Z = "Z"
        const val NBT_KEY_YAW = "Yaw"
        const val NBT_KEY_INVISIBLE = "Invisible"
        const val NBT_KEY_DURATION_TICKS = "DurationTicks"
        const val NBT_KEY_ARC_HEIGHT = "ArcHeight"
        const val NBT_KEY_START_OFFSET_X = "StartOffsetX"
        const val NBT_KEY_START_OFFSET_Y = "StartOffsetY"
        const val NBT_KEY_START_OFFSET_Z = "StartOffsetZ"
        const val NBT_KEY_START_POSE_ROTATION = "StartPoseRotation"
        const val NBT_KEY_END_POSE_ROTATION = "EndPoseRotation"
        const val NBT_KEY_EASE_ROTATION = "EaseRotation"
    }
}
