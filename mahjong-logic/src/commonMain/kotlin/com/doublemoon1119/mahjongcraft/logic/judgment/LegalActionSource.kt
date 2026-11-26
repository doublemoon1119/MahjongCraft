package com.doublemoon1119.mahjongcraft.logic.judgment

import com.doublemoon1119.mahjongcraft.logic.base.GameAction
import com.doublemoon1119.mahjongcraft.logic.base.IdentifiedTile
import kotlin.uuid.Uuid

/** 合法動作判定所回應的來源，不代表玩家已取得執行該動作的權限。 */
sealed interface LegalActionSource {
    /** 自己回合的動作查詢，摸牌由判定玩家的實際手牌取得。 */
    data object OwnTurn : LegalActionSource

    /**
     * 回應其他玩家打出的牌。
     *
     * @property playerId 捨牌者身分。
     * @property tile 被判定的捨牌。
     */
    data class Discard(
        val playerId: Uuid,
        val tile: IdentifiedTile,
    ) : LegalActionSource

    /**
     * 回應其他玩家宣告的動作，例如槓或將牌移出手牌。
     *
     * @property playerId 宣告者身分。
     * @property action 完整宣告動作，保留擴充動作所帶的資料。
     * @property tile 此次宣告可被搶和的牌。
     */
    data class Declaration(
        val playerId: Uuid,
        val action: GameAction,
        val tile: IdentifiedTile,
    ) : LegalActionSource
}
