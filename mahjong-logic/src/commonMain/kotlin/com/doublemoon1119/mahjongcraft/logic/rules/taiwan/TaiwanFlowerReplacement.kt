package com.doublemoon1119.mahjongcraft.logic.rules.taiwan

import com.doublemoon1119.mahjongcraft.logic.base.GameAction
import com.doublemoon1119.mahjongcraft.logic.base.IdentifiedTile
import com.doublemoon1119.mahjongcraft.logic.rules.taiwan.tile.TaiwanTileTypes
import com.doublemoon1119.mahjongcraft.logic.table.TableState
import com.doublemoon1119.mahjongcraft.logic.table.drawFromReservedWall
import kotlin.uuid.Uuid

/** 台灣麻將公開花牌與從保留區補牌的純狀態操作。 */
object TaiwanFlowerReplacement {
    /**
     * 一次只取得補牌、不再公開花牌的結果。
     *
     * @property tableState 補牌已加入指定玩家手牌的桌況。
     * @property replacementTile 本次從保留區取得的補牌。
     */
    data class Supplemented(
        val tableState: TableState,
        val replacementTile: IdentifiedTile,
    )

    /**
     * 公開指定玩家持有的花牌，但尚不補牌。
     *
     * @param tableState 公開前的桌況。
     * @param playerId 持有花牌的玩家。
     * @param flower 要公開的花牌。
     * @return 更新後桌況；牌不在該玩家手中或不是花牌時為 null。
     */
    fun reveal(
        tableState: TableState,
        playerId: Uuid,
        flower: IdentifiedTile,
    ): TableState? {
        if (!TaiwanTileTypes.isFlower(flower.tile)) return null
        val index = tableState.players.indexOfFirst { it.id == playerId }
        if (index == -1) return null
        val player = tableState.players[index]
        if (flower !in player.hand.standingTiles) return null
        val remainingTiles = player.hand.tiles.filterNot { it.id == flower.id }
        val remainingDrawn = player.hand.lastDrawn?.takeUnless { it.id == flower.id }
        val flowers = player.tileAreas[TaiwanPlayerTileAreaIds.FLOWERS]?.tiles.orEmpty() + flower
        val updated = player.copy(
            hand = player.hand.copy(tiles = remainingTiles, lastDrawn = remainingDrawn),
            tileAreas = player.tileAreas.withArea(TaiwanPlayerTileAreaIds.FLOWERS, flowers),
        ).recordAction(TAIWAN_REVEAL_FLOWER_ACTION)
        return tableState.copy(
            players = tableState.players.toMutableList().apply { this[index] = updated },
            publicTileEvidenceIds = tableState.publicTileEvidenceIds + flower.id,
        )
    }

    /**
     * 從保留區缺口側補一張牌，活牌尾端同時補入保留區。
     *
     * @param tableState 補牌前的桌況。
     * @param playerId 接收補牌的玩家。
     * @return 補牌結果；已無合法補牌或玩家不存在時為 null。
     */
    fun supplement(tableState: TableState, playerId: Uuid): Supplemented? {
        val supplement = drawFromReservedWall(tableState) ?: return null
        val index = tableState.players.indexOfFirst { it.id == playerId }
        if (index == -1) return null
        val player = tableState.players[index]
        val hand = player.hand
        val replacement = supplement.drawnTiles.single()
        val normalizedTiles = hand.lastDrawn?.let { hand.tiles + it } ?: hand.tiles
        val updated = player.copy(
            hand = hand.copy(tiles = normalizedTiles, lastDrawn = replacement),
        ).recordAction(GameAction.Draw)
        val remainingWallIds = (supplement.tileWall.getAllTiles() + supplement.reservedWallTiles)
            .mapTo(mutableSetOf()) { it.id }
        val result = tableState.copy(
            players = tableState.players.toMutableList().apply { this[index] = updated },
            tileWall = supplement.tileWall,
            reservedWallTiles = supplement.reservedWallTiles,
            physicalWallLayout = tableState.physicalWallLayout?.retainOnly(remainingWallIds),
        )
        return Supplemented(result, replacement)
    }
}
