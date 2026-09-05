package com.doublemoon1119.mahjongcraft.platform.fabric.server.game

import com.doublemoon1119.mahjongcraft.platform.fabric.entity.MahjongAnimationSounds
import com.doublemoon1119.mahjongcraft.platform.fabric.entity.MahjongTileEntity
import com.doublemoon1119.mahjongcraft.platform.fabric.entity.MahjongVisualEffectKeys
import com.doublemoon1119.mahjongcraft.platform.fabric.entity.WinCelebrationEffectEntity
import com.doublemoon1119.mahjongcraft.platform.minecraft.animation.AnimationStep
import com.doublemoon1119.mahjongcraft.platform.minecraft.tile.MahjongTileTableLayout
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents
import net.minecraft.server.world.ServerWorld
import org.koin.core.annotation.Single
import java.util.concurrent.ConcurrentHashMap
import kotlin.random.Random
import kotlin.uuid.Uuid
import kotlin.uuid.toJavaUuid

/**
 * 胡牌慶祝視覺 entity 的 Fabric 排程器，結構比照 [FabricDecisionTimerScheduler]：用
 * [ServerTickEvents.END_SERVER_TICK] 驅動程序內收尾任務；[schedule] 當下就在胡牌張位置生成
 * [WinCelebrationEffectEntity]，由 entity 自己依絕對時間等待開始、跟隨目標與到期，不再持續生成無法
 * 精確控制尺寸的原版粒子。
 *
 * 一炮多響時多位贏家各自呼叫 [schedule]、但 `targetTileId` 相同，[tasksByTargetTileId] 以
 * `putIfAbsent` 去重，只有第一次呼叫真正生成 entity。entity 保存目標 UUID 並持續跟隨，因此排程時自摸
 * 牌尚未完成重排與倒牌也不會記死舊位置。
 *
 * 排程任務本身維持純記憶體；效果 entity 會持久化效果 key、目標、seed 與絕對起訖時間，因此即使在
 * 開始前重載世界也能等待並依原時間軸播放。呼叫端提供的 [Task.onComplete] 是程序內資源清理回呼，無法
 * 序列化；正式對局不依賴該回呼，debug 指令若剛好在重啟期間中斷，臨時牌仍依一般 entity 保存規則留在
 * 世界中。
 */
@Single
class FabricWinCelebrationEffectScheduler {
    /** 依 [Task.targetTileId] 索引的待開始／進行中任務。 */
    private val tasksByTargetTileId = ConcurrentHashMap<Uuid, Task>()

    /**
     * 排定新的效果 entity；相同胡牌張的既有任務尚未到期時直接忽略。
     *
     * @param onComplete 任務到期時額外呼叫一次的程序內收尾動作；debug 指令用來移除臨時牌。
     */
    fun schedule(
        world: ServerWorld,
        targetTileId: Uuid,
        startGameTime: Long,
        endGameTime: Long,
        onComplete: (() -> Unit)? = null,
    ) {
        require(endGameTime > startGameTime) { "Win celebration effect end time must be after start time" }
        val tile = world.getEntity(targetTileId.toJavaUuid()) as? MahjongTileEntity ?: return
        val effect = WinCelebrationEffectEntity(world = world).apply {
            configure(
                effectKey = MahjongVisualEffectKeys.WIN_CELEBRATION,
                targetEntityId = targetTileId,
                animationSeed = Random.nextLong(),
                startGameTime = startGameTime,
                endGameTime = endGameTime,
            )
            enqueue(
                AnimationStep.PlaySound(
                    soundId = MahjongAnimationSounds.TRIDENT_HIT_GROUND,
                    volume = 1.0f,
                    pitch = 1.0f,
                    playAtGameTime = startGameTime + MahjongTileTableLayout.WIN_TRIDENT_FALL_DURATION_TICKS,
                    expiresAtGameTime = startGameTime + MahjongTileTableLayout.WIN_TRIDENT_FALL_DURATION_TICKS + MahjongAnimationSounds.EVENT_GRACE_TICKS,
                ),
            )
            enqueue(
                AnimationStep.PlaySound(
                    soundId = MahjongAnimationSounds.WIN_LIGHTNING,
                    volume = 0.9f,
                    pitch = 1.0f,
                    playAtGameTime = startGameTime + MahjongTileTableLayout.WIN_LIGHTNING_START_TICK,
                    expiresAtGameTime = startGameTime + MahjongTileTableLayout.WIN_LIGHTNING_START_TICK + MahjongAnimationSounds.EVENT_GRACE_TICKS,
                ),
            )
            refreshPositionAndAngles(tile.x, tile.y, tile.z, 0.0f, 0.0f)
        }
        val task = Task(
            world = world,
            targetTileId = targetTileId,
            endGameTime = endGameTime,
            onComplete = onComplete,
            effect = effect,
        )
        if (tasksByTargetTileId.putIfAbsent(targetTileId, task) == null && !world.spawnEntity(effect)) {
            tasksByTargetTileId.remove(targetTileId, task)
        }
    }

    /** 向 Fabric 登記每 tick 一次的效果排程驅動；只能呼叫一次。 */
    fun registerEvents() {
        ServerTickEvents.END_SERVER_TICK.register {
            if (tasksByTargetTileId.isEmpty()) return@register
            val iterator = tasksByTargetTileId.values.iterator()
            while (iterator.hasNext()) {
                val task = iterator.next()
                val now = task.world.time
                if (now >= task.endGameTime) {
                    task.effect.discard()
                    iterator.remove()
                    task.onComplete?.invoke()
                    continue
                }
            }
        }
    }

    /** 單一胡牌張的排程與已生成效果 entity。 */
    private data class Task(
        val world: ServerWorld,
        val targetTileId: Uuid,
        val endGameTime: Long,
        val onComplete: (() -> Unit)? = null,
        val effect: WinCelebrationEffectEntity,
    )
}
