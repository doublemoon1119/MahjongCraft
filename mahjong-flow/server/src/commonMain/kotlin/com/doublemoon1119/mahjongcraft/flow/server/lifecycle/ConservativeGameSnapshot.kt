package com.doublemoon1119.mahjongcraft.flow.server.lifecycle

import com.doublemoon1119.mahjongcraft.flow.common.game.model.Game
import com.doublemoon1119.mahjongcraft.logic.base.TileFaceView
import com.doublemoon1119.mahjongcraft.logic.table.TableStateSnapshot
import com.doublemoon1119.mahjongcraft.logic.table.mapTiles
import com.doublemoon1119.mahjongcraft.logic.table.toSnapshot
import com.doublemoon1119.mahjongcraft.logic.tile.component.resolveTileAreas
import kotlin.uuid.Uuid

/**
 * 規則投影失敗時，只保留本人持有的牌面與權威桌況已記下的公共證據。
 *
 * 不呼叫規則投影，不重用部分完成的快照，也不帶出組件、外觀或額外授權。
 * 這是讀檔還原的保守顯示，不會修補或改寫權威對局。
 *
 * @param observerId 這份快照的參與者。
 * @return 只包含明確可讀資訊的桌況快照。
 */
internal fun Game.conservativeSnapshotFor(observerId: Uuid): TableStateSnapshot {
    val readableTiles = tableState.resolveTileAreas().filter { area ->
        area.ownerId == observerId || area.tile.id in tableState.publicTileEvidenceIds
    }.associate { area -> area.tile.id to area.tile.tile }
    return tableState.toSnapshot(
        visibleHandPlayerIds = emptySet(),
        revealsClosedKanTiles = false,
    ).mapTiles { tile -> tile.copy(face = readableTiles[tile.id]?.let(::TileFaceView)) }
}
