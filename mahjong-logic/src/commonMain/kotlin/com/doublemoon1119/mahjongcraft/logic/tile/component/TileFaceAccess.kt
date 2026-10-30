package com.doublemoon1119.mahjongcraft.logic.tile.component

import kotlin.uuid.Uuid

/** 額外授予的牌面讀取權，不取代對局讀取資格或既有公開證據。 */
sealed interface TileFaceAccess {
    /** 不額外授權。 */
    data object None : TileFaceAccess

    /** 對所有有資格讀取本局的觀察者公開。 */
    data object Public : TileFaceAccess

    /**
     * 只額外授予名單內的本局參與者，不能因此寫入全桌公開證據。
     *
     * @property playerIds 獲准的本局玩家 UUID。
     */
    data class Players(val playerIds: Set<Uuid>) : TileFaceAccess

    /** 建立不保留授權名單來源別名的值。 */
    fun detached(): TileFaceAccess = when (this) {
        None -> None
        Public -> Public
        is Players -> Players(playerIds.toSet())
    }

    /**
     * 聯合額外授權，不把指定玩家授權升格為公共授權。
     *
     * @param other 另一個來源的額外授權。
     * @return 不持有原名單別名的聯集。
     */
    fun union(other: TileFaceAccess): TileFaceAccess = when {
        this == Public || other == Public -> Public
        this is Players && other is Players -> Players(playerIds + other.playerIds)
        this is Players -> detached()
        other is Players -> other.detached()
        else -> None
    }
}
