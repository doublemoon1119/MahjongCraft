package com.doublemoon1119.mahjongcraft.platform.fabric.server.achievement

import com.doublemoon1119.mahjongcraft.flow.common.concurrency.AppCoroutineScope
import com.doublemoon1119.mahjongcraft.flow.common.concurrency.CoroutineDispatchers
import com.doublemoon1119.mahjongcraft.flow.common.game.history.CommittedGameFacts
import com.doublemoon1119.mahjongcraft.flow.server.state.AuthoritativeStateStore
import com.doublemoon1119.mahjongcraft.flow.server.state.CommittedFactsListener
import com.doublemoon1119.mahjongcraft.logic.module.MahjongModuleRegistry
import com.doublemoon1119.mahjongcraft.platform.fabric.logging.mahjongCraftLogger
import com.doublemoon1119.mahjongcraft.platform.minecraft.achievement.GameAchievementDetector
import com.doublemoon1119.mahjongcraft.platform.minecraft.achievement.GameAchievementResolverRegistry
import kotlinx.coroutines.launch
import org.koin.core.annotation.Single
import java.util.concurrent.ConcurrentHashMap
import kotlin.uuid.Uuid

/**
 * server session 期間把已提交的對局事實轉成原版統計與進度。
 *
 * 交易提交時在提交的執行緒上立即判定成果，不保留對局狀態；判定出成果時才把授予排到 [CoroutineDispatchers.main]
 * （伺服器 tick 佇列），原版統計與進度只能在該執行緒操作。單筆事實判定或授予失敗時只記錄警告，不影響後續事實；
 * 成果發生時不在線的玩家不補發。
 *
 * @property scope 執行授予的協程 scope；隨 server session 結束取消。
 * @property dispatchers 取得伺服器主執行緒 dispatcher。
 * @property store 送出已提交事實的權威狀態。
 * @property gateway 把成果交給原版統計與進度系統。
 */
@Single
class FabricAchievementService(
    private val scope: AppCoroutineScope,
    private val dispatchers: CoroutineDispatchers,
    private val store: AuthoritativeStateStore,
    moduleRegistry: MahjongModuleRegistry,
    resolverRegistry: GameAchievementResolverRegistry,
    private val gateway: AchievementGrantGateway,
) {
    private val logger = mahjongCraftLogger(FabricAchievementService::class)

    /** 通用與規則專屬成果判定。 */
    private val detector = GameAchievementDetector(moduleRegistry, resolverRegistry)

    /** 將已提交事實轉交本服務；釋放通知不需要額外處理。 */
    private val committedFactsListener = object : CommittedFactsListener {
        override fun onCommitted(sequence: Long, facts: CommittedGameFacts) = handle(facts)

        override fun onReleased(sequence: Long) = Unit
    }

    /** 使用過 debug 指令、不再產生成果的場次。 */
    private val excludedMatchIds: MutableSet<Uuid> = ConcurrentHashMap.newKeySet()

    /** 開始接收已提交事實。 */
    fun startSession() {
        store.setCommittedFactsListener(committedFactsListener)
    }

    /** 停止接收已提交事實並清除排除的場次，讓下一個 session 從頭開始。 */
    fun stopSession() {
        store.setCommittedFactsListener(CommittedFactsListener.NONE)
        excludedMatchIds.clear()
    }

    /**
     * 之後不再為 [matchId] 產生成果；debug 指令改寫或檢查桌況時呼叫。
     *
     * @param matchId 要排除的場次。
     */
    fun excludeMatch(matchId: Uuid) {
        excludedMatchIds += matchId
    }

    /**
     * 判定一筆已提交事實的成果，有成果時排到伺服器主執行緒授予；排除的場次不判定。
     *
     * @param facts 本次交易對單一桌子提交的事實。
     */
    internal fun handle(facts: CommittedGameFacts) {
        if (facts.matchId in excludedMatchIds) return
        val batches = runCatching { detector.detect(facts) }.getOrElse { cause ->
            logger.warn("Achievement detection failed for match {} at table {}", facts.matchId, facts.venueId, cause)
            return
        }
        if (batches.isEmpty()) return
        scope.launch(dispatchers.main) {
            batches.forEach { achievements ->
                runCatching { gateway.grant(achievements) }.onFailure { cause ->
                    logger.warn("Granting achievements {} to player {} failed", achievements.achievementIds, achievements.playerId, cause)
                }
            }
        }
    }
}
