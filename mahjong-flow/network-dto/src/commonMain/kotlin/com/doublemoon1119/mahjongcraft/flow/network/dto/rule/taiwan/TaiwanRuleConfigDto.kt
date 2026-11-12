package com.doublemoon1119.mahjongcraft.flow.network.dto.rule.taiwan

import com.doublemoon1119.mahjongcraft.flow.network.dto.rule.GameLengthDto
import com.doublemoon1119.mahjongcraft.flow.network.dto.rule.MahjongRuleConfigDto
import com.doublemoon1119.mahjongcraft.flow.network.dto.rule.MultiRonPolicyDto
import com.doublemoon1119.mahjongcraft.logic.rules.taiwan.TaiwanRuleConfig
import kotlinx.serialization.Serializable

/**
 * [TaiwanRuleConfig] 的完整網路 DTO。起始手牌張數、保留區張數、玩家人數與暗槓揭示欄位還原時使用規則固定值。
 *
 * @property initialHandSize 起始手牌張數。
 * @property reservedTileCount 規則保留區的牌張數。
 * @property scoreConfig 計分設定。
 * @property gameLength 對局長度。
 * @property minimumWinConstraint 和牌所需的最低翻數或點數限制。
 * @property minPlayers 最少玩家數量。
 * @property maxPlayers 最多玩家數量。
 * @property multiRonPolicy 多家榮和處理政策。
 * @property useFlowerTiles 是否使用花牌。
 * @property fourWindsAbortiveDraw 是否啟用四風連打流局。
 * @property kanClearsPassedWin 暗槓或加槓前後等待牌相同時，是否額外解除過水。
 * @property generalReadyDeclaration 是否可在天聽、地聽以外的時機宣告聽牌（報聽）並計報聽台。
 * @property revealsClosedKanTiles 暗槓時是否揭示牌面。
 */
@Serializable
data class TaiwanRuleConfigDto(
    val initialHandSize: Int,
    val reservedTileCount: Int,
    val scoreConfig: TaiwanScoreConfigDto,
    val gameLength: GameLengthDto,
    val minimumWinConstraint: Int,
    val minPlayers: Int,
    val maxPlayers: Int,
    val multiRonPolicy: MultiRonPolicyDto,
    val useFlowerTiles: Boolean,
    val fourWindsAbortiveDraw: Boolean,
    val kanClearsPassedWin: Boolean,
    val generalReadyDeclaration: Boolean,
    val revealsClosedKanTiles: Boolean,
) : MahjongRuleConfigDto
