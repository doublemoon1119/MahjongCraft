package com.doublemoon1119.mahjongcraft.logic.tile.component

import kotlin.uuid.Uuid

/** 由可信任呼叫端建立的牌張讀取情境，不能由外部請求宣稱。 */
sealed interface TileReadContext {
    /**
     * 本局玩家與額外明確公開的手牌範圍。
     *
     * @property playerId 本局讀取者。
     * @property publicHandPlayerIds 已對全桌公開手牌的玩家，例如和牌者。
     */
    data class Player(val playerId: Uuid, val publicHandPlayerIds: Set<Uuid> = emptySet()) : TileReadContext

    /**
     * 已通過觀戰入口檢查的旁觀者。
     *
     * @property observerId 已通過入口授權的旁觀者識別碼。
     * @property visibleHandPlayerIds 觀戰政策或明確公共效果允許的手牌範圍。
     */
    data class Spectator(val observerId: Uuid, val visibleHandPlayerIds: Set<Uuid>) : TileReadContext

    /**
     * 不代表任何特定玩家的公共讀取者。
     *
     * @property publicHandPlayerIds 已對全桌公開手牌的玩家。
     */
    data class Public(val publicHandPlayerIds: Set<Uuid> = emptySet()) : TileReadContext
}
