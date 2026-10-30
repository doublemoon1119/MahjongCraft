package com.doublemoon1119.mahjongcraft.ai

import com.doublemoon1119.mahjongcraft.logic.base.Hand
import com.doublemoon1119.mahjongcraft.logic.base.HandSnapshot
import com.doublemoon1119.mahjongcraft.logic.base.IdentifiedTile
import com.doublemoon1119.mahjongcraft.logic.base.IdentifiedTileSnapshot
import com.doublemoon1119.mahjongcraft.logic.base.Meld
import com.doublemoon1119.mahjongcraft.logic.base.MeldSnapshot
import com.doublemoon1119.mahjongcraft.logic.base.toKnownTile
import com.doublemoon1119.mahjongcraft.logic.tile.TileInterpretationPolicy

/**
 * 把 AI 自己的手牌快照還原成 [Hand]。
 *
 * 快照的立牌已包含剛摸到的牌，還原後的 [Hand.tiles] 即為所有立牌，[Hand.lastDrawn] 為 null。
 * 只能用於觀察者本人的手牌：他家的手牌在快照中不可見，會拋出例外。
 *
 * @param interpretation 本局規則的牌值解讀方式。
 * @return 使用可見牌張重建的手牌；剛摸到的牌保留在立牌清單中。
 * @throws IllegalStateException 立牌或副露中存在不可見的牌張。
 */
internal fun HandSnapshot.toOwnHand(interpretation: TileInterpretationPolicy): Hand = Hand(
    tiles = standingTiles.distinctBy { it.id }.map { it.toVisibleTile(interpretation) },
    melds = melds.map { it.toVisibleMeld(interpretation) },
)

/**
 * 還原一組自己的副露，保留副露身分及已知牌值。
 *
 * @param interpretation 本局規則的牌值解讀方式。
 * @return 重建的副露。
 */
private fun MeldSnapshot.toVisibleMeld(interpretation: TileInterpretationPolicy): Meld = Meld(
    id = id,
    type = type,
    tiles = tiles.map { it.toVisibleTile(interpretation) },
    sourceTile = sourceTile?.toVisibleTile(interpretation),
    sourceDirection = sourceDirection,
    publiclyRevealedTileIds = tiles.map { it.id }.toSet(),
    faceDownTileIds = emptySet(),
)

/**
 * 還原一張觀察者看得到的牌。
 *
 * @param interpretation 本局規則的牌值解讀方式。
 * @return 已知的牌張。
 * @throws IllegalStateException 快照沒有提供這張牌的牌值。
 */
private fun IdentifiedTileSnapshot.toVisibleTile(interpretation: TileInterpretationPolicy): IdentifiedTile = checkNotNull(toKnownTile(interpretation)) { "Tile $id is not visible to the AI" }
