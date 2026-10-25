package com.doublemoon1119.mahjongcraft.platform.fabric.server.achievement

import com.doublemoon1119.mahjongcraft.flow.api.event.CommittedMatchEventBatch
import com.doublemoon1119.mahjongcraft.flow.common.concurrency.AppCoroutineScope
import com.doublemoon1119.mahjongcraft.flow.common.concurrency.CoroutineDispatchers
import com.doublemoon1119.mahjongcraft.flow.common.game.history.CommittedGameFacts
import com.doublemoon1119.mahjongcraft.logic.module.MahjongModuleRegistry
import com.doublemoon1119.mahjongcraft.platform.fabric.logging.mahjongCraftLogger
import com.doublemoon1119.mahjongcraft.platform.fabric.server.event.GameEventExclusions
import com.doublemoon1119.mahjongcraft.platform.minecraft.achievement.GameAchievementDetector
import com.doublemoon1119.mahjongcraft.platform.minecraft.achievement.GameAchievementResolverRegistry
import com.doublemoon1119.mahjongcraft.platform.minecraft.achievement.PlayerAchievements
import kotlinx.coroutines.launch

/**
 * 將公開事件批次與暫留的規則專屬成果合併，交給原版統計與進度。
 *
 * 提交時只解析規則專屬成果並複製結果；解鎖後收到同筆公開批次，才判定通用成果。
 * 同一玩家、同筆交易只排程一次合併後的授予。原生玩家資料只在主執行緒授予時讀取，離線略過且不補發。
 * 規則專屬判定失敗時整批不授予，其他交易仍可繼續。
 *
 * @property scope 執行授予的協程 scope；隨 server session 結束取消。
 * @property dispatchers 取得伺服器主執行緒 dispatcher。
 * @property exclusions debug 場次的共用排除名單。
 * @property gateway 把成果交給原版統計與進度系統。
 * @property moduleRegistry 依提交設定識別規則。
 * @param resolverRegistry 提供規則專屬成果判定器。
 */
class FabricAchievementService(
    private val scope: AppCoroutineScope,
    private val dispatchers: CoroutineDispatchers,
    private val moduleRegistry: MahjongModuleRegistry,
    resolverRegistry: GameAchievementResolverRegistry,
    private val gateway: AchievementGrantGateway,
    private val exclusions: GameEventExclusions,
) {
    /** 記錄成果判定與授予的個別失敗。 */
    private val logger = mahjongCraftLogger(FabricAchievementService::class)

    /** 批次通用成果與暫留規則專屬成果的合併判定。 */
    private val detector = GameAchievementDetector(moduleRegistry, resolverRegistry)

    /**
     * 解析規則專屬成果，建立可隨事件佇列交付的接收端，不保留原始對局或事實。
     *
     * @param facts 已提交交易，僅在本次同步呼叫中使用。
     * @param isSessionActive 在準備及授予時確認來源 session。
     * @return 本筆交易的接收端；已排除、session 失效或規則判定失敗時為 null。
     */
    internal fun prepare(facts: CommittedGameFacts, isSessionActive: () -> Boolean = { true }): PreparedBatch? {
        if (!isSessionActive() || exclusions.contains(facts.matchId)) return null
        val game = facts.game ?: checkNotNull(facts.previousGame)
        val ruleId = moduleRegistry.getModule(game.tableState.config).id
        val matchId = facts.matchId
        val venueId = facts.venueId
        val rule = runCatching { detector.resolveRule(facts) }.getOrElse { cause ->
            logger.warn("Achievement detection failed for match {} at table {}", matchId, venueId, cause)
            return null
        }
        val ruleOnly = runCatching {
            game.tableState.players.filterNot { game.isAi(it.id) }.mapNotNull { player ->
                rule[player.id]?.takeIf { it.isNotEmpty() }?.let { PlayerAchievements(player.id, matchId, ruleId, it) }
            }
        }.getOrElse { cause ->
            logger.warn("Achievement snapshot failed for match {} at table {}", matchId, venueId, cause)
            return null
        }
        return PreparedBatch(ruleOnly.isNotEmpty()) { batch ->
            val achievements = runCatching {
                if (batch == null) ruleOnly else detector.detect(batch, rule)
            }.getOrElse { cause ->
                logger.warn("Achievement batch detection failed for match {} at table {}", matchId, venueId, cause)
                return@PreparedBatch
            }
            grant(achievements, isSessionActive)
        }
    }

    /** 將同筆交易合併後的成果排到主執行緒，逐人隔離授予失敗。 */
    private fun grant(achievements: List<PlayerAchievements>, isSessionActive: () -> Boolean) {
        if (achievements.isEmpty()) return
        scope.launch(dispatchers.main) {
            achievements.forEach { player ->
                if (!isSessionActive()) return@launch
                runCatching { gateway.grant(player) }.onFailure { cause ->
                    logger.warn("Granting achievements {} to player {} failed", player.achievementIds, player.playerId, cause)
                }
            }
        }
    }

    /**
     * 只攜帶複製後成果與批次處理方法的內建接收端。
     *
     * @property hasRuleAchievements 沒有公開事件時，是否仍須交付既有規則專屬成果。
     * @property consume 合併公開批次與本筆規則專屬成果的方法；不捕捉原始事實或對局。
     */
    internal class PreparedBatch(
        val hasRuleAchievements: Boolean,
        private val consume: (CommittedMatchEventBatch?) -> Unit,
    ) {
        /** 接收同筆交易的公開批次；只有規則專屬成果時不建立空的公開批次。 */
        fun deliver(batch: CommittedMatchEventBatch?) = consume(batch)
    }
}
