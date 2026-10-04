package com.doublemoon1119.mahjongcraft.platform.fabric.client.history

import com.doublemoon1119.mahjongcraft.flow.network.dto.message.HistoryReplayPlayerStateDto
import com.doublemoon1119.mahjongcraft.flow.network.dto.model.TileDto
import com.doublemoon1119.mahjongcraft.flow.network.dto.model.toDomain
import com.doublemoon1119.mahjongcraft.logic.base.TileOrder

/**
 * 保存歷史牌面中各玩家的手牌排序偏好，並提供不修改來源資料的排序結果。
 *
 * 排序偏好以對局識別碼與玩家識別碼共同索引，預設為規則排序；因此同一玩家切換排序不會影響
 * 其他玩家，也不會把某場對局的顯示狀態帶到另一場對局。
 */
internal class HistoryHandSorting {
    /** 只存在本次瀏覽的對局與玩家排序選擇。 */
    private val sortedByPlayer = mutableMapOf<PreferenceKey, Boolean>()

    /**
     * 取得指定玩家目前是否使用規則排序。
     * @param matchId 對局識別碼。
     * @param playerId 玩家識別碼。
     * @return 尚未切換時為 true。
     */
    fun isSorted(matchId: String, playerId: String): Boolean = sortedByPlayer[PreferenceKey(matchId, playerId)] ?: true

    /**
     * 切換指定玩家的手牌排序偏好。
     * @param matchId 對局識別碼。
     * @param playerId 玩家識別碼。
     * @return 切換後是否使用規則排序。
     */
    fun toggle(matchId: String, playerId: String): Boolean {
        val key = PreferenceKey(matchId, playerId)
        val sorted = !(sortedByPlayer[key] ?: true)
        sortedByPlayer[key] = sorted
        return sorted
    }

    /**
     * 依規則排序手牌立牌，並保留最近摸牌的獨立欄位語意。
     *
     * [player.handTiles] 與 [player.lastDrawn] 是不同的保存欄位，因此回傳值只包含立牌；
     * [player.lastDrawn] 不會被排序或附加到結果中。相同牌面的不同實體依原本索引穩定排序。
     *
     * @param player 已驗證的玩家桌況。
     * @param catalog 同一筆歷史桌況的牌面目錄。
     * @param order 規則提供的牌面排序策略；為 null 時保留原本順序。
     * @return 不修改原始 DTO 的立牌索引列表。
     */
    fun orderedTiles(
        player: HistoryReplayPlayerStateDto,
        catalog: List<TileDto>,
        order: TileOrder?,
    ): List<Int> {
        val standingTiles = player.handTiles.filterNot { it == player.lastDrawn }
        if (order == null) return standingTiles.toList()

        return standingTiles.withIndex().sortedWith { left, right ->
            val leftTile = catalog.getOrNull(left.value)?.toDomain()
            val rightTile = catalog.getOrNull(right.value)?.toDomain()
            when {
                leftTile == null && rightTile == null -> left.index.compareTo(right.index)
                leftTile == null -> 1
                rightTile == null -> -1
                else -> order.compare(leftTile, rightTile).takeIf { it != 0 } ?: left.index.compareTo(right.index)
            }
        }.map { it.value }
    }

    /**
     * 對局與玩家的顯示偏好鍵。
     * @property matchId 對局識別碼。
     * @property playerId 玩家身分或穩定初始座位鍵。
     */
    private data class PreferenceKey(
        val matchId: String,
        val playerId: String,
    )
}
