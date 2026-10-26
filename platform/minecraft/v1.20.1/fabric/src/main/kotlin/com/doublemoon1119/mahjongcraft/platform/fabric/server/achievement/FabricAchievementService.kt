package com.doublemoon1119.mahjongcraft.platform.fabric.server.achievement

import com.doublemoon1119.mahjongcraft.flow.api.event.CommittedMatchEventBatch
import com.doublemoon1119.mahjongcraft.flow.common.concurrency.AppCoroutineScope
import com.doublemoon1119.mahjongcraft.flow.common.concurrency.CoroutineDispatchers
import com.doublemoon1119.mahjongcraft.flow.common.error.ExtensionFailurePolicy
import com.doublemoon1119.mahjongcraft.platform.fabric.logging.mahjongCraftLogger
import com.doublemoon1119.mahjongcraft.platform.minecraft.achievement.GameAchievementDetector
import com.doublemoon1119.mahjongcraft.platform.minecraft.achievement.GameAchievementResolverRegistry
import com.doublemoon1119.mahjongcraft.platform.minecraft.achievement.PlayerAchievements
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

/**
 * 將公開事件批次中的通用與規則專屬成果合併，交給原版統計與進度。
 *
 * 收到同筆公開批次後才判定通用與規則專屬成果。
 * 同一玩家、同筆交易只排程一次合併後的授予。原生玩家資料只在主執行緒授予時讀取，離線略過且不補發。
 * 規則專屬判定失敗時整批不授予，其他交易仍可繼續。
 *
 * @property scope 執行授予的協程 scope；隨 server session 結束取消。
 * @property dispatchers 取得伺服器主執行緒 dispatcher。
 * @property gateway 把成果交給原版統計與進度系統。
 * @property failurePolicy 判斷規則成果判定與授予失敗能否隔離。
 * @param resolverRegistry 提供規則專屬成果判定器。
 */
class FabricAchievementService(
    private val scope: AppCoroutineScope,
    private val dispatchers: CoroutineDispatchers,
    resolverRegistry: GameAchievementResolverRegistry,
    private val gateway: AchievementGrantGateway,
    private val failurePolicy: ExtensionFailurePolicy,
) {
    /** 記錄成果判定與授予的個別失敗。 */
    private val logger = mahjongCraftLogger(FabricAchievementService::class)

    /** 批次通用成果與規則專屬成果的合併判定。 */
    private val detector = GameAchievementDetector(resolverRegistry)

    /**
     * 解析事件批次並交付合併後的成果，不保留原始對局或事實。
     *
     * @param batch 同一筆權威提交產生的公開事件批次。
     * @param isSessionActive 在準備及授予時確認來源 session。
     */
    internal fun consume(batch: CommittedMatchEventBatch, isSessionActive: () -> Boolean = { true }) {
        if (!isSessionActive()) return
        val achievements = try {
            detector.detect(batch)
        } catch (cause: Throwable) {
            if (cause is CancellationException || !failurePolicy.isIsolatable(cause)) throw cause
            logger.warn("Achievement batch detection failed for match {} at table {}", batch.matchId, batch.venueId, cause)
            return
        }
        grant(achievements, isSessionActive)
    }

    /** 將同筆交易合併後的成果排到主執行緒，逐人隔離授予失敗。 */
    private fun grant(achievements: List<PlayerAchievements>, isSessionActive: () -> Boolean) {
        if (achievements.isEmpty()) return
        scope.launch(dispatchers.main) {
            achievements.forEach { player ->
                if (!isSessionActive()) return@launch
                try {
                    gateway.grant(player)
                } catch (cause: Throwable) {
                    if (cause is CancellationException || !failurePolicy.isIsolatable(cause)) throw cause
                    logger.warn("Granting achievements {} to player {} failed", player.achievementIds, player.playerId, cause)
                }
            }
        }
    }
}
