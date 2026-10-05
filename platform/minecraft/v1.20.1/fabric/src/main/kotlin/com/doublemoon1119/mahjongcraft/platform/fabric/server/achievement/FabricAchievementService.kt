package com.doublemoon1119.mahjongcraft.platform.fabric.server.achievement

import com.doublemoon1119.mahjongcraft.flow.common.concurrency.AppCoroutineScope
import com.doublemoon1119.mahjongcraft.flow.common.concurrency.CoroutineDispatchers
import com.doublemoon1119.mahjongcraft.flow.common.game.history.CommittedGameFacts
import com.doublemoon1119.mahjongcraft.flow.server.state.AuthoritativeStateStore
import com.doublemoon1119.mahjongcraft.logic.module.MahjongModuleRegistry
import com.doublemoon1119.mahjongcraft.platform.minecraft.achievement.GameAchievementDetector
import com.doublemoon1119.mahjongcraft.platform.minecraft.achievement.GameAchievementResolverRegistry
import com.doublemoon1119.mahjongcraft.platform.minecraft.metadata.MinecraftModMetadata
import kotlinx.coroutines.launch
import org.koin.core.annotation.Single
import org.slf4j.LoggerFactory
import java.util.concurrent.ConcurrentHashMap
import kotlin.uuid.Uuid

/**
 * server session 期間把已提交的對局事實轉成原版統計與進度。
 *
 * 訂閱在 [CoroutineDispatchers.main]（伺服器 tick 佇列）上執行，原版統計與進度只能在該執行緒操作。
 * 單筆事實判定或授予失敗時只記錄警告，不影響後續事實；成果發生時不在線的玩家不補發。
 *
 * @property scope 執行訂閱的協程 scope；隨 server session 結束取消。
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
    private val logger = LoggerFactory.getLogger(MinecraftModMetadata.MOD_ID)

    /** 通用與規則專屬成果判定。 */
    private val detector = GameAchievementDetector(moduleRegistry, resolverRegistry)

    /** 使用過 debug 指令、不再產生成果的場次。 */
    private val excludedMatchIds: MutableSet<Uuid> = ConcurrentHashMap.newKeySet()

    /** 開始訂閱已提交事實；協程隨 server session 的作用域一起結束。 */
    fun startSession() {
        scope.launch(dispatchers.main) {
            store.committedFacts.collect(::handle)
        }
    }

    /** 清除排除的場次，讓下一個 session 從頭開始。 */
    fun stopSession() {
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
     * 判定並授予一筆已提交事實的成果。
     *
     * @param facts 本次交易對單一桌子提交的事實。
     */
    internal fun handle(facts: CommittedGameFacts) {
        if (facts.matchId in excludedMatchIds) return
        val batches = runCatching { detector.detect(facts) }.getOrElse { cause ->
            logger.warn("Achievement detection failed for match {} at table {}", facts.matchId, facts.venueId, cause)
            return
        }
        batches.forEach { achievements ->
            runCatching { gateway.grant(achievements) }.onFailure { cause ->
                logger.warn("Granting achievements {} to player {} failed", achievements.achievementIds, achievements.playerId, cause)
            }
        }
    }
}
