package com.doublemoon1119.mahjongcraft.flow.network.dto.message

import com.doublemoon1119.mahjongcraft.flow.network.dto.snapshot.RoundPreparationSnapshotDto
import com.doublemoon1119.mahjongcraft.flow.network.dto.snapshot.TableStateSnapshotDto
import com.doublemoon1119.mahjongcraft.logic.table.TableStateSnapshot
import kotlinx.serialization.Serializable

/**
 * 沒有伴隨遊戲動作、單純重新同步最新 [TableStateSnapshot] 時使用的 S2C payload。
 *
 * @property gameId 遊戲 UUID 的字串表示。
 * @property snapshot 指定 observer 當下可見的完整遊戲快照。
 * @property aiPlayerIds 由 AI 操控的玩家 UUID 字串；對所有觀察者相同。
 * @property roundPreparation 指定 observer 可見的開局準備狀態。
 * @property handReadinessAnalysis 只向參與者本人公開的目前手牌分析；未聽牌或規則不支援時為 `null`。
 */
@Serializable
data class GameSnapshotSyncPayloadDto(
    val gameId: String,
    val snapshot: TableStateSnapshotDto,
    val aiPlayerIds: List<String>,
    val roundPreparation: RoundPreparationSnapshotDto?,
    val handReadinessAnalysis: HandReadinessAnalysisDto?,
)
