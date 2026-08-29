package com.doublemoon1119.mahjongcraft.logic.table

import com.doublemoon1119.mahjongcraft.logic.config.DynamicRuleState
import com.doublemoon1119.mahjongcraft.logic.config.MahjongRuleConfig
import kotlin.uuid.Uuid

/**
 * [TableState] 的不可變快照，用於 Client 端渲染。
 *
 * @property id 桌局的唯一識別碼
 * @property players 所有玩家的快照列表
 * @property config 當前桌局使用的規則配置
 * @property tileWall 牌山快照，僅包含規則定義下應公開可見的牌張
 * @property dealerPlayerId 本局權威莊家 Uuid。
 * @property prevalentWind 當前場風
 * @property roundNumber 當前局數
 * @property roundPosition 當前權威局位。
 * @property comboCount 當前連莊次數
 * @property currentPlayerIndex 當前回合玩家的索引
 * @property dynamicRuleState 規則特定的動態桌況狀態
 * @property finishedPlayerIds 本局已完成、不再參與後續回合的玩家 Uuid 集合，供 HUD、牌面與觀戰
 * 呈現使用。
 */
data class TableStateSnapshot(
    val id: Uuid,
    val players: List<MahjongPlayerSnapshot>,
    val config: MahjongRuleConfig,
    val tileWall: TileWallSnapshot,
    val dealerPlayerId: Uuid,
    val prevalentWind: Wind,
    val roundNumber: Int,
    val roundPosition: MatchRoundPosition,
    val comboCount: Int,
    val currentPlayerIndex: Int,
    val dynamicRuleState: DynamicRuleState?,
    val finishedPlayerIds: Set<Uuid>,
)

/**
 * 產生一個相對於指定觀察者可見範圍的 [TableState] 不可變快照。
 *
 * @param visibleHandPlayerIds 可以顯示完整手牌的玩家識別碼集合。
 * @return 依據明確可見範圍產生的桌局快照。
 */
fun TableState.toSnapshot(visibleHandPlayerIds: Set<Uuid>): TableStateSnapshot {
    // 若規則實作 TileWallRevealable，則計算牌山中應公開可見的牌張 Uuid
    val visibleTileIds = (dynamicRuleState as? TileWallRevealable)
        ?.getVisibleTileIds(this)
        ?: emptySet()

    return TableStateSnapshot(
        id = this.id,
        players = this.players.map {
            it.toSnapshot(isVisible = it.id in visibleHandPlayerIds, revealsClosedKanTiles = config.revealsClosedKanTiles)
        },
        config = this.config,
        tileWall = this.tileWall.toSnapshot(visibleTileIds = visibleTileIds, deadWallTiles = this.initialDeadWall),
        dealerPlayerId = this.dealerPlayerId,
        prevalentWind = this.prevalentWind,
        roundNumber = this.roundNumber,
        roundPosition = this.roundPosition,
        comboCount = this.comboCount,
        currentPlayerIndex = this.currentPlayerIndex,
        dynamicRuleState = this.dynamicRuleState,
        finishedPlayerIds = this.finishedPlayerIds,
    )
}
