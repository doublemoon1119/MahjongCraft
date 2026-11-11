package com.doublemoon1119.mahjongcraft.logic.rules.riichi

import com.doublemoon1119.mahjongcraft.logic.base.IdentifiedTile
import com.doublemoon1119.mahjongcraft.logic.component.ComponentPatch
import com.doublemoon1119.mahjongcraft.logic.component.ComponentSet
import com.doublemoon1119.mahjongcraft.logic.table.TableState
import com.doublemoon1119.mahjongcraft.logic.table.layout.WallRingLayoutSupport

/**
 * 一局內的補牌與槓寶牌公開進度。
 *
 * @property completedSupplementalDrawCount 已完成的槓後補牌次數。
 * @property revealedKanDoraCount 已公開的追加槓寶牌指示牌數量。
 * @property pendingKanDoraReveals 尚未公開的槓寶牌項目，依建立順序排列。
 * @property completedNorthDrawCount 已完成的拔北補牌次數。
 */
data class RiichiWallProgress(
    val completedSupplementalDrawCount: Int = 0,
    val revealedKanDoraCount: Int = 0,
    val pendingKanDoraReveals: List<RiichiPendingKanDoraReveal> = emptyList(),
    val completedNorthDrawCount: Int = 0,
) {
    /**
     * 計算並取得寶牌、裏寶牌列表。
     *
     * 資料來源必須是 [TableState.reservedWallTiles]，不能用 [TableState.tileWall]——後者只保存仍可正常摸取
     * 的活牌。每次槓後補牌會移除死牌區最前方的嶺上牌，再將活牌尾端補到死牌區末端，因此原本的
     * 寶牌與裏寶牌會在列表中向前移動，指示牌索引必須扣除已完成的補牌次數。
     *
     * [TableState.reservedWallTiles] 的排列順序（[WallRingLayoutSupport] 建牌時決定）是「離開門缺口最近的
     * 一墩排最前面，往深處排到最後」，每墩固定 [上層, 下層]；最前面的嶺上牌（四人日麻 4 張、三人麻將 8 張，
     * 見 [RiichiFamilyRuleConfig.rinshanTileCount]）是王牌區前段的嶺上摸牌語意槽位，指示牌從其後開始。
     * 指示牌實體位置依已完成補牌次數（槓與拔北合計）往前位移，而公開組數僅依已正式公開的槓寶牌數量
     * 增加；延後公開期間兩者不必相等。
     *
     * @param state 目前的權威桌況與死牌排列。
     * @return Pair<寶牌列表, 裏寶牌列表>
     */
    fun getDoraIndicators(state: TableState): Pair<List<IdentifiedTile>, List<IdentifiedTile>> {
        val doraIndicators = mutableListOf<IdentifiedTile>()
        val uraDoraIndicators = mutableListOf<IdentifiedTile>()

        val wanPai = state.reservedWallTiles

        // 開局固定公開一組；追加指示牌只依正式公開進度計算，不把等待項目提前視為可見。
        val indicatorCount = (1 + revealedKanDoraCount).coerceAtMost(5)

        val rinshanTileCount = (state.config as? RiichiFamilyRuleConfig)?.rinshanTileCount ?: DEFAULT_RINSHAN_TILE_COUNT
        val completedDraws = completedSupplementalDrawCount + completedNorthDrawCount
        val indicatorStartIndex = (rinshanTileCount - completedDraws).coerceAtLeast(0)
        // 開局王牌之後的牌是補牌時從活牌尾端補進來的：每墩先補下層、再補上層，列表順序因此是 [下層, 上層]。
        val replenishedStartIndex = state.config.reservedTileCount - completedDraws
        for (i in 0 until indicatorCount) {
            val baseIndex = indicatorStartIndex + (i * 2)
            val isReplenishedStack = baseIndex >= replenishedStartIndex
            val upperIndex = if (isReplenishedStack) baseIndex + 1 else baseIndex
            val lowerIndex = if (isReplenishedStack) baseIndex else baseIndex + 1

            // 取得寶牌指示牌（每墩的上層）
            wanPai.getOrNull(upperIndex)?.let {
                doraIndicators.add(it)
            }

            // 取得裏寶牌指示牌（同一墩的下層）
            wanPai.getOrNull(lowerIndex)?.let {
                uraDoraIndicators.add(it)
            }
        }

        return doraIndicators to uraDoraIndicators
    }

    companion object {
        /** 桌況規則不是日麻配置時，王牌最前方嶺上牌的張數；比照四人日麻。 */
        internal const val DEFAULT_RINSHAN_TILE_COUNT = 4
    }
}

/**
 * 建立只更新本局牌山進度的桌況變更。
 *
 * @param previous 計算這次進度時讀到的牌山進度，作為預期舊值。
 * @return 不影響供託或其他規則資料的組件變更。
 */
fun RiichiWallProgress.toTableComponentPatch(previous: RiichiWallProgress): ComponentPatch = ComponentPatch(
    values = ComponentSet.EMPTY.with(RiichiTableComponents.WALL_PROGRESS, this),
    expected = ComponentSet.EMPTY.with(RiichiTableComponents.WALL_PROGRESS, previous),
)
