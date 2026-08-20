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
    val riichiStickCount: Int = 0,
) : DynamicRuleState,
    TileWallRevealable {
    /**
     * 取得「寶牌指示器」的 [Uuid]
     */
    override fun getVisibleTileIds(state: TableState): Set<Uuid> = getDoraIndicators(state).first.map { it.id }.toSet()

    /**
     * 計算並取得寶牌、裏寶牌列表。
     *
     * 資料來源必須是 [TableState.initialDeadWall]，不能用 [TableState.tileWall]——後者是
     * `GameInitializer.buildOpenedWall()` 建立時就已經排除王牌的活牌堆（`TileWall(layoutResult.drawOrder)`），
     * 而且會隨每次摸牌持續縮短；[KanDeclarationApplier] 的嶺上摸牌（`TileWall.drawLast()`）目前也是
     * 直接從這個活牌堆尾端摸給玩家，並不會真的去動 [TableState.initialDeadWall]。也就是說在這個
     * 簡化後的實作裡，王牌集合從開局到終局都完全固定不變，這裡不需要、也不應該對索引做任何隨槓數
     * 或摸牌次數變動的補償計算——過去用 `state.tileWall` 當來源、外加 `(4 - kanCount)` 補償位移的
     * 寫法，是把「活牌堆尾端會縮短」跟「王牌本身固定不變」搞混，算出來的指示牌會隨場上摸牌次數持續
     * 飄移，是真正影響到寶牌算分的錯誤，不只是呈現層看不到指示牌翻面而已。
     *
     * [initialDeadWall] 的排列順序（[FourSidedWallLayoutSupport] 建牌時決定）是「離開門缺口最近的
     * 一墩排最前面，往深處排到最後」，每墩固定 [上層, 下層]；`FIRST_INDICATOR_OFFSET`（4）比照通行
     * 日麻慣例跳過最前面 2 墩（王牌區前段留給嶺上摸牌的慣例位置，即使本實作的嶺上摸牌實際不取自
     * 這裡，仍維持指示牌起始位置與傳統一致），之後每多一槓就往深一墩、多公開一組寶牌／裏寶牌。
     *
     * @return Pair<寶牌列表, 裏寶牌列表>
     */
    fun getDoraIndicators(state: TableState): Pair<List<IdentifiedTile>, List<IdentifiedTile>> {
        val doraIndicators = mutableListOf<IdentifiedTile>()
        val uraDoraIndicators = mutableListOf<IdentifiedTile>()

        val wanPai = state.initialDeadWall

        // 牌桌上槓的總數
        val kanCount = state.players.sumOf { p ->
            p.hand.exposedMelds.count {
                it.type == MeldType.OPEN_KAN || it.type == MeldType.ADDED_KAN || it.type == MeldType.CLOSED_KAN
            }
        }

        // 每多 1 槓多公開 1 組寶牌／裏寶牌，最多 5 組（4 槓封頂）。
        val indicatorCount = (1 + kanCount).coerceAtMost(5)

        for (i in 0 until indicatorCount) {
            val baseIndex = FIRST_INDICATOR_OFFSET + (i * 2)

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
         * 第一組寶牌／裏寶牌指示牌在 [TableState.initialDeadWall] 裡的起始索引，比照通行日麻慣例跳過
         * 王牌區最前面 2 墩（4 張），見 [getDoraIndicators] KDoc。
         */
        const val FIRST_INDICATOR_OFFSET = 4
    }
}
