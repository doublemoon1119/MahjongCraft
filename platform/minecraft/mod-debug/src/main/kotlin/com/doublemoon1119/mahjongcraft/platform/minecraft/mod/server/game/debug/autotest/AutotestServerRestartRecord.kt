package com.doublemoon1119.mahjongcraft.platform.minecraft.mod.server.game.debug.autotest

import kotlinx.serialization.Serializable

/**
 * 伺服器自動測試第一段關機前記下的狀態，第二段讀回世界後拿來比對。
 *
 * @property tables 各張牌桌進行中的牌局。
 * @property decision 關機時正在倒數的機器人決策時間。
 */
@Serializable
internal data class AutotestServerRestartRecord(
    val tables: List<AutotestServerTableRecord>,
    val decision: AutotestServerDecisionRecord,
)

/**
 * 一張牌桌關機前進行中的牌局。
 *
 * @property rule 牌桌的規則模組識別碼。
 * @property tableId 牌桌識別碼。
 * @property botName 坐在這張牌桌的機器人玩家名稱。
 * @property matchId 這一場的識別碼。
 * @property roomPlayerIds 房間成員，依房間順序。
 * @property fingerprint 牌局進度的摘要：局位、莊家、輪到誰、牌山張數、捨牌順序與各家的分數和手牌，重新讀回後應該完全相同。
 */
@Serializable
internal data class AutotestServerTableRecord(
    val rule: String,
    val tableId: String,
    val botName: String,
    val matchId: String,
    val roomPlayerIds: List<String>,
    val fingerprint: String,
)

/**
 * 關機時機器人正在倒數的決策時間。
 *
 * @property tableId 牌桌識別碼。
 * @property playerId 機器人玩家識別碼。
 * @property baseRemainingMillis 這次決策剩下的基本時間（毫秒）。
 * @property reserveRemainingMillis 剩下的保留思考時間（毫秒）。
 */
@Serializable
internal data class AutotestServerDecisionRecord(
    val tableId: String,
    val playerId: String,
    val baseRemainingMillis: Long,
    val reserveRemainingMillis: Long,
)
