package com.doublemoon1119.mahjongcraft.flow.common.game.event

import com.doublemoon1119.mahjongcraft.flow.api.event.MatchCompletion
import com.doublemoon1119.mahjongcraft.logic.table.TableState
import kotlin.uuid.Uuid

/**
 * 規則解析整場對局結束公開標記時可讀取的權威資料。
 *
 * @property ruleModuleId 使用中的規則模組識別碼。
 * @property matchId 對局識別碼。
 * @property completion 對局正常完成或中途終止。
 * @property reasonId 整場結束原因識別碼。
 * @property previousState 交易前的權威桌況。
 * @property finalState 正常結束時的最終桌況；中途終止時為 null。
 * @property finalScoresByPlayerId 終局時各玩家的分數。
 */
data class MatchEndPublicMarkerContext(
    val ruleModuleId: String,
    val matchId: Uuid,
    val completion: MatchCompletion,
    val reasonId: String,
    val previousState: TableState,
    val finalState: TableState?,
    val finalScoresByPlayerId: Map<Uuid, Int>,
)
