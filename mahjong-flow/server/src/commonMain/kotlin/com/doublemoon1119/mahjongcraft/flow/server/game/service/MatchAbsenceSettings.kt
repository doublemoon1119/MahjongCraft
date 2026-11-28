package com.doublemoon1119.mahjongcraft.flow.server.game.service

import kotlin.time.Duration

/**
 * 整桌真人都不在玩時怎麼處理對局，由伺服器設定提供，每次讀取都是目前的值。
 *
 * - [inactiveRoundLimit]：連續幾局所有真人都只有逾時代打、沒有主動操作時結束這場。0 代表不因此結束。
 * - [offlinePauseLimit]：所有真人都離線而暫停的累計時間到達這個值時中止這場，不計成績。[Duration.ZERO] 代表一直等待。
 */
interface MatchAbsenceSettings {
    /** 連續幾局無人主動操作就結束這場。0 代表不因此結束。 */
    val inactiveRoundLimit: Int

    /** 全員離線而暫停的累計時間上限。[Duration.ZERO] 代表一直等待。 */
    val offlinePauseLimit: Duration

    /** 不設定的預設值。 */
    companion object {
        /** 不因無人操作或離線結束對局的設定。 */
        val DISABLED: MatchAbsenceSettings = object : MatchAbsenceSettings {
            override val inactiveRoundLimit: Int = 0
            override val offlinePauseLimit: Duration = Duration.ZERO
        }
    }
}
