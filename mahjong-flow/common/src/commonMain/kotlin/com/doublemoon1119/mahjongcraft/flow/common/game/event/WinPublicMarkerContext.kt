package com.doublemoon1119.mahjongcraft.flow.common.game.event

import com.doublemoon1119.mahjongcraft.flow.common.game.model.WinSettlementDetailField
import com.doublemoon1119.mahjongcraft.logic.table.TableState
import kotlin.uuid.Uuid

/**
 * 規則解析一次胡牌公開標記時可讀取的權威資料。
 *
 * @property ruleModuleId 使用中的規則模組識別碼。
 * @property matchId 對局識別碼。
 * @property outcomeId 本次胡牌結果識別碼。
 * @property winnerId 本次胡牌玩家。
 * @property previousState 交易前的權威桌況。
 * @property settledState 胡牌結算後的權威桌況。
 * @property detailFields 本次胡牌已產生的規則詳情欄位。
 */
data class WinPublicMarkerContext(
    val ruleModuleId: String,
    val matchId: Uuid,
    val outcomeId: String,
    val winnerId: Uuid,
    val previousState: TableState,
    val settledState: TableState,
    val detailFields: List<WinSettlementDetailField>,
)
