package com.doublemoon1119.mahjongcraft.flow.network.dto.rule.taiwan

import com.doublemoon1119.mahjongcraft.flow.network.dto.rule.GameLengthDto
import com.doublemoon1119.mahjongcraft.flow.network.dto.rule.MahjongRuleConfigDto
import com.doublemoon1119.mahjongcraft.flow.network.dto.rule.MultiRonPolicyDto
import com.doublemoon1119.mahjongcraft.logic.rules.taiwan.TaiwanRuleConfig
import kotlinx.serialization.Serializable

/**
 * [TaiwanRuleConfig] 的完整網路 DTO。
 *
 * @property initialHandSize 起始手牌張數。
 * @property deadTileCount 王牌張數。
 * @property scoreConfig 計分設定。
 * @property gameLength 對局長度。
 * @property minimumWinConstraint 和牌所需的最低翻數或點數限制。
 * @property minPlayers 最少玩家數量。
 * @property maxPlayers 最多玩家數量。
 * @property multiRonPolicy 多家榮和處理政策。
 * @property useFlowerTiles 是否使用花牌。
 * @property revealsClosedKanTiles 暗槓時是否揭示牌面。
 */
@Serializable
data class TaiwanRuleConfigDto(
    val initialHandSize: Int,
    val deadTileCount: Int,
    val scoreConfig: TaiwanScoreConfigDto,
    val gameLength: GameLengthDto,
    val minimumWinConstraint: Int,
    val minPlayers: Int,
    val maxPlayers: Int,
    val multiRonPolicy: MultiRonPolicyDto,
    val useFlowerTiles: Boolean,
    val revealsClosedKanTiles: Boolean,
) : MahjongRuleConfigDto
