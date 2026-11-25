package com.doublemoon1119.mahjongcraft.platform.minecraft.mod.server.character

import com.doublemoon1119.mahjongcraft.flow.network.dto.rule.NetworkJson
import com.doublemoon1119.mahjongcraft.platform.minecraft.character.AiCharacterTargetPayload
import com.doublemoon1119.mahjongcraft.platform.minecraft.mod.network.MahjongChannels
import com.doublemoon1119.mahjongcraft.platform.minecraft.mod.server.TrackingPlayers
import net.minecraft.server.level.ServerLevel
import net.minecraft.server.level.ServerPlayer
import net.minecraft.world.entity.Entity

/**
 * 只同步已追蹤角色的公開接管標記，不包含牌局資訊或放置權限。
 *
 * @property json 共用線路格式。
 */
internal class AiCharacterTracking(
    private val json: NetworkJson,
) {
    /**
     * 通知目前追蹤此實體的玩家。
     *
     * @param entity 原生角色。
     * @param bound 是否仍被接管。
     */
    fun broadcast(entity: Entity, bound: Boolean) {
        val world = entity.level() as? ServerLevel ?: return
        if (world.getEntity(entity.uuid) !== entity) return
        TrackingPlayers.of(entity).forEach { player -> send(entity, player, bound) }
    }

    /**
     * 傳送公開標記；伺服器執行互動時仍重新驗證接管。
     *
     * @param entity 被追蹤的實體。
     * @param player 正在追蹤的玩家。
     * @param bound 是否被接管。
     */
    fun send(entity: Entity, player: ServerPlayer, bound: Boolean) {
        MahjongChannels.aiCharacterTarget.sendTo(
            player = player,
            json = json,
            value = AiCharacterTargetPayload(entity.uuid.toString(), bound),
        )
    }
}
