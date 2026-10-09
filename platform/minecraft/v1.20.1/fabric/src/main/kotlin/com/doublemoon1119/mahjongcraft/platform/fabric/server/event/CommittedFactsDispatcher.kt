package com.doublemoon1119.mahjongcraft.platform.fabric.server.event

import com.doublemoon1119.mahjongcraft.flow.common.game.event.GameEventProjector
import com.doublemoon1119.mahjongcraft.flow.common.game.history.CommittedGameFacts
import com.doublemoon1119.mahjongcraft.flow.server.state.AuthoritativeStateStore
import com.doublemoon1119.mahjongcraft.flow.server.state.CommittedFactsListener
import com.doublemoon1119.mahjongcraft.logic.module.MahjongModuleRegistry
import com.doublemoon1119.mahjongcraft.platform.fabric.api.event.FabricGameEventContexts
import com.doublemoon1119.mahjongcraft.platform.fabric.logging.mahjongCraftLogger
import com.doublemoon1119.mahjongcraft.platform.fabric.server.achievement.FabricAchievementService
import com.doublemoon1119.mahjongcraft.platform.minecraft.event.GameEventDelivery
import com.doublemoon1119.mahjongcraft.platform.minecraft.event.GameEventDeliveryReporter
import com.doublemoon1119.mahjongcraft.platform.minecraft.event.GameEventLocationSource
import com.doublemoon1119.mahjongcraft.platform.minecraft.event.GameEventScheduler
import com.doublemoon1119.mahjongcraft.platform.minecraft.event.GameEventSubscriptions
import net.minecraft.server.MinecraftServer
import org.koin.core.annotation.Single
import kotlin.concurrent.atomics.AtomicReference
import kotlin.concurrent.atomics.ExperimentalAtomicApi
import kotlin.uuid.Uuid

/**
 * 將權威 store 的提交通知分派給原版成果與第三方遊戲事件兩個接收端。
 *
 * @property store 提供權威交易提交與釋放通知的 store。
 * @property achievementService 處理原版成果判定的服務。
 * @property moduleRegistry 建立規則中立事件投影器所需的模組 registry。
 * @property locations 由記憶體位置索引提供事件產生當下的桌子位置。
 * @property reporter 回報事件交付失敗的診斷元件。
 * @property exclusions 由 debug 流程共用的場次排除名單。
 */
@OptIn(ExperimentalAtomicApi::class)
@Single
class CommittedFactsDispatcher(
    private val store: AuthoritativeStateStore,
    private val achievementService: FabricAchievementService,
    private val moduleRegistry: MahjongModuleRegistry,
    private val locations: GameEventLocationSource,
    private val reporter: GameEventDeliveryReporter,
    private val exclusions: GameEventExclusions,
) {
    /** 記錄個別接收端失敗而不阻斷另一端的診斷 logger。 */
    private val logger = mahjongCraftLogger(CommittedFactsDispatcher::class)

    /** 目前有效 server session；舊 listener 即使稍後回呼也會被此身份檢查拒絕。 */
    private val activeSession = AtomicReference<Session?>(null)

    /**
     * 啟動指定 Minecraft server 的遊戲事件分派。
     *
     * @param server 要接收事件交付工作的 Minecraft server。
     */
    fun startSession(server: MinecraftServer) {
        startSession(
            scheduler = FabricGameEventScheduler(server),
            serverProvider = { server },
        )
    }

    /**
     * 供 Fabric 生產環境與 JVM 測試共用的啟動入口；測試可提供非 Minecraft 的排程器。
     *
     * @param scheduler 將交付工作排入主執行緒的排程器。
     * @param serverProvider 目前 session 對應的 server；省略時不安裝 server 情境轉換綁定。
     * @param subscriptions 本次橋接使用的訂閱點；正式環境使用全域端點，測試可提供獨立集合。
     */
    internal fun startSession(
        scheduler: GameEventScheduler,
        serverProvider: (() -> MinecraftServer)? = null,
        subscriptions: GameEventSubscriptions = GameEventSubscriptions.global(),
    ) {
        stopSession()
        val delivery = GameEventDelivery(
            projector = GameEventProjector(moduleRegistry),
            scheduler = scheduler,
            locations = locations,
            reporter = reporter,
            matchStarted = subscriptions.matchStarted,
            roundSettled = subscriptions.roundSettled,
            matchEnded = subscriptions.matchEnded,
        )
        val sessionId = delivery.startSession()
        lateinit var session: Session
        val listener = object : CommittedFactsListener {
            override fun onCommitted(sequence: Long, facts: CommittedGameFacts) {
                if (activeSession.load() !== session || exclusions.contains(facts.matchId)) return
                runCatching { achievementService.handle(facts) { activeSession.load() === session } }.onFailure { cause ->
                    logger.warn(
                        "Dispatching achievements failed for match {} at venue {}",
                        facts.matchId,
                        facts.venueId,
                        cause,
                    )
                }
                runCatching { delivery.onCommitted(sessionId, sequence, facts) }.onFailure { cause ->
                    logger.warn(
                        "Dispatching game events failed for match {} at venue {}",
                        facts.matchId,
                        facts.venueId,
                        cause,
                    )
                }
            }

            override fun onReleased(sequence: Long) {
                if (activeSession.load() !== session) return
                delivery.onReleased(sessionId, sequence)
            }
        }
        session = Session(sessionId, delivery)
        activeSession.store(session)
        serverProvider?.let { provider ->
            FabricGameEventContexts.install(provider) { context ->
                activeSession.load() === session && delivery.belongsToSession(context, sessionId)
            }
        }
        store.setCommittedFactsListener(listener)
    }

    /**
     * 停止目前 server session，解除 store 接收者並捨棄尚未交付的事件。
     * 舊事件的情境與排程工作不會轉交給下一個 server。
     */
    fun stopSession() {
        val session = activeSession.exchange(null) ?: return
        store.setCommittedFactsListener(CommittedFactsListener.NONE)
        session.delivery.stopSession(session.sessionId)
        FabricGameEventContexts.clear()
        exclusions.clear()
    }

    /**
     * 一個不可變的 session 交付上下文。
     *
     * @property sessionId 此 session 的唯一識別碼。
     * @property delivery 此 session 的事件交付器。
     */
    private class Session(
        val sessionId: Uuid,
        val delivery: GameEventDelivery,
    )
}
