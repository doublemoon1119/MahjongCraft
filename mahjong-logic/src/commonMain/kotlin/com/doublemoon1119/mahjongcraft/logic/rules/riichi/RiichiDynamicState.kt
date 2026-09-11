package com.doublemoon1119.mahjongcraft.logic.rules.riichi

import com.doublemoon1119.mahjongcraft.logic.base.IdentifiedTile
import com.doublemoon1119.mahjongcraft.logic.config.DynamicRuleState
import com.doublemoon1119.mahjongcraft.logic.table.TableState
import com.doublemoon1119.mahjongcraft.logic.table.TileWallRevealable
import kotlin.uuid.Uuid

/**
 * 日本麻將特有的動態桌況狀態。
 *
 * @property riichiStickCount 場上存留的立直棒數量。
 * @property completedSupplementalDrawCount 本局已成功完成的槓後補牌次數。
 */
data class RiichiDynamicState(
    val riichiStickCount: Int = 0,
    val completedSupplementalDrawCount: Int = 0,
) : DynamicRuleState,
    TileWallRevealable {
    /**
     * 取得「寶牌指示器」的 [Uuid]
     */
    override fun getVisibleTileIds(state: TableState): Set<Uuid> = getDoraIndicators(state).first.map { it.id }.toSet()

    /**
     * 計算並取得寶牌、裏寶牌列表。
     *
     * 資料來源必須是 [TableState.reservedWallTiles]，不能用 [TableState.tileWall]——後者只保存仍可正常摸取
     * 的活牌。每次槓後補牌會移除死牌區最前方的嶺上牌，再將活牌尾端補到死牌區末端，因此原本的
     * 寶牌與裏寶牌會在列表中向前移動，指示牌索引必須扣除已完成的補牌次數。
     *
     * [TableState.reservedWallTiles] 的排列順序（[FourSidedWallLayoutSupport] 建牌時決定）是「離開門缺口最近的
     * 一墩排最前面，往深處排到最後」，每墩固定 [上層, 下層]；`FIRST_INDICATOR_OFFSET`（4）比照通行
     * 日麻慣例跳過最前面 2 墩（王牌區前段的嶺上摸牌語意槽位），之後每完成一次槓後補牌就往深一墩、
     * 多公開一組寶牌／裏寶牌。
     *
     * @return Pair<寶牌列表, 裏寶牌列表>
     */
    fun getDoraIndicators(state: TableState): Pair<List<IdentifiedTile>, List<IdentifiedTile>> {
        val doraIndicators = mutableListOf<IdentifiedTile>()
        val uraDoraIndicators = mutableListOf<IdentifiedTile>()

        val wanPai = state.reservedWallTiles

        // 每成功完成 1 次槓後補牌多公開 1 組寶牌／裏寶牌，最多 5 組（4 次補牌封頂）。
        val indicatorCount = (1 + completedSupplementalDrawCount).coerceAtMost(5)

        val indicatorStartIndex =
            (FIRST_INDICATOR_OFFSET - completedSupplementalDrawCount).coerceAtLeast(0)
        for (i in 0 until indicatorCount) {
            val baseIndex = indicatorStartIndex + (i * 2)

            // 取得寶牌指示牌（每墩的上層）
            wanPai.getOrNull(baseIndex)?.let {
                doraIndicators.add(it)
            }

            // 取得裏寶牌指示牌（同一墩的下層）
            wanPai.getOrNull(baseIndex + 1)?.let {
                uraDoraIndicators.add(it)
            }
        }

        return doraIndicators to uraDoraIndicators
    }

    private companion object {
        /**
         * 第一組寶牌／裏寶牌指示牌在 [TableState.reservedWallTiles] 裡的起始索引，比照通行日麻慣例跳過
         * 王牌區最前面 2 墩（4 張），見 [getDoraIndicators] KDoc。
         */
        const val FIRST_INDICATOR_OFFSET = 4
    }
}
