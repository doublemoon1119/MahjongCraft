package com.doublemoon1119.mahjongcraft.platform.minecraft.mod.server.game

import com.doublemoon1119.mahjongcraft.platform.minecraft.mod.entity.MahjongTileEntity
import com.doublemoon1119.mahjongcraft.platform.minecraft.mod.entity.MahjongVisualEffectKeys
import com.doublemoon1119.mahjongcraft.platform.minecraft.mod.entity.WinCelebrationEffectEntity
import com.doublemoon1119.mahjongcraft.platform.minecraft.mod.loader.ServerEvents
import com.doublemoon1119.mahjongcraft.platform.minecraft.mod.logging.mahjongCraftLogger
import com.doublemoon1119.mahjongcraft.platform.minecraft.mod.server.entity.EntitySpawnGateway
import com.doublemoon1119.mahjongcraft.platform.minecraft.tile.WinCelebrationEffectSteps
import net.minecraft.server.level.ServerLevel
import java.util.concurrent.ConcurrentHashMap
import kotlin.random.Random
import kotlin.uuid.Uuid
import kotlin.uuid.toJavaUuid

/**
 * 胡牌慶祝視覺 entity 的排程器，結構比照 [DecisionTimerScheduler]：用
 * 伺服器 tick 結束事件驅動程序內收尾任務；[schedule] 當下就在胡牌張位置生成
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
 *
 * @property spawnGateway 生成慶祝效果 entity。
 */
class WinCelebrationEffectScheduler(private val spawnGateway: EntitySpawnGateway) {
    /** 記錄胡牌張 entity 找不到而略過效果的 logger。 */
    private val logger = mahjongCraftLogger(WinCelebrationEffectScheduler::class)

    /** 依 [Task.targetTileId] 索引的待開始／進行中任務。 */
    private val tasksByTargetTileId = ConcurrentHashMap<Uuid, Task>()

    /**
     * 排定新的效果 entity；相同胡牌張的既有任務尚未到期時直接忽略，胡牌張 entity 找不到時記錄警告並略過。
     *
     * @param onComplete 任務到期時額外呼叫一次的程序內收尾動作；debug 指令用來移除臨時牌。
     * @param world 胡牌張所在的世界。
     * @param tableId 觸發慶祝效果的牌桌識別碼。
     * @param targetTileId 效果跟隨的胡牌張識別碼。
     * @param startGameTime 效果時間軸的絕對起始 game time。
     * @param endGameTime 效果到期的絕對 game time，必須晚於起始時間。
     */
    fun schedule(
        world: ServerLevel,
        tableId: Uuid,
        targetTileId: Uuid,
        startGameTime: Long,
        endGameTime: Long,
        onComplete: (() -> Unit)? = null,
    ) {
        require(endGameTime > startGameTime) { "Win celebration effect end time must be after start time" }
        val tile = world.getEntity(targetTileId.toJavaUuid()) as? MahjongTileEntity
        if (tile == null) {
            logger.warn("Win celebration effect skipped on table {}: winning tile entity {} is missing", tableId, targetTileId)
            return
        }
        val effect = WinCelebrationEffectEntity(world = world).apply {
            configure(
                tableId = tableId,
                effectKey = MahjongVisualEffectKeys.WIN_CELEBRATION,
                targetEntityId = targetTileId,
                animationSeed = Random.nextLong(),
                startGameTime = startGameTime,
                endGameTime = endGameTime,
            )
            WinCelebrationEffectSteps.sounds(startGameTime).forEach { enqueue(step = it) }
            moveTo(tile.x, tile.y, tile.z, 0.0f, 0.0f)
        }
        val task = Task(
            world = world,
            tableId = tableId,
            targetTileId = targetTileId,
            endGameTime = endGameTime,
            onComplete = onComplete,
            effect = effect,
        )
        if (tasksByTargetTileId.putIfAbsent(targetTileId, task) == null &&
            !spawnGateway.spawn(world, effect, "win-celebration-effect", tableId)
        ) {
            tasksByTargetTileId.remove(targetTileId, task)
        }
    }

    /**
     * 取消指定桌子的全部待播放／播放中效果並立即移除其世界實體。
     *
     * @param tableId 要取消效果的牌桌識別碼。
     */
    fun cancel(tableId: Uuid) {
        val iterator = tasksByTargetTileId.values.iterator()
        while (iterator.hasNext()) {
            val task = iterator.next()
            if (task.tableId != tableId) continue
            task.effect.discard()
            iterator.remove()
            task.onComplete?.invoke()
        }
    }

    /**
     * 登記每 tick 一次的效果排程驅動；只能呼叫一次。
     *
     * @param events 登記事件用的伺服器事件來源。
     */
    fun registerEvents(events: ServerEvents) {
        events.onServerTickEnd {
            if (tasksByTargetTileId.isEmpty()) return@onServerTickEnd
            val iterator = tasksByTargetTileId.values.iterator()
            while (iterator.hasNext()) {
                val task = iterator.next()
                val now = task.world.gameTime
                if (now >= task.endGameTime) {
                    task.effect.discard()
                    iterator.remove()
                    task.onComplete?.invoke()
                    continue
                }
            }
        }
    }

    /**
     * 單一胡牌張的排程與已生成效果 entity。
     *
     * @property world 效果 entity 所在的世界。
     * @property tableId 觸發效果的牌桌識別碼。
     * @property targetTileId 效果跟隨的胡牌張識別碼。
     * @property endGameTime 效果到期的絕對 game time。
     * @property onComplete 到期或取消時呼叫的程序內回呼。
     * @property effect 此任務生成的效果 entity。
     */
    private data class Task(
        val world: ServerLevel,
        val tableId: Uuid,
        val targetTileId: Uuid,
        val endGameTime: Long,
        val onComplete: (() -> Unit)? = null,
        val effect: WinCelebrationEffectEntity,
    )
}
