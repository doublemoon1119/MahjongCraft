package com.doublemoon1119.mahjongcraft.logic.table

import com.doublemoon1119.mahjongcraft.logic.base.IdentifiedTile
import com.doublemoon1119.mahjongcraft.logic.base.toKnownTile
import com.doublemoon1119.mahjongcraft.logic.base.toSnapshot
import com.doublemoon1119.mahjongcraft.logic.component.ComponentViewSet
import com.doublemoon1119.mahjongcraft.logic.config.MahjongRuleConfig
import com.doublemoon1119.mahjongcraft.logic.table.layout.TileWallPhysicalLayout
import com.doublemoon1119.mahjongcraft.logic.table.opening.OpeningDiceRoll
import com.doublemoon1119.mahjongcraft.logic.tile.TileInterpretationPolicy
import com.doublemoon1119.mahjongcraft.logic.tile.component.TilePoseState
import kotlin.uuid.Uuid

/**
 * [TableState] 對指定觀察者的不可變可見快照。
 *
 * @property componentViews 已按讀取者裁切的規則資料，不含原始權威組件。
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
 * @property finishedPlayerIds 本局已完成、不再參與後續回合的玩家 Uuid 集合。
 * @property physicalWallLayout 目前仍在牌牆中的牌張抽象位置；規則不支援時為 null。
 * @property openingDiceRolls 本局完整擲骰記錄；規則不使用擲骰時為空清單。
 * @property publicTileIds 所有合法觀察者都可讀取牌面的牌張，不包含私人授權。
 * @property publicEvidenceTileIds 由對局事件明確公開過的牌張，例如流局攤開的手牌、宣告的暗槓；不包含規則讓所有人都
 *   讀得到牌面的牌。
 * @property tilePoseStates 規則要求的逐張共同姿態，不攜帶原始授權名單。
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
    val finishedPlayerIds: Set<Uuid>,
    val physicalWallLayout: TileWallPhysicalLayout?,
    val openingDiceRolls: List<OpeningDiceRoll>,
    val publicTileIds: Set<Uuid>,
    val publicEvidenceTileIds: Set<Uuid>,
    val tilePoseStates: List<TilePoseState>,
    val componentViews: ComponentViewSet = ComponentViewSet.EMPTY,
)

/**
 * 產生一個相對於指定觀察者可見範圍的 [TableState] 不可變快照。
 *
 * 這個低階函式不產生規則組件的公開投影；對外同步或 AI 情境必須再經過
 * flow 的 visibility policy 套用規則專屬投影，不能直接把這個結果當成觀察者快照。
 *
 * @param visibleHandPlayerIds 可以顯示完整手牌的玩家識別碼集合。
 * @param revealsClosedKanTiles 是否顯示他家暗槓牌面；預設使用本局規則的公開政策。
 * @return 依據明確可見範圍產生的桌局快照。
 */
fun TableState.toSnapshot(
    visibleHandPlayerIds: Set<Uuid>,
    revealsClosedKanTiles: Boolean = config.revealsClosedKanTiles,
): TableStateSnapshot {
    // 低階快照只讀明確的公共證據；完整規則效果由正式可見性政策裁切。
    return TableStateSnapshot(
        id = this.id,
        players = this.players.map {
            val player = it
            val snapshot = player.toSnapshot(
                isVisible = player.id in visibleHandPlayerIds,
                revealsClosedKanTiles = revealsClosedKanTiles,
            )
            val handById = player.hand.standingTiles.associateBy { tile -> tile.id }
            snapshot.copy(
                hand = snapshot.hand.copy(
                    standingTiles = snapshot.hand.standingTiles.map { tile ->
                        handById.getValue(tile.id).toSnapshot(tile.tile != null || tile.id in publicTileEvidenceIds)
                    },
                    lastDrawn = snapshot.hand.lastDrawn?.let { tile ->
                        handById.getValue(tile.id).toSnapshot(tile.tile != null || tile.id in publicTileEvidenceIds)
                    },
                ),
            )
        },
        config = this.config,
        tileWall = this.tileWall.toSnapshot(visibleTileIds = publicTileEvidenceIds, reservedWallTiles = this.reservedWallTiles),
        dealerPlayerId = this.dealerPlayerId,
        prevalentWind = this.prevalentWind,
        roundNumber = this.roundNumber,
        roundPosition = this.roundPosition,
        comboCount = this.comboCount,
        currentPlayerIndex = this.currentPlayerIndex,
        finishedPlayerIds = this.finishedPlayerIds,
        physicalWallLayout = this.physicalWallLayout,
        openingDiceRolls = this.openingDiceRolls,
        publicTileIds = publicTileEvidenceIds,
        publicEvidenceTileIds = publicTileEvidenceIds,
        tilePoseStates = emptyList(),
    )
}

/**
 * 這份快照的觀察者看得到牌面的牌張，依牌張 ID 去重。
 *
 * 來源包含：各家手牌與副露中看得到牌面的牌（他家立牌與暗槓是否看得到，由建立快照時的可見範圍決定）、牌河中
 * 未被鳴走的牌、移出手牌公開擺在桌上的牌、牌山中已公開的牌，以及仍在手牌中但已公開的牌。同一張牌可能同時出現在
 * 多個來源，例如被鳴走的捨牌或被搶和的牌，只計一次。
 *
 * @param interpretation 本局規則由已知牌面解讀計算特性的方式。
 * @return 依牌張 ID 去重的可見牌列表。
 */
fun TableStateSnapshot.visibleTiles(interpretation: TileInterpretationPolicy): List<IdentifiedTile> = buildList {
    players.forEach { player ->
        addAll(player.heldTiles(interpretation))
        player.discardPile.entries.filterNot { it.isTaken }.forEach { entry -> entry.tile.toKnownTile(interpretation)?.let(::add) }
    }
    tileWall.tiles.forEach { snapshot -> snapshot.toKnownTile(interpretation)?.let(::add) }
}.distinctBy { it.id }

/**
 * 這位玩家手上看得到牌面的牌：立牌、剛摸到的牌、副露與移出手牌公開擺在桌上的牌，依牌張 ID 去重。
 *
 * 立牌與暗槓是否看得到，由建立快照時的可見範圍決定。
 *
 * @param interpretation 本局規則由已知牌面解讀計算特性的方式。
 * @return 這位玩家快照中可見牌面的牌張，依牌張 ID 去重。
 */
fun MahjongPlayerSnapshot.heldTiles(interpretation: TileInterpretationPolicy): List<IdentifiedTile> = buildList {
    (hand.standingTiles + listOfNotNull(hand.lastDrawn) + hand.melds.flatMap { it.tiles }).forEach { snapshot ->
        snapshot.toKnownTile(interpretation)?.let(::add)
    }
    setAsideTiles.forEach { snapshot -> snapshot.toKnownTile(interpretation)?.let(::add) }
}.distinctBy { it.id }
