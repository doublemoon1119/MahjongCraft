package com.doublemoon1119.mahjongcraft.flow.network.dto.snapshot

import com.doublemoon1119.mahjongcraft.flow.network.dto.component.ComponentViewDto
import com.doublemoon1119.mahjongcraft.flow.network.dto.model.WindDto
import com.doublemoon1119.mahjongcraft.flow.network.dto.rule.MahjongRuleConfigDto
import com.doublemoon1119.mahjongcraft.logic.table.TableStateSnapshot
import kotlinx.serialization.Polymorphic
import kotlinx.serialization.Serializable

/**
 * [TableStateSnapshot] 的 observer-specific 網路 DTO。
 *
 * @property componentViews 本次允許交付的具型別規則資料。
 * @property id 對局 UUID 字串。
 * @property players 玩家快照。
 * @property config 本局規則設定。
 * @property tileWall 牌牆快照。
 * @property dealerPlayerId 莊家玩家 UUID 字串。
 * @property prevalentWind 場風。
 * @property roundNumber 跨場風累計、從一開始計算的局數。
 * @property roundPosition 場次位置。
 * @property comboCount 本場數。
 * @property currentPlayerIndex 目前玩家的座位索引。
 * @property finishedPlayerIds 已完成本局玩家 UUID 字串。
 * @property physicalWallLayout 實體牌牆布局；沒有時為 `null`。
 * @property openingDiceRolls 本局開局程序已完成的逐輪擲骰結果。
 * @property publicTileIds 所有合法觀察者都可讀取牌面的牌張 ID。
 * @property publicEvidenceTileIds 由對局事件明確公開過的牌張 UUID 字串。
 * @property tilePoseStates 規則描述的逐張共同姿態，不含原始授權名單。
 */
@Serializable
data class TableStateSnapshotDto(
    val id: String,
    val players: List<MahjongPlayerSnapshotDto>,
    val config: MahjongRuleConfigDto,
    val tileWall: TileWallSnapshotDto,
    val dealerPlayerId: String,
    val prevalentWind: WindDto,
    val roundNumber: Int,
    val roundPosition: MatchRoundPositionDto,
    val comboCount: Int,
    val currentPlayerIndex: Int,
    val finishedPlayerIds: Set<String>,
    val physicalWallLayout: TileWallPhysicalLayoutDto?,
    val openingDiceRolls: List<OpeningDiceRollDto>,
    val publicTileIds: Set<String>,
    val publicEvidenceTileIds: Set<String>,
    val tilePoseStates: List<TilePoseStateDto>,
    val componentViews: List<@Polymorphic ComponentViewDto>,
)
