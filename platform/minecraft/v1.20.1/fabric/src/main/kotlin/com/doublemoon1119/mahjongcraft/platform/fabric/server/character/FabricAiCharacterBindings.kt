package com.doublemoon1119.mahjongcraft.platform.fabric.server.character

import com.doublemoon1119.mahjongcraft.flow.common.error.ExtensionFailurePolicy
import com.doublemoon1119.mahjongcraft.platform.fabric.logging.mahjongCraftLogger
import com.doublemoon1119.mahjongcraft.platform.fabric.server.persistence.FabricAuthoritativeStatePersistence
import com.doublemoon1119.mahjongcraft.platform.minecraft.character.AiCharacterBindingKey
import com.doublemoon1119.mahjongcraft.platform.minecraft.character.AiCharacterLeaseCoordinator
import kotlin.uuid.Uuid

/**
 * 共用入座與生命週期查詢同一份角色綁定，不維護第二份實體名冊。
 *
 * @property persistence 查詢目前權威場次，拒絕前一場的角色。
 * @param failurePolicy 原生擴充失敗的隔離政策。
 */
internal class FabricAiCharacterBindings(
    private val persistence: FabricAuthoritativeStatePersistence,
    failurePolicy: ExtensionFailurePolicy,
) {
    /** 記錄來源與代理身分，不輸出手牌等私有狀態。 */
    private val logger = mahjongCraftLogger(FabricAiCharacterBindings::class)

    /** 每份代理最近一次失敗摘要；重試時不重複記錄相同問題。 */
    private val reported = mutableMapOf<AiCharacterBindingKey, String>()

    /** 負責唯一接管、session 與所有權的共用核心。 */
    val coordinator = AiCharacterLeaseCoordinator<FabricAiCharacterActor>(failurePolicy) { key, cause ->
        val signature = "${cause::class.qualifiedName}:${cause.message}"
        if (reported.put(key, signature) != signature) {
            val capabilityId = persistence.characterSnapshot()?.characters?.matches?.get(key.matchId)?.get(key.playerId)?.definition?.worldCapabilityId
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
     * 移除已離開對局的角色與診斷。
     *
     * @param expected 目前權威名冊中的代理。
     */
    fun retain(expected: Set<AiCharacterBindingKey>) {
        coordinator.retain(expected)
        reported.keys.retainAll(expected)
    }

    /**
     * 取得目前場次有效的角色。
     *
     * @param tableId 桌子 UUID。
     * @param playerId AI UUID。
     * @return 本場有效的角色，否則為 null。
     */
    fun find(tableId: Uuid, playerId: Uuid): FabricAiCharacterActor? {
        val game = persistence.characterSnapshot()?.authoritative?.games?.get(tableId) ?: return null
        if (playerId !in game.aiPlayerIds) return null
        return coordinator.find(AiCharacterBindingKey(tableId, game.matchId, playerId))
    }
}
