package com.doublemoon1119.mahjongcraft.flow.server.game.usecase

import com.doublemoon1119.mahjongcraft.logic.base.IdentifiedTile
import com.doublemoon1119.mahjongcraft.logic.module.MahjongRuleModule
import com.doublemoon1119.mahjongcraft.logic.table.MahjongPlayer
import com.doublemoon1119.mahjongcraft.logic.table.TableState
import kotlin.uuid.Uuid

/**
 * 把一張交給玩家、但玩家沒有榮和的牌記入 [MahjongPlayer.passedTilesInRound]。
 *
 * 規則以這份清單判斷自己下次取牌前的限制（例如日麻的同巡振聽）；記錄前先以規則的牌面解讀正規化。
 */
internal object PassedTileRecorder {
    /**
     * 將 [tile] 記入 [playerIds] 每位玩家的放過清單。
     *
     * @param tableState 目前的權威桌況。
     * @param tile 交給玩家、但沒有被榮和的牌。
     * @param playerIds 要記錄的玩家。
     * @param module 該對局採用的規則模組。
     * @return 記錄後的桌況。
     */
    fun record(
        tableState: TableState,
        tile: IdentifiedTile,
        playerIds: Set<Uuid>,
        module: MahjongRuleModule<*>,
    ): TableState {
        if (playerIds.isEmpty()) return tableState
        val canonicalTile = module.createTileInterpretationPolicy().canonicalize(tile.tile)
        return tableState.copy(
            players = tableState.players.map { player ->
                if (player.id in playerIds) player.addPassedTile(canonicalTile) else player
            },
        )
    }
}
