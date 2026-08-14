package com.doublemoon1119.mahjongcraft.logic.table

import com.doublemoon1119.mahjongcraft.logic.base.IdentifiedTile
import com.doublemoon1119.mahjongcraft.logic.base.RelativeDirection
import com.doublemoon1119.mahjongcraft.logic.config.DynamicRuleState
import com.doublemoon1119.mahjongcraft.logic.config.MahjongRuleConfig
import com.doublemoon1119.mahjongcraft.logic.table.opening.WallOpening
import kotlin.uuid.Uuid

/**
 * 代表一場麻將遊戲的通用全局狀態。
 *
 * 負責管理所有參與玩家、牌山、規則配置以及跨規則通用的局數資訊。
 *
 * @property id 當前遊戲的唯一識別碼
 * @property players 參與遊戲的玩家列表。
 * @property config 當前遊戲的規則配置，包含物理參數與計分規則。
 * @property tileWall 當前遊戲使用的牌山。
 * @property prevalentWind 當前的場風（圈風）。
 * @property roundNumber 當前的局數。
 * @property comboCount 連莊次數（日麻：本場數；台麻：連幾）。
 * @property currentPlayerIndex 目前輪到執行動作的玩家索引。
 * @property dynamicRuleState 規則特有的動態狀態實體（如日麻的立直棒、供託）。
 * @property pendingReaction 目前尚待其他玩家回應（吃/碰/槓/過）的捨牌反應視窗，若無則為 null。
 * @property pendingChankan 目前尚待其他玩家回應（搶槓/過）的暗槓/加槓反應視窗，若無則為 null。
 * @property wallOpening 本局權威擲骰決定的牌牆開門位置；規則尚未支援開門流程時為 null。
 * @property initialDeadWall 開局瞬間的王牌快照，依規則定義的固定內部順序保存；規則尚未支援開門
 * 流程時為空清單。這只是初始狀態，不代表王牌整局固定不變——見
 * [com.doublemoon1119.mahjongcraft.logic.table.layout.TileWallLayoutResult.initialDeadWall]。
 */
