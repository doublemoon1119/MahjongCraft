package com.doublemoon1119.mahjongcraft.platform.fabric.server.character

import com.doublemoon1119.mahjongcraft.flow.network.dto.rule.NetworkJson
import com.doublemoon1119.mahjongcraft.platform.fabric.network.MahjongChannels
import com.doublemoon1119.mahjongcraft.platform.minecraft.character.AiCharacterTargetPayload
import net.fabricmc.fabric.api.networking.v1.PlayerLookup
import net.minecraft.entity.Entity
import net.minecraft.server.network.ServerPlayerEntity
import net.minecraft.server.world.ServerWorld

/**
 * 只同步已追蹤角色的公開接管標記，不包含牌局資訊或放置權限。
 *
 * @property json 共用線路格式。
 */
internal class FabricAiCharacterTracking(
    private val json: NetworkJson,
) {
    /**
     * 通知目前追蹤此實體的玩家。
     *
     * @param entity 原生角色。
     * @param bound 是否仍被接管。
     */
    fun broadcast(entity: Entity, bound: Boolean) {
        val world = entity.world as? ServerWorld ?: return
        if (world.getEntity(entity.uuid) !== entity) return
        PlayerLookup.tracking(entity).forEach { player -> send(entity, player, bound) }
    }

    /**
     * 傳送公開標記；伺服器執行互動時仍重新驗證接管。
     *
     * @param entity 被追蹤的實體。
     * @param player 正在追蹤的玩家。
     * @param bound 是否被接管。
     */
    fun send(entity: Entity, player: ServerPlayerEntity, bound: Boolean) {
        MahjongChannels.aiCharacterTarget.sendTo(
            player = player,
            json = json,
            value = AiCharacterTargetPayload(entity.uuid.toString(), bound),
        )
    }
}
