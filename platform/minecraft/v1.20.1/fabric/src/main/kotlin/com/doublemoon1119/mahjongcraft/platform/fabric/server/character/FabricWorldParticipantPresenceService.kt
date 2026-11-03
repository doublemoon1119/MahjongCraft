@file:OptIn(ExperimentalAtomicApi::class)

package com.doublemoon1119.mahjongcraft.platform.fabric.server.character

import com.doublemoon1119.mahjongcraft.flow.common.concurrency.AppCoroutineScope
import com.doublemoon1119.mahjongcraft.flow.common.concurrency.CoroutineDispatchers
import com.doublemoon1119.mahjongcraft.flow.server.game.service.GameAbortHandler
import com.doublemoon1119.mahjongcraft.platform.fabric.logging.mahjongCraftLogger
import com.doublemoon1119.mahjongcraft.platform.fabric.server.persistence.FabricAuthoritativeStatePersistence
import com.doublemoon1119.mahjongcraft.platform.fabric.server.seating.FabricSeatingSessionFactory
import com.doublemoon1119.mahjongcraft.platform.minecraft.character.WorldParticipantPresenceMonitor
import com.doublemoon1119.mahjongcraft.platform.minecraft.config.MinecraftServerConfigState
import com.doublemoon1119.mahjongcraft.platform.minecraft.table.TableInteractionReach
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents
import net.minecraft.server.MinecraftServer
import kotlin.concurrent.atomics.AtomicReference
import kotlin.concurrent.atomics.ExperimentalAtomicApi
import kotlin.coroutines.cancellation.CancellationException
import kotlin.uuid.toJavaUuid

/**
 * 定期檢查參與遊戲的 AI 玩家是否仍在桌子附近，找不到時套用伺服器設定。
 *
 * @property persistence 讀取目前對局與 AI 玩家對應的實體身分。
 * @property sessions 取得已載入桌子的所在世界與位置。
 * @property configState 找不到 AI 玩家時要中止還是繼續的設定。
 * @property abortHandler 確認對局狀態後中止對局，讓玩家返回房間。
 * @property scope 隨本次伺服器停止而取消的協程作用域。
 * @property dispatchers 伺服器主執行緒調度器。
 */
internal class FabricWorldParticipantPresenceService(
    private val persistence: FabricAuthoritativeStatePersistence,
    private val sessions: FabricSeatingSessionFactory,
    private val configState: MinecraftServerConfigState,
    private val abortHandler: GameAbortHandler,
    private val scope: AppCoroutineScope,
    private val dispatchers: CoroutineDispatchers,
) {
    /** 記錄 AI 玩家檢查或中止作業失敗的原因。 */
    private val logger = mahjongCraftLogger(FabricWorldParticipantPresenceService::class)

    /** 本次接受檢查的伺服器。 */
    private var server: MinecraftServer? = null

    /** 標記已登記的檢查，工作結束或取消時釋放。 */
    private val pending = AtomicReference<Any?>(null)

    /** 目前的檢查工作，伺服器停止時會取消。 */
    private var job: Job? = null

    /** 登記每秒一次的檢查，同時間只執行一份工作。 */
    fun registerEvents() {
        ServerTickEvents.END_SERVER_TICK.register { current ->
            if (server !== current || current.ticks % CHECK_INTERVAL != 0) return@register
            val token = Any()
            if (!pending.compareAndSet(null, token)) return@register
            val task = scope.launch(dispatchers.main, start = CoroutineStart.LAZY) {
                try {
                    val monitor = WorldParticipantPresenceMonitor(
                        active = { server === current && pending.load() === token },
                        policy = { configState.current.missingAiPlayerPolicy },
                        tableActive = { game -> sessions.prepareTable(game.id) != null },
                        available = { game, identity ->
                            val table = sessions.prepareTable(game.id)
                            val entity = table?.world?.getEntity(identity.subjectId.toJavaUuid())
                            entity != null &&
                                entity.isAlive &&
                                !entity.isRemoved &&
                                table.world.chunkManager.isChunkLoaded(entity.blockX shr 4, entity.blockZ shr 4) &&
                                TableInteractionReach.distance(
                                    entity.x,
                                    entity.y,
                                    entity.z,
                                    table.location.x,
                                    table.location.y,
                                    table.location.z,
                                ) <= TableInteractionReach.SURVIVAL
                        },
                        abortHandler = abortHandler,
                    )
                    monitor.reconcile(persistence.characterSnapshot()?.authoritative?.games?.values?.toList().orEmpty())
                } catch (cause: CancellationException) {
                    throw cause
                } catch (cause: Exception) {
                    logger.error("World participant presence check failed", cause)
                }
            }
            job = task
            task.invokeOnCompletion { pending.compareAndSet(token, null) }
            task.start()
        }
    }

    /**
     * 開始接受本次伺服器的檢查。
     *
     * @param current 已還原資料的伺服器。
     */
    fun startSession(current: MinecraftServer) {
        stopSession()
        server = current
    }

    /** 停止接收新檢查，並取消尚未完成的工作。 */
    fun stopSession() {
        server = null
        job?.cancel()
        job = null
        pending.store(null)
    }

    /** AI 玩家檢查的執行頻率。 */
    private companion object {
        /** 每秒檢查一次。 */
        const val CHECK_INTERVAL = 20
    }
}
