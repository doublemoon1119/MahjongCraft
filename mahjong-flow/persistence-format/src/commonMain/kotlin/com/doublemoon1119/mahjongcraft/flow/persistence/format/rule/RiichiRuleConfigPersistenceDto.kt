package com.doublemoon1119.mahjongcraft.flow.persistence.format.rule

import com.doublemoon1119.mahjongcraft.logic.rules.riichi.RiichiRuleConfig
import kotlinx.serialization.Serializable

/**
 * [RiichiRuleConfig] 的完整 persistence DTO。
 *
 * @property redDoraCount 紅寶牌數量。
 * @property allowOpenTanyao 是否允許副露斷么九。
 * @property useLocalYaku 是否啟用規則專屬役種。
 * @property minimumWinConstraint 和牌所需的最低翻數限制。
 * @property initialScore 對局開始時每位玩家的分數。
 * @property bustThreshold 低於此分數時判定為擊飛；沒有設定時為 null。
 * @property minPointsToWin 和牌至少需要的基本分數。
 * @property notenPenaltyUnit 流局未聽罰符的單位分數。
 * @property gameLength 對局長度。
 * @property multiRonPolicy 一炮多響的處理方式。
 */
@Serializable
data class RiichiRuleConfigPersistenceDto(
    val redDoraCount: Int,
    val allowOpenTanyao: Boolean,
    val useLocalYaku: Boolean,
    val minimumWinConstraint: Int,
    val initialScore: Int,
    val bustThreshold: Int?,
    val minPointsToWin: Int,
    val notenPenaltyUnit: Int,
    val gameLength: RiichiGameLengthPersistenceDto,
    val multiRonPolicy: MultiRonPolicyPersistenceDto,
)
