package com.doublemoon1119.mahjongcraft.platform.minecraft.tile

import com.doublemoon1119.mahjongcraft.logic.tile.component.TilePose
import com.doublemoon1119.mahjongcraft.logic.tile.component.TilePoseState
import kotlin.uuid.Uuid

/**
 * 記錄各桌已成功套用的逐張手牌姿態，只刷新姿態有變化的牌區。
 *
 * 呼叫端須序列化存取；新局與桌子清理時呼叫 [clear]，新的執行階段呼叫 [reset]。
 * 玩家識別碼只用來區分呈現成功的範圍，不是整手平放開關。
 */
class HandPoseUpdatePlanner {
    /** 各桌、各玩家已成功套用的逐張非預設姿態。 */
    private val applied = mutableMapOf<Uuid, MutableMap<Uuid, Map<Uuid, TilePose>>>()

    /**
     * 找出逐張姿態有變化的玩家，不把查詢當成套用成功。
     *
     * @param tableId 桌子識別碼。
     * @param statesByPlayer 每位玩家當前手牌的逐張姿態。
     * @return 需要更新的牌區所屬玩家。
     */
    fun changedPlayers(tableId: Uuid, statesByPlayer: Map<Uuid, List<TilePoseState>>): Set<Uuid> = statesByPlayer.filter { (player, states) -> applied[tableId]?.get(player).orEmpty() != overrides(states) }.keys.toSet()

    /**
     * 記錄一位玩家已成功呈現的逐張姿態，不修改同桌其他玩家的紀錄。
     *
     * @param tableId 桌子識別碼。
     * @param playerId 已成功更新的玩家。
     * @param states 這位玩家當前手牌中已套用的姿態。
     */
    fun recordApplied(tableId: Uuid, playerId: Uuid, states: List<TilePoseState>) {
        applied.getOrPut(tableId) { mutableMapOf() }[playerId] = overrides(states)
    }

    /**
     * 清除指定桌子的呈現紀錄。
     *
     * @param tableId 桌子識別碼。
     */
    fun clear(tableId: Uuid) {
        applied.remove(tableId)
    }

    /** 清除整個執行階段的呈現紀錄。 */
    fun reset() {
        applied.clear()
    }

    /** 只保存有別於預設直立姿態的資料，並建立獨立索引。 */
    private fun overrides(states: List<TilePoseState>): Map<Uuid, TilePose> = states
        .filter { it.pose != TilePose.UPRIGHT }.associate { it.tileId to it.pose }
}
