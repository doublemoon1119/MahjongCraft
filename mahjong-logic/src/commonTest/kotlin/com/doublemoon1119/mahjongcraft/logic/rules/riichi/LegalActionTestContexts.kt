package com.doublemoon1119.mahjongcraft.logic.rules.riichi

import com.doublemoon1119.mahjongcraft.logic.base.GameAction
import com.doublemoon1119.mahjongcraft.logic.base.IdentifiedTile
import com.doublemoon1119.mahjongcraft.logic.base.RelativeDirection
import com.doublemoon1119.mahjongcraft.logic.judgment.LegalActionContext
import com.doublemoon1119.mahjongcraft.logic.table.MahjongPlayer
import com.doublemoon1119.mahjongcraft.logic.table.TableState
import com.doublemoon1119.mahjongcraft.logic.table.Wind
import com.doublemoon1119.mahjongcraft.testing.logic.table.FakeMahjongPlayerFactory
import kotlin.uuid.Uuid

/** 建立合法動作測試使用的明確來源情境。 */
internal object LegalActionTestContexts {
    /**
     * 建立尚未另外指定進牌的自己回合情境。
     *
     * @param tableState 包含 [player] 的實際測試桌況。
     * @param player 要查詢合法動作的玩家；其遊戲資料必須與 [tableState] 中同一個 Uuid 的玩家一致。
     *   允許測試桌工廠調整起家順位與自風；除此之外的玩家資料都必須一致。
     * @return 以自己回合為來源的合法動作判定情境。
     * @throws IllegalArgumentException [player] 不在桌況中，或桌況中的手牌、分數、規則狀態等資料與
     * [player] 不一致。
     */
    fun ownTurn(tableState: TableState, player: MahjongPlayer): LegalActionContext = LegalActionContext.ownTurn(requirePlayerMatches(tableState, player), player.id)

    /**
     * 建立包含指定摸牌的自己回合情境。
     *
     * @param tableState 包含 [player] 的實際測試桌況。
     * @param player 要查詢合法動作的玩家；其遊戲資料必須與 [tableState] 中同一個 Uuid 的玩家一致。
     *   允許測試桌工廠調整起家順位與自風；除此之外的玩家資料都必須一致。
     * @param drawnTile 本次要放入玩家 [MahjongPlayer.hand] 的摸牌。
     * @return 以自己回合為來源、且已帶入 [drawnTile] 的合法動作判定情境。
     * @throws IllegalArgumentException [player] 不在桌況中，或桌況中的手牌、分數、規則狀態等資料與
     * [player] 不一致。
     */
    fun ownTurnWithDraw(
        tableState: TableState,
        player: MahjongPlayer,
        drawnTile: IdentifiedTile,
    ): LegalActionContext {
        requirePlayerMatches(tableState, player)
        val playerWithDraw = player.copy(hand = player.hand.copy(lastDrawn = drawnTile))
        val tableWithDraw = tableState.copy(
            players = tableState.players.map { current ->
                if (current.id == player.id) playerWithDraw else current
            },
        )
        return LegalActionContext.ownTurn(tableWithDraw, player.id)
    }

    /**
     * 建立回應其他玩家捨牌的情境，來源玩家由相對方位決定。
     *
     * @param tableState 包含 [player] 的實際測試桌況。
     * @param player 要查詢合法動作的玩家；其遊戲資料必須與 [tableState] 中同一個 Uuid 的玩家一致。
     *   允許測試桌工廠調整起家順位與自風；除此之外的玩家資料都必須一致。
     * @param sourceDirection 捨牌者相對於 [player] 的方位。
     * @param discardedTile 其他玩家剛捨出的牌。
     * @return 以捨牌反應為來源的合法動作判定情境。
     * @throws IllegalArgumentException [player] 不在桌況中，或桌況中的手牌、分數、規則狀態等資料與
     * [player] 不一致。
     */
    fun discard(
        tableState: TableState,
        player: MahjongPlayer,
        sourceDirection: RelativeDirection,
        discardedTile: IdentifiedTile,
    ): LegalActionContext {
        requirePlayerMatches(tableState, player)
        val preparedTable = tableWithSource(tableState, player.id, sourceDirection)
        return LegalActionContext.discardReaction(
            tableState = preparedTable,
            playerId = player.id,
            discarderId = sourcePlayerId(preparedTable, player.id, sourceDirection),
            discardedTile = discardedTile,
        )
    }