data class TableState(
    val id: Uuid,
    val players: List<MahjongPlayer>,
    val config: MahjongRuleConfig,
    val tileWall: TileWall,
    val prevalentWind: Wind = Wind.EAST,
    val roundNumber: Int = 1,
    val comboCount: Int = 0,
    val currentPlayerIndex: Int = 0,
    val dynamicRuleState: DynamicRuleState? = null,
    val pendingReaction: PendingReaction? = null,
    val pendingChankan: PendingChankanReaction? = null,
    val wallOpening: WallOpening? = null,
    val initialDeadWall: List<IdentifiedTile> = emptyList(),
) {
    /** 獲取參與遊戲的總人數。 */
    val playerCount: Int get() = players.size

    /** 獲取目前輪到執行動作的玩家。 */
    val currentPlayer: MahjongPlayer get() = players[currentPlayerIndex]

    /**
     * 初始化對局。
     * 將所有玩家的分數根據 [config] 設定為初始分數，回傳套用後的新 [TableState] 實例。
     *
     * @return 所有玩家分數皆已初始化的新 [TableState] 實例。
     */
    fun init(): TableState {
        val initialScore = config.scoreConfig.initialScore
        return copy(players = players.map { it.copy(score = initialScore) })
    }

    /**
     * 根據指定玩家獲取其下家（逆時針下一位玩家）。
     *
     * @param player 指定的玩家。
     * @return 該玩家的下家。
     * @throws IllegalArgumentException 當玩家不在該桌子上時拋出。
     */
    fun getNextPlayer(player: MahjongPlayer): MahjongPlayer {
        val index = players.indexOf(player)
        require(index != -1) { "Player not found in this table" }
        return players[(index + 1) % playerCount]
    }

    /**
     * 依 [players] 目前的座位順序（即回合順序，與 [getNextPlayer] 使用同一套順序）計算
     * [toPlayerId] 相對於 [fromPlayerId] 的方位。
     *
     * 座位順序中的下一位玩家（[getNextPlayer]）即為 [fromPlayerId] 的下家（[RelativeDirection.Right]）；
     * 反之，順序中排在 [fromPlayerId] 前一位的玩家即為其上家（[RelativeDirection.Left]，
     * 也是唯一合法的吃牌來源）。
     *
     * 判斷順序：先判斷是否為自己、上家、下家，其餘（僅四人桌可能出現）才是對家。這個順序在三人桌
     * 這類 [playerCount] 較小的情境下格外重要——例如三人桌中「下一位」與「上一位」以外已經沒有
     * 第三種座位關係，此時「差值 2」同時等於「playerCount - 1」，必須被判定為上家而非對家。
     *
     * @param fromPlayerId 作為方位判斷基準的玩家 Uuid。
     * @param toPlayerId 欲判斷相對方位的玩家 Uuid。
     * @return [toPlayerId] 相對於 [fromPlayerId] 的方位。若兩者相同則為 [RelativeDirection.Self]。
     * @throws IllegalArgumentException 當任一玩家不在該桌子上時拋出。
     */
    fun relativeDirectionOf(fromPlayerId: Uuid, toPlayerId: Uuid): RelativeDirection {
        val fromIndex = players.indexOfFirst { it.id == fromPlayerId }
        require(fromIndex != -1) { "Player not found in this table" }
        val toIndex = players.indexOfFirst { it.id == toPlayerId }
        require(toIndex != -1) { "Player not found in this table" }

        val diff = (toIndex - fromIndex).mod(playerCount)
        return when (diff) {
            0 -> RelativeDirection.Self
            playerCount - 1 -> RelativeDirection.Left
            1 -> RelativeDirection.Right
            else -> RelativeDirection.Across
        }
    }

    /**
     * 依 [players] 的回合順序，從 [candidateIds] 中找出離 [fromPlayerId] 最近（依 [getNextPlayer] 方向、
     * 即從下家開始算起）的玩家。用於頭跳（atama-hane）判定：同一張捨牌有多位玩家可反應時，
     * 依序找出第一位符合資格的玩家。
     *
     * @param fromPlayerId 作為順位判斷基準的玩家 Uuid（例如放銃者）。
     * @param candidateIds 候選玩家 Uuid 集合。
     * @return [candidateIds] 中順位最接近 [fromPlayerId] 下家方向的玩家 Uuid。
     * @throws IllegalArgumentException 當 [fromPlayerId] 不在桌上、[candidateIds] 為空、
     *         或其中有玩家不在桌上時拋出。
     */
    fun nearestPlayerInTurnOrder(fromPlayerId: Uuid, candidateIds: Set<Uuid>): Uuid {
        require(candidateIds.isNotEmpty()) { "candidateIds must not be empty" }
        val fromIndex = players.indexOfFirst { it.id == fromPlayerId }
        require(fromIndex != -1) { "Player not found in this table" }
        return candidateIds.minBy { candidateId ->
            val candidateIndex = players.indexOfFirst { it.id == candidateId }
            require(candidateIndex != -1) { "Player not found in this table" }
            (candidateIndex - fromIndex).mod(playerCount)
        }
    }

    /**
     * 依「莊家是否連莊」計算連莊/過莊後的局數、本場數、場風與各玩家方位，並判斷整場對局是否已結束。
     *
     * 連莊/過莊本身的判斷依據（莊家胡牌、或流局聽牌/流局滿貫）由呼叫端決定，這裡只接受已經決定好的
     * [dealerRepeats]，不在這裡重新判斷——呼叫端（`AdvanceRoundUseCase`）目前是檢查莊家的
     * `actionHistory` 裡有沒有 `Tsumo`/`Ron`/`ExhaustiveDraw`，這個函式本身不需要因此跟著改。
     *
     * 莊家判定為 `players` 中 `currentWind == Wind.EAST` 的那一位；過莊時把莊家換成座位順序中的
     * 下一位（[getNextPlayer] 方向），並重新指派所有玩家的 [MahjongPlayer.currentWind]。
     *
     * @param dealerRepeats 本局是否應該連莊。
     * @return 套用連莊或過莊後的結果。
     */
    fun advanceRound(dealerRepeats: Boolean): RoundAdvancementResult {
        if (dealerRepeats) {
            return RoundAdvancementResult(
                players = players,
                roundNumber = roundNumber,
                comboCount = comboCount + 1,
                prevalentWind = prevalentWind,
                isMatchOver = false,
            )
        }

        val newRoundNumber = roundNumber + 1
        val currentDealerIndex = players.indexOfFirst { it.currentWind == Wind.EAST }
        val newDealerIndex = (currentDealerIndex + 1) % playerCount
        val winds = listOf(Wind.EAST, Wind.SOUTH, Wind.WEST, Wind.NORTH).take(playerCount)
        val rotatedPlayers = players.mapIndexed { index, player ->
            player.copy(currentWind = winds[(index - newDealerIndex).mod(playerCount)])
        }
        // 若 newRoundNumber 已經超過 totalRounds（isMatchOver = true），這裡算出的場風理論上
        // 不會再被使用；coerceAtMost 純粹避免此時的陣列界外存取，屬防呆。
        val windIndex = ((newRoundNumber - 1) / playerCount).coerceAtMost(winds.size - 1)

        return RoundAdvancementResult(
            players = rotatedPlayers,
            roundNumber = newRoundNumber,
            comboCount = 0,
            prevalentWind = winds[windIndex],
            isMatchOver = newRoundNumber > config.gameLength.totalRounds,
        )
    }
}
