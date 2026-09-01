package com.doublemoon1119.mahjongcraft.flow.network.dto.message

import com.doublemoon1119.mahjongcraft.flow.common.game.model.PlayerDecisionPhase
import kotlinx.serialization.Serializable

/** 玩家目前取得決策權的網路階段。 */
@Serializable
enum class PlayerDecisionPhaseDto {
    /** 玩家正在進行開局準備。 */
    ROUND_PREPARATION,

    /** 玩家自己的回合。 */
    OWN_TURN,

    /** 玩家正在回應其他玩家的捨牌。 */
    DISCARD_REACTION,

    /** 玩家正在回應搶槓視窗。 */
    KAN_REACTION,
}

/** 將流程決策階段轉成網路 DTO。 */
fun PlayerDecisionPhase.toDto(): PlayerDecisionPhaseDto = when (this) {
    PlayerDecisionPhase.ROUND_PREPARATION -> PlayerDecisionPhaseDto.ROUND_PREPARATION
    PlayerDecisionPhase.OWN_TURN -> PlayerDecisionPhaseDto.OWN_TURN
    PlayerDecisionPhase.DISCARD_REACTION -> PlayerDecisionPhaseDto.DISCARD_REACTION
    PlayerDecisionPhase.KAN_REACTION -> PlayerDecisionPhaseDto.KAN_REACTION
}

/** 將網路決策階段還原成 flow common 型別。 */
fun PlayerDecisionPhaseDto.toDomain(): PlayerDecisionPhase = when (this) {
    PlayerDecisionPhaseDto.ROUND_PREPARATION -> PlayerDecisionPhase.ROUND_PREPARATION
    PlayerDecisionPhaseDto.OWN_TURN -> PlayerDecisionPhase.OWN_TURN
    PlayerDecisionPhaseDto.DISCARD_REACTION -> PlayerDecisionPhase.DISCARD_REACTION
    PlayerDecisionPhaseDto.KAN_REACTION -> PlayerDecisionPhase.KAN_REACTION
}

/**
 * 一次權威決策計時同步的剩餘時間。
 *
 * @property phase 目前決策階段。
 * @property baseRemainingMillis 尚未使用的基本思考時間。
 * @property reserveRemainingMillis 尚未使用的保留思考時間。
 */
@Serializable
data class DecisionTimerStatusDto(
    val phase: PlayerDecisionPhaseDto,
    val baseRemainingMillis: Long,
    val reserveRemainingMillis: Long,
    val prompt: PlayerDecisionPromptDto? = null,
)

/**
 * `mahjongcraft:decision_timer_update` S2C 頻道的權威計時 payload。
 *
 * [status] 為 null 表示玩家已失去該遊戲的決策權，客戶端必須停止顯示舊倒數。
 *
 * @property gameId 計時狀態所屬遊戲的 UUID 字串。
 * @property status 目前有效的計時狀態；停止計時時為 null。
 */
@Serializable
data class DecisionTimerUpdatePayloadDto(
    val gameId: String,
    val status: DecisionTimerStatusDto?,
)
