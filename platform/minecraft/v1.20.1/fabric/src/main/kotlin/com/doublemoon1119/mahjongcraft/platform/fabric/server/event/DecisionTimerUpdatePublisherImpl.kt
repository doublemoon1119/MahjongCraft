package com.doublemoon1119.mahjongcraft.platform.fabric.server.event

import com.doublemoon1119.mahjongcraft.flow.common.game.service.DecisionTimerUpdate
import com.doublemoon1119.mahjongcraft.flow.common.game.service.DecisionTimerUpdatePublisher
import com.doublemoon1119.mahjongcraft.flow.network.dto.rule.NetworkJson
import com.doublemoon1119.mahjongcraft.platform.fabric.network.MahjongChannels
import com.doublemoon1119.mahjongcraft.platform.fabric.server.FabricServerHolder
import com.doublemoon1119.mahjongcraft.platform.fabric.server.game.PlayerDecisionPromptFactory
import com.doublemoon1119.mahjongcraft.platform.minecraft.decision.DecisionTimerStatusDto
import com.doublemoon1119.mahjongcraft.platform.minecraft.decision.DecisionTimerUpdatePayloadDto
import com.doublemoon1119.mahjongcraft.platform.minecraft.decision.toDto
import kotlin.uuid.Uuid

/**
 * [DecisionTimerUpdatePublisher] 的 Fabric S2C 實作。
 *
 * @property serverHolder 尋找目前在線的目標玩家。
 * @property promptFactory 建立目標玩家可見的決策提示。
 * @property json 編碼計時 payload。
 */
class DecisionTimerUpdatePublisherImpl(
    private val serverHolder: FabricServerHolder,
    private val promptFactory: PlayerDecisionPromptFactory,
    private val json: NetworkJson,
) : DecisionTimerUpdatePublisher {
    /** 只向目前在線的目標玩家送出權威計時更新。 */
    override suspend fun publish(targetPlayerId: Uuid, update: DecisionTimerUpdate) {
        val player = serverHolder.findPlayer(targetPlayerId) ?: return
        val payload = when (update) {
            is DecisionTimerUpdate.Active -> DecisionTimerUpdatePayloadDto(
                gameId = update.gameId.toString(),
                status = DecisionTimerStatusDto(
                    phase = update.phase.toDto(),
                    baseRemainingMillis = update.baseRemainingMillis,
                    reserveRemainingMillis = update.reserveRemainingMillis,
                    prompt = promptFactory.create(update.gameId, targetPlayerId, update.phase),
                ),
            )

            is DecisionTimerUpdate.Stopped -> DecisionTimerUpdatePayloadDto(
                gameId = update.gameId.toString(),
                status = null,
            )
        }
        MahjongChannels.decisionTimerUpdate.sendTo(player, json, payload)
    }
}
