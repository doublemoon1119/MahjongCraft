package com.doublemoon1119.mahjongcraft.platform.minecraft.mod.server.character

import com.doublemoon1119.mahjongcraft.flow.common.error.ExtensionFailurePolicy
import com.doublemoon1119.mahjongcraft.platform.minecraft.character.AiCharacterBindingKey
import com.doublemoon1119.mahjongcraft.platform.minecraft.character.AiCharacterLeaseCoordinator
import com.doublemoon1119.mahjongcraft.platform.minecraft.mod.logging.mahjongCraftLogger
import com.doublemoon1119.mahjongcraft.platform.minecraft.mod.server.persistence.AuthoritativeStatePersistence
import net.minecraft.world.entity.Entity
import kotlin.uuid.Uuid
import kotlin.uuid.toKotlinUuid

/**
 * 共用入座與生命週期查詢同一份角色綁定，不維護第二份實體名冊。
 *
 * @property persistence 查詢目前權威場次，拒絕前一場的角色。
 * @param failurePolicy 原生擴充失敗的隔離政策。
 */
internal class AiCharacterBindings(
    private val persistence: AuthoritativeStatePersistence,
    failurePolicy: ExtensionFailurePolicy,
) {
    /** 記錄來源與代理身分，不輸出手牌等私有狀態。 */
    private val logger = mahjongCraftLogger(AiCharacterBindings::class)

    /** 每份代理最近一次失敗摘要；重試時不重複記錄相同問題。 */
    private val reported = mutableMapOf<AiCharacterBindingKey, String>()

    /** 負責唯一接管、session 與所有權的共用核心。 */
    val coordinator = AiCharacterLeaseCoordinator<AiCharacterActor>(failurePolicy) { key, cause ->
        val signature = "${cause::class.qualifiedName}:${cause.message}"
        if (reported.put(key, signature) != signature) {
            val capabilityId = persistence.characterSnapshot()?.characters?.matchPlayers(key.matchId)?.get(key.playerId)?.definition?.worldCapabilityId
            logger.warn("AI character failed tableId={} matchId={} playerId={} capabilityId={}", key.tableId, key.matchId, key.playerId, capabilityId, cause)
        }
    }

    /** 清除前一個 session 的診斷並開始接收角色。 */
    fun startSession() {
        coordinator.startSession()
        reported.clear()
    }

    /** 釋放全部角色，清除診斷。 */
    fun stopSession() {
        try {
            coordinator.stopSession()
        } finally {
            reported.clear()
        }
    }

    /**
     * 移除不屬於目前任何權威對局的角色與診斷；入座前先呼叫，新一場的入座不會遇到上一場留下的角色。
     *
     * 只能在伺服器主執行緒呼叫。
     *
     * @return 目前權威對局應有的角色身分。
     */
    fun releaseStale(): Set<AiCharacterBindingKey> {
        val expected = coordinator.retainGames(persistence.characterSnapshot()?.authoritative?.games?.values.orEmpty())
        reported.keys.retainAll(expected)
        return expected
    }

    /**
     * 取得目前場次有效的角色。
     *
     * @param tableId 桌子 UUID。
     * @param playerId AI UUID。
     * @return 本場有效的角色，否則為 null。
     */
    fun find(tableId: Uuid, playerId: Uuid): AiCharacterActor? {
        val game = persistence.characterSnapshot()?.authoritative?.games?.get(tableId) ?: return null
        if (playerId !in game.aiPlayerIds) return null
        return coordinator.find(AiCharacterBindingKey(tableId, game.matchId, playerId))
    }

    /**
     * 由被點擊的原生實體查詢本場接管，不接受客戶端提供桌子或玩家身分。
     *
     * @param entity 被點擊的實體。
     * @return 本場有效的接管及角色，否則為 null。
     */
    fun findEntity(entity: Entity): Pair<AiCharacterBindingKey, AiCharacterActor>? {
        val key = coordinator.keyFor(entity.uuid.toKotlinUuid()) ?: return null
        val actor = find(key.tableId, key.playerId)?.takeIf { it.entity === entity } ?: return null
        return key to actor
    }
}
