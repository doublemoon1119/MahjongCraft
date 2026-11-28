package com.doublemoon1119.mahjongcraft.flow.common.game.model

import kotlin.uuid.Uuid

/**
 * 真人玩家在目前這一局怎麼完成自己的決策，用來判斷整桌是否已經沒有人在玩。
 *
 * - 主動：玩家自己送出的命令，或玩家自己開啟的自動操作代為送出的決策。
 * - 逾時：思考時間用完後由伺服器代打的決策。
 * - AI 的決策不記錄。每一局開始時清空。
 *
 * @property activePlayerIds 這一局至少主動完成一次決策的玩家。
 * @property timedOutPlayerIds 這一局至少有一次決策因逾時由伺服器代打的玩家。
 */
data class RoundPlayerActivity(
    val activePlayerIds: Set<Uuid> = emptySet(),
    val timedOutPlayerIds: Set<Uuid> = emptySet(),
) {
    /**
     * 記下 [playerId] 主動完成了一次決策。
     *
     * @param playerId 完成決策的玩家。
     * @return 加入這次紀錄後的活動。
     */
    fun withActive(playerId: Uuid): RoundPlayerActivity = copy(activePlayerIds = activePlayerIds + playerId)

    /**
     * 記下 [playerId] 有一次決策因逾時由伺服器代打。
     *
     * @param playerId 逾時的玩家。
     * @return 加入這次紀錄後的活動。
     */
    fun withTimedOut(playerId: Uuid): RoundPlayerActivity = copy(timedOutPlayerIds = timedOutPlayerIds + playerId)

    /**
     * [humanPlayerIds] 是否每一位都在這一局逾時過，而且沒有任何一位主動操作。
     *
     * @param humanPlayerIds 這場的真人玩家。
     * @return 至少有一位真人，而且整桌真人這一局都只有逾時代打時為 true。
     */
    fun isInactive(humanPlayerIds: Set<Uuid>): Boolean = humanPlayerIds.isNotEmpty() &&
        humanPlayerIds.none { it in activePlayerIds } &&
        humanPlayerIds.all { it in timedOutPlayerIds }
}
