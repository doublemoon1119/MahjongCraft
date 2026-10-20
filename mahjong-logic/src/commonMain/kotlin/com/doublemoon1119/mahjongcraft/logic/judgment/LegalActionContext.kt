package com.doublemoon1119.mahjongcraft.logic.judgment

import com.doublemoon1119.mahjongcraft.logic.base.GameAction
import com.doublemoon1119.mahjongcraft.logic.base.Hand
import com.doublemoon1119.mahjongcraft.logic.base.IdentifiedTile
import com.doublemoon1119.mahjongcraft.logic.base.RelativeDirection
import com.doublemoon1119.mahjongcraft.logic.table.MahjongPlayer
import com.doublemoon1119.mahjongcraft.logic.table.TableState
import kotlin.uuid.Uuid

/**
 * 從同一份桌況建立的合法動作判定資料。
 *
 * 原始玩家保留實際手牌；牌型計算使用 [handBeforeIncoming] 或 [handWithIncoming]，
 * 不需自行移除摸牌或再加入來源牌。本類別不修改桌況，也不代替操作時機與權限驗證。
 *
 * @property tableState 本次查詢依據的原始桌況。
 * @param playerId 欲判定的玩家身分，必須存在於 [tableState]。
 * @property source 明確的來源種類與資料。
 */
class LegalActionContext private constructor(
    val tableState: TableState,
    playerId: Uuid,
    val source: LegalActionSource,
) {
    /** 從原始桌況取得的玩家；手牌不會為了判定而被改寫。 */
    val player: MahjongPlayer = requireNotNull(tableState.players.firstOrNull { it.id == playerId }) {
        "Decision player must belong to the table: $playerId"
    }

    /** 來源玩家身分；自己回合時就是 [player]。 */
    val sourcePlayerId: Uuid = when (source) {
        LegalActionSource.OwnTurn -> player.id
        is LegalActionSource.Discard -> source.playerId
        is LegalActionSource.Declaration -> source.playerId
    }

    /** 觸發判定的完整動作，保留宣告擴充所需資訊。 */
    val sourceAction: GameAction = when (source) {
        LegalActionSource.OwnTurn -> GameAction.Draw
        is LegalActionSource.Discard -> GameAction.Discard(source.tile.id)
        is LegalActionSource.Declaration -> source.action
    }

    /** 相對於判定玩家的來源方位，由同一份桌況計算。 */
    val sourceDirection: RelativeDirection

    /** 本次來源牌；自己回合且尚未摸牌時為 null。 */
    val incomingTile: IdentifiedTile? = when (source) {
        LegalActionSource.OwnTurn -> player.hand.lastDrawn
        is LegalActionSource.Discard -> source.tile
        is LegalActionSource.Declaration -> source.tile
    }

    /** 尚未加入本次來源牌的手牌，副露與其他牌張維持原狀。 */
    val handBeforeIncoming: Hand

    /** 包含本次來源牌一次的手牌，來源牌位於 lastDrawn。 */
    val handWithIncoming: Hand

    init {
        require(tableState.players.any { it.id == sourcePlayerId }) {
            "Source player must belong to the table: $sourcePlayerId"
        }
        if (source != LegalActionSource.OwnTurn) {
            require(sourcePlayerId != player.id) { "Reaction source must be another player" }
            require(player.hand.lastDrawn == null) { "Reaction hand must not contain a pending own draw" }
        }
        handBeforeIncoming = if (source == LegalActionSource.OwnTurn) player.hand.copy(lastDrawn = null) else player.hand
        incomingTile?.let { tile ->
            require(
                handBeforeIncoming.tiles.none { it.id == tile.id } &&
                    handBeforeIncoming.melds.none { meld -> meld.tiles.any { it.id == tile.id } },
            ) {
                "Incoming tile must not already belong to the assessed hand: ${tile.id}"
            }
        }
        sourceDirection = if (source == LegalActionSource.OwnTurn) {
            RelativeDirection.Self
        } else {
            tableState.relativeDirectionOf(player.id, sourcePlayerId)
        }
        handWithIncoming = if (source == LegalActionSource.OwnTurn) player.hand else handBeforeIncoming.copy(lastDrawn = incomingTile)
    }

    /** 由明確的來源建立判定資料，不接收可任意改寫的第二份玩家手牌。 */
    companion object {
        /**
         * 建立自己回合的判定資料。
         *
         * @param tableState 原始桌況。
         * @param playerId 欲判定的玩家。
         * @return 摸牌由原始手牌取得的判定資料。
         */
        fun ownTurn(tableState: TableState, playerId: Uuid): LegalActionContext = LegalActionContext(tableState, playerId, LegalActionSource.OwnTurn)

        /**
         * 建立捨牌反應資料，不要求反應視窗已經建立。
         *
         * 呼叫端須從本次權威操作取得捨牌者與捨牌；本方法不檢查牌河或操作時機。
         *
         * @param tableState 判定時的桌況。
         * @param playerId 欲判定的玩家。
         * @param discarderId 捨牌者。
         * @param discardedTile 被判定的捨牌。
         * @return 捨牌不重複計入自己手牌的判定資料。
         */
        fun discardReaction(
            tableState: TableState,
            playerId: Uuid,
            discarderId: Uuid,
            discardedTile: IdentifiedTile,
        ): LegalActionContext = LegalActionContext(tableState, playerId, LegalActionSource.Discard(discarderId, discardedTile))

        /**
         * 建立宣告反應資料，不要求搶和視窗已經建立。
         *
         * 呼叫端須從本次權威操作取得宣告者、完整動作與來源牌；本方法不判定宣告是否合法。
         *
         * @param tableState 判定時的桌況。
         * @param playerId 欲判定的玩家。
         * @param declarerId 宣告者。
         * @param declaredAction 完整宣告動作。
         * @param declaredTile 可被搶和的來源牌。
         * @return 保留來源動作與牌張身分的判定資料。
         */
        fun declarationReaction(
            tableState: TableState,
            playerId: Uuid,
            declarerId: Uuid,
            declaredAction: GameAction,
            declaredTile: IdentifiedTile,
        ): LegalActionContext = LegalActionContext(
            tableState,
            playerId,
            LegalActionSource.Declaration(declarerId, declaredAction, declaredTile),
        )
    }
}
