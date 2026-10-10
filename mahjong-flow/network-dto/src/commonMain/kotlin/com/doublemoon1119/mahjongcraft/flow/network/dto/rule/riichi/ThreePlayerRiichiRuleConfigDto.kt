package com.doublemoon1119.mahjongcraft.flow.network.dto.rule.riichi

import com.doublemoon1119.mahjongcraft.flow.network.dto.rule.GameLengthDto
import com.doublemoon1119.mahjongcraft.flow.network.dto.rule.MahjongRuleConfigDto
import com.doublemoon1119.mahjongcraft.flow.network.dto.rule.MultiRonPolicyDto
import com.doublemoon1119.mahjongcraft.logic.rules.riichi.threeplayer.ThreePlayerRiichiRuleConfig
import kotlinx.serialization.Serializable

/**
 * [ThreePlayerRiichiRuleConfig] 的完整網路 DTO；人數、王牌張數等由規則固定的欄位只作為顯示用途傳送。
 *
 * @property initialHandSize 起始手牌張數。
 * @property deadTileCount 王牌張數。
 * @property scoreConfig 計分設定。
 * @property gameLength 對局長度。
 * @property minimumWinConstraint 和牌所需的最低翻數或點數限制。
 * @property minPlayers 最少玩家數量。
 * @property maxPlayers 最多玩家數量。
 * @property multiRonPolicy 多家榮和處理政策。
 * @property redDoraCount 赤寶牌數量。
 * @property allowOpenTanyao 是否允許副露斷么九。
 * @property useLocalYaku 是否使用本地役種。
 * @property revealsClosedKanTiles 暗槓時是否揭示牌面。
 */
@Serializable
data class ThreePlayerRiichiRuleConfigDto(
    val initialHandSize: Int,
    val deadTileCount: Int,
    val scoreConfig: RiichiScoreConfigDto,
    val gameLength: GameLengthDto,
    val minimumWinConstraint: Int,
    val minPlayers: Int,
    val maxPlayers: Int,
    val multiRonPolicy: MultiRonPolicyDto,
    val redDoraCount: Int,
    val allowOpenTanyao: Boolean,
    val useLocalYaku: Boolean,
    val revealsClosedKanTiles: Boolean,
) : MahjongRuleConfigDto
