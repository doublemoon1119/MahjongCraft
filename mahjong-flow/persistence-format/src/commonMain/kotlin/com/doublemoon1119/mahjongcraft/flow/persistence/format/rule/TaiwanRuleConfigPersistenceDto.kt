package com.doublemoon1119.mahjongcraft.flow.persistence.format.rule

import com.doublemoon1119.mahjongcraft.logic.rules.taiwan.TaiwanRuleConfig
import kotlinx.serialization.Serializable

/**
 * [TaiwanRuleConfig] 的完整 persistence DTO。
 *
 * @property useFlowerTiles 是否使用花牌。
 * @property minimumWinConstraint 和牌所需的最低台數限制。
 * @property baseScore 和牌的基本分數。
 * @property pointPerTai 每一台增加的分數。
 * @property initialScore 對局開始時每位玩家的分數。
 * @property bustThreshold 低於此分數時判定為擊飛；沒有設定時為 null。
 * @property gameLength 對局長度。
 * @property multiRonPolicy 一炮多響的處理方式。
 */
@Serializable
data class TaiwanRuleConfigPersistenceDto(
    val useFlowerTiles: Boolean,
    val minimumWinConstraint: Int,
    val baseScore: Int,
    val pointPerTai: Int,
    val initialScore: Int,
    val bustThreshold: Int?,
    val gameLength: TaiwanGameLengthPersistenceDto,
    val multiRonPolicy: MultiRonPolicyPersistenceDto,
)
