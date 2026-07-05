package com.doublemoon1119.mahjongcraft.logic.table

import com.doublemoon1119.mahjongcraft.logic.config.DynamicRuleState
import com.doublemoon1119.mahjongcraft.logic.config.MahjongRuleConfig
import java.util.*

/**
 * [TableState] 的不可變快照，用於 Client 端渲染。
 *
 * @property id 桌局的唯一識別碼
 * @property players 所有玩家的快照列表
 * @property config 當前桌局使用的規則配置
 * @property tileWall 牌山快照，僅包含規則定義下應公開可見的牌張
 * @property prevalentWind 當前場風
 * @property roundNumber 當前局數
 * @property comboCount 當前連莊次數
 * @property currentPlayerIndex 當前回合玩家的索引
 * @property dynamicRuleState 規則特定的動態桌況狀態
 */
data class TableStateSnapshot(
    val id: UUID,
    val players: List<MahjongPlayerSnapshot>,
    val config: MahjongRuleConfig,
    val tileWall: TileWallSnapshot,
    val prevalentWind: Wind,
    val roundNumber: Int,
    val comboCount: Int,
    val currentPlayerIndex: Int,
    val dynamicRuleState: DynamicRuleState?
)

/**
 * 產生一個相對於指定觀察者可見範圍的 [TableState] 不可變快照。
 *
 * @param observerId 觀察者的玩家識別碼，用於判斷手牌與牌山的可見範圍
 * @return 依據 [observerId] 計算可見範圍的桌局快照
 */
fun TableState.toSnapshot(observerId: UUID): TableStateSnapshot {
    // 若規則實作 TileWallRevealable，則計算牌山中應公開可見的牌張 UUID
    val visibleTileIds = (dynamicRuleState as? TileWallRevealable)
        ?.getVisibleTileIds(this)
        ?: emptySet()

    return TableStateSnapshot(
        id = this.id,
        players = this.players.map { it.toSnapshot(isVisible = it.id == observerId) },
        config = this.config,
        tileWall = this.tileWall.toSnapshot(visibleTileIds = visibleTileIds),
        prevalentWind = this.prevalentWind,
        roundNumber = this.roundNumber,
        comboCount = this.comboCount,
        currentPlayerIndex = this.currentPlayerIndex,
        dynamicRuleState = this.dynamicRuleState
    )
}