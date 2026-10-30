package com.doublemoon1119.mahjongcraft.logic.component

import kotlin.uuid.Uuid

/** 已通過入口授權的資料讀取情境。 */
sealed interface ComponentReadContext {
    /**
     * 參與對局的玩家。
     *
     * @property playerId 該玩家的識別碼。
     */
    data class Player(val playerId: Uuid) : ComponentReadContext

    /**
     * 已獲准觀戰的讀取者。
     *
     * @property observerId 觀戰者的識別碼，不等於任何參與者的權限。
     */
    data class Spectator(val observerId: Uuid) : ComponentReadContext

    /** 所有合法觀察者都可讀取的內容，不假扮玩家身分。 */
    data object Public : ComponentReadContext
}
