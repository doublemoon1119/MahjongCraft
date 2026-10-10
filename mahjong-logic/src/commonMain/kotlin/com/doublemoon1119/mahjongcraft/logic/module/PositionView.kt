package com.doublemoon1119.mahjongcraft.logic.module

import com.doublemoon1119.mahjongcraft.logic.base.IdentifiedTile
import com.doublemoon1119.mahjongcraft.logic.table.MahjongPlayerSnapshot
import com.doublemoon1119.mahjongcraft.logic.table.TableStateSnapshot
import kotlin.uuid.Uuid

/**
 * 以評估者本人為觀察者的局面視角。
 *
 * [snapshot] 必須是以 [evaluatorId] 為觀察者產生的桌況快照，因此查詢只能使用這位玩家實際看得到的資訊。
 *
 * @property snapshot 以評估者為觀察者的桌況快照。
 * @property evaluatorId 進行查詢的玩家。
 */
data class PositionView(
    val snapshot: TableStateSnapshot,
    val evaluatorId: Uuid,
) {
    /** 評估者本人。 */
    val evaluator: MahjongPlayerSnapshot
        get() = player(evaluatorId)

    /** 取得桌上指定玩家；不存在時拋出例外。 */
    fun player(playerId: Uuid): MahjongPlayerSnapshot = snapshot.players.first { it.id == playerId }

    /** 評估者換成 [evaluator] 之後的視角。 */
    fun withEvaluator(evaluator: MahjongPlayerSnapshot): PositionView {
        require(evaluator.id == evaluatorId) { "Replacement must be the evaluator" }
        return copy(snapshot = snapshot.copy(players = snapshot.players.map { if (it.id == evaluatorId) evaluator else it }))
    }

    /**
     * 評估者手牌中的 [tileId] 移到評估者移出手牌的牌（[MahjongPlayerSnapshot.setAsideTiles]）末端之後的視角。
     *
     * 評估者手牌中沒有這張牌時拋出例外。
     */
    fun withTileSetAside(tileId: Uuid): PositionView {
        val self = evaluator
        val tile = (self.hand.standingTiles + listOfNotNull(self.hand.lastDrawn)).first { it.id == tileId }
        val face = requireNotNull(tile.tile) { "The evaluator must see its own tiles" }
        return withEvaluator(
            self.copy(
                hand = self.hand.copy(
                    standingTiles = self.hand.standingTiles.filterNot { it.id == tileId },
                    lastDrawn = self.hand.lastDrawn?.takeUnless { it.id == tileId },
                ),
                setAsideTiles = self.setAsideTiles + IdentifiedTile(tileId, face),
            ),
        )
    }
}
