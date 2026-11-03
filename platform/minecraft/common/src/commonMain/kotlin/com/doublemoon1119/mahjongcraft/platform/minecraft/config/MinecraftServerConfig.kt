package com.doublemoon1119.mahjongcraft.platform.minecraft.config

import kotlin.time.Duration
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.seconds

/** 將斷線政策轉成 server config 使用的穩定值。 */
val DisconnectedPlayerPolicy.configValue: String
    get() = name.lowercase()

/** 將桌子破壞政策轉成 server config 使用的穩定值。 */
val TableBreakPolicy.configValue: String
    get() = name.lowercase()

/**
 * 依目前是否存在 Room／Game 判斷此政策是否允許玩家破壞桌子。
 *
 * @param hasRoom 目前是否有等待中的房間使用此桌。
 * @param hasGame 目前是否有進行中的對局使用此桌。
 * @return 依破壞政策允許玩家執行破壞時為 true。
 */
fun TableBreakPolicy.allowsTableBreak(hasRoom: Boolean, hasGame: Boolean): Boolean = when (this) {
    TableBreakPolicy.DENY_WHILE_OCCUPIED -> !hasRoom && !hasGame
    TableBreakPolicy.ALLOW_WAITING_ROOM_ONLY -> !hasGame
    TableBreakPolicy.ALLOW_AND_TERMINATE -> true
}

/** 將缺失桌子政策轉成 server config 使用的穩定值。 */
val OrphanedTablePolicy.configValue: String
    get() = name.lowercase()

/**
 * MahjongCraft Minecraft 平台的伺服器生命週期設定。
 *
 * @property disconnectedPlayerPolicy 玩家斷線時採用的座位保留政策。
 * @property tableBreakPolicy 麻將桌被破壞時採用的政策。
 * @property orphanedTablePolicy 已確認桌子缺失時採用的資料清理政策。
 * @property disconnectedPlayerTimeout `LEAVE_AFTER_TIMEOUT` 使用的離線寬限時間。
 * @property mahjongTilePhysicalCollisionEnabled 麻將牌是否阻擋玩家及其他非麻將牌 entity。
 * @property history 歷史資料記錄與封存設定。
 * @property missingAiPlayerPolicy 找不到參與遊戲的 AI 玩家時，中止或繼續對局。
 */
data class MinecraftServerConfig(
    val disconnectedPlayerPolicy: DisconnectedPlayerPolicy = DisconnectedPlayerPolicy.LEAVE_IMMEDIATELY,
    val tableBreakPolicy: TableBreakPolicy = TableBreakPolicy.DENY_WHILE_OCCUPIED,
    val orphanedTablePolicy: OrphanedTablePolicy = OrphanedTablePolicy.REMOVE_ALL,
    val disconnectedPlayerTimeout: Duration = DEFAULT_DISCONNECTED_PLAYER_TIMEOUT,
    val mahjongTilePhysicalCollisionEnabled: Boolean = true,
    val history: MinecraftHistoryConfig = MinecraftHistoryConfig(),
    val missingAiPlayerPolicy: MissingAiPlayerPolicy = MissingAiPlayerPolicy.ABORT_MATCH,
) {
    /** Server config 數值邊界與預設值。 */
    companion object {
        /** 斷線逾時允許的最小值。 */
        val MIN_DISCONNECTED_PLAYER_TIMEOUT: Duration = 1.seconds

        /** 斷線逾時允許的最大值。 */
        val MAX_DISCONNECTED_PLAYER_TIMEOUT: Duration = 1.hours

        /** 預設斷線逾時。 */
        val DEFAULT_DISCONNECTED_PLAYER_TIMEOUT: Duration = 30.seconds
    }
}
