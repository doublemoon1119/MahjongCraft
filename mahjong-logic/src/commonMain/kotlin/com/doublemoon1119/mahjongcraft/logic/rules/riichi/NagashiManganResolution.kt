package com.doublemoon1119.mahjongcraft.logic.rules.riichi

import kotlin.uuid.Uuid

/**
 * 日麻流局滿貫的純規則判定結果。
 *
 * @property achieverPlayerIds 成立者，依桌上座位順序保存。
 * @property scoreDeltas 不含供託、但包含所有成立者自摸滿貫式付款的完整玩家差額。
 */
data class NagashiManganResolution(
    val achieverPlayerIds: Set<Uuid>,
    val scoreDeltas: Map<Uuid, Int>,
)