    /**
     * 建立回應其他玩家宣告的情境，來源玩家由相對方位決定。
     *
     * @param tableState 包含 [player] 的實際測試桌況。
     * @param player 要查詢合法動作的玩家；其遊戲資料必須與 [tableState] 中同一個 Uuid 的玩家一致。
     *   允許測試桌工廠調整起家順位與自風；除此之外的玩家資料都必須一致。
     * @param sourceDirection 宣告者相對於 [player] 的方位。
     * @param declaredAction 其他玩家剛宣告的動作。
     * @param declaredTile 其他玩家宣告時使用的牌。
     * @return 以宣告反應為來源的合法動作判定情境。
     * @throws IllegalArgumentException [player] 不在桌況中，或桌況中的手牌、分數、規則狀態等資料與
     * [player] 不一致。
     */
    fun declaration(
        tableState: TableState,
        player: MahjongPlayer,
        sourceDirection: RelativeDirection,
        declaredAction: GameAction,
        declaredTile: IdentifiedTile,
    ): LegalActionContext {
        requirePlayerMatches(tableState, player)
        val preparedTable = tableWithSource(tableState, player.id, sourceDirection)
        return LegalActionContext.declarationReaction(
            tableState = preparedTable,
            playerId = player.id,
            declarerId = sourcePlayerId(preparedTable, player.id, sourceDirection),
            declaredAction = declaredAction,
            declaredTile = declaredTile,
        )
    }

    /**
     * 確保測試傳入的玩家與桌況中的同一位玩家具有相同遊戲資料。
     * 測試桌工廠可能正規化起家順位與自風，這兩個欄位不列入比對。
     *
     * @param tableState 要檢查的桌況。
     * @param player 呼叫端預期要判定的玩家。
     * @return 原桌況；檢查成功後可直接交給情境 factory。
     * @throws IllegalArgumentException 玩家不在桌況中，或兩份玩家的遊戲資料不一致。
     */
    private fun requirePlayerMatches(tableState: TableState, player: MahjongPlayer): TableState {
        require(tableState.players.any { it.id == player.id }) {
            "Player ${player.id} is not in the test table"
        }
        val tablePlayer = tableState.players.first { it.id == player.id }
        val normalizedTablePlayer = tablePlayer.copy(initialSeatIndex = 0, seatWind = Wind.EAST)
        val normalizedInputPlayer = player.copy(initialSeatIndex = 0, seatWind = Wind.EAST)
        require(normalizedTablePlayer == normalizedInputPlayer) {
            "Test player ${player.id} does not match the table player"
        }
        return tableState
    }

    /**
     * 確保測試桌況包含指定方位的來源玩家；不足四人時補上測試玩家。
     *
     * @param tableState 原始測試桌況。
     * @param playerId 要判定合法動作的玩家 Uuid。
     * @param direction 要尋找的來源相對方位。
     * @return 已包含來源玩家的桌況；若原本已有來源則回傳原桌況。
     */
    private fun tableWithSource(
        tableState: TableState,
        playerId: Uuid,
        direction: RelativeDirection,
    ): TableState {
        if (tableState.players.any { candidate ->
                candidate.id != playerId && tableState.relativeDirectionOf(playerId, candidate.id) == direction
            }
        ) {
            return tableState
        }

        val extraPlayerCount = (4 - tableState.players.size).coerceAtLeast(0)
        val availableWinds = Wind.entries.filterNot { it in tableState.players.map { player -> player.seatWind } }
        val extraPlayers = (0 until extraPlayerCount).map { offset ->
            val seatIndex = tableState.players.size + offset
            val seatWind = availableWinds[offset]
            FakeMahjongPlayerFactory.create(
                initialSeat = seatWind,
                initialSeatIndex = seatIndex,
                seatWind = seatWind,
            )
        }
        return tableState.copy(players = tableState.players + extraPlayers)
    }

    /**
     * 找出指定玩家在測試桌況中的來源玩家。
     *
     * @param tableState 已包含來源玩家的測試桌況。
     * @param playerId 要判定合法動作的玩家 Uuid。
     * @param direction 來源玩家相對於 [playerId] 的方位。
     * @return 符合方位的來源玩家 Uuid。
     * @throws IllegalStateException 桌況中沒有符合方位的來源玩家。
     */
    private fun sourcePlayerId(
        tableState: TableState,
        playerId: Uuid,
        direction: RelativeDirection,
    ): Uuid = tableState.players.firstOrNull { candidate ->
        candidate.id != playerId && tableState.relativeDirectionOf(playerId, candidate.id) == direction
    }?.id ?: error("Test table has no player at source direction $direction")
}
