package com.doublemoon1119.mahjongcraft.logic.rules.riichi

import com.doublemoon1119.mahjongcraft.logic.base.IdentifiedTile
import com.doublemoon1119.mahjongcraft.logic.base.MeldType
import com.doublemoon1119.mahjongcraft.logic.config.DynamicRuleState
import com.doublemoon1119.mahjongcraft.logic.table.TableState
import com.doublemoon1119.mahjongcraft.logic.table.TileWallRevealable
import kotlin.uuid.Uuid

/**
 * 日本麻將特有的動態桌況狀態。
 *
 * @property riichiStickCount 場上存留的立直棒數量。
 */
data class RiichiDynamicState(
    var riichiStickCount: Int = 0
) : DynamicRuleState, TileWallRevealable {
    /**
     * 取得「寶牌指示器」的 [Uuid]
     */
    override fun getVisibleTileIds(state: TableState): Set<Uuid> {
        return getDoraIndicators(state).first.map { it.id }.toSet()
    }

    /**
     * 計算並取得寶牌、裏寶牌列表
     *
     * @return Pair<寶牌列表, 裏寶牌列表>
     */
    fun getDoraIndicators(state: TableState): Pair<List<IdentifiedTile>, List<IdentifiedTile>> {
        val doraIndicators = mutableListOf<IdentifiedTile>()
        val uraDoraIndicators = mutableListOf<IdentifiedTile>()

        // 日麻規則的王牌數量為 14
        val wanPaiCount = state.config.deadTileCount

        // 牌桌上槓的總數
        val kanCount = state.players.sumOf { p ->
            p.hand.exposedMelds.count {
                it.type == MeldType.OPEN_KAN || it.type == MeldType.ADDED_KAN || it.type == MeldType.CLOSED_KAN
            }
        }

        // 取得王牌
        val wanPai = state.tileWall.getAllTiles()
            .takeLast(wanPaiCount)
            .reversed()  // 反轉後索引 0 轉為嶺上位置，便於由左至右計算

        // 根據槓數推算指示牌索引
        // 初始 0 槓 = 1 張 (索引 = (4 - kanCount))
        // 每多 1 槓 = 多 1 張 (索引 = (4 - kanCount) + n*2)
        val indicatorCount = (1 + kanCount).coerceAtMost(5)

        for (i in 0 until indicatorCount) {
            // 補償計算：(4 - kanCount) 抵消了因為 drawLast() 導致嶺上牌移除後的索引位移
            // i * 2 則用於跳過每一墩的下層牌（裏寶牌指示牌）
            val baseIndex = (4 - kanCount) + (i * 2)

            // 取得寶牌指示牌
            wanPai.getOrNull(baseIndex)?.let {
                doraIndicators.add(it)
            }

            // 取得裏寶牌指示牌
            wanPai.getOrNull(baseIndex + 1)?.let {
                uraDoraIndicators.add(it)
            }
        }

        return doraIndicators to uraDoraIndicators
    }
}