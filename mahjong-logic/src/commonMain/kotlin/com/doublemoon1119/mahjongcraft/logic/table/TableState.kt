package com.doublemoon1119.mahjongcraft.logic.table

import com.doublemoon1119.mahjongcraft.logic.base.IdentifiedTile
import com.doublemoon1119.mahjongcraft.logic.base.RelativeDirection
import com.doublemoon1119.mahjongcraft.logic.config.DynamicRuleState
import com.doublemoon1119.mahjongcraft.logic.config.MahjongRuleConfig
import com.doublemoon1119.mahjongcraft.logic.table.layout.TileWallLayoutResult
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
 * @property dealerPlayerId 本局權威莊家 Uuid；莊家身分與自風彼此獨立。
 * @property prevalentWind 當前的場風（圈風）。
 * @property roundNumber 當前的局數。
 * @property comboCount 連莊次數（日麻：本場數；台麻：連幾）。
 * @property currentPlayerIndex 目前輪到執行動作的玩家索引。
 * @property dynamicRuleState 規則特有的動態狀態實體（如日麻的立直棒、供託）。
 * @property pendingReaction 目前尚待其他玩家回應（吃/碰/槓/過）的捨牌反應視窗，若無則為 null。
 * @property pendingKanReaction 目前尚待其他玩家回應（搶槓/過）的暗槓/加槓反應視窗，若無則為 null。
 * @property wallOpening 本局權威擲骰決定的牌牆開門位置；規則尚未支援開門流程時為 null。
 * @property initialDeadWall 開局瞬間的王牌快照，依規則定義的固定內部順序保存；規則尚未支援開門
 * 流程時為空清單。這只是初始狀態，不代表王牌整局固定不變——見 [TileWallLayoutResult.initialDeadWall]。
 * @property finishedPlayerIds 本局已完成、不再參與後續回合的玩家 Uuid 集合。供第三方規則實作
 * 「胡牌後本局可能不結束」的擴充（如持續胡牌局）；核心規則預設不會寫入這個集合，因此對現有
 * 規則永遠是空集合、行為不變。座位、分數、快照仍保留這些玩家；見 [isPlayerActive]、[activePlayers]、
 * [nextActivePlayerAfter]。
 */
data class TableState(
    val id: Uuid,
    val players: List<MahjongPlayer>,
    val config: MahjongRuleConfig,
    val tileWall: TileWall,
    val dealerPlayerId: Uuid,
    val prevalentWind: Wind = Wind.EAST,
    val roundNumber: Int = 1,
    val comboCount: Int = 0,
    val currentPlayerIndex: Int = 0,
    val dynamicRuleState: DynamicRuleState? = null,
    val pendingReaction: PendingReaction? = null,
    val pendingKanReaction: PendingKanReaction? = null,
    val wallOpening: WallOpening? = null,
    val initialDeadWall: List<IdentifiedTile> = emptyList(),
    val finishedPlayerIds: Set<Uuid> = emptySet(),
) {
    init {
        require(players.map { it.id }.distinct().size == players.size) { "Table players must have unique IDs" }
        require(players.any { it.id == dealerPlayerId }) { "dealerPlayerId must belong to this table" }
        require(players.map { it.initialSeatIndex }.sorted() == players.indices.toList()) {
            "initialSeatIndex values must form a complete zero-based sequence"
        }
        require(players.map { it.seatWind }.distinct().size == players.size) {
            "Players must have unique seat winds"
        }
        if (finishedPlayerIds.isNotEmpty()) {
            val playerIds = players.mapTo(mutableSetOf()) { it.id }
            require(finishedPlayerIds.all { it in playerIds }) {
                "finishedPlayerIds must belong to this table: ${finishedPlayerIds - playerIds}"
            }
            if (players.isNotEmpty()) {
                require(currentPlayer.id !in finishedPlayerIds) {
                    "currentPlayerIndex must not point at a finished player: ${currentPlayer.id}"
                }
            }
        }
    }

    /** 獲取參與遊戲的總人數。 */
    val playerCount: Int get() = players.size

    /** 獲取目前輪到執行動作的玩家。 */
    val currentPlayer: MahjongPlayer get() = players[currentPlayerIndex]

    /** 本局權威莊家。 */
    val dealer: MahjongPlayer get() = players[dealerIndex]

    /** 本局權威莊家在固定座位順序中的索引。 */
    val dealerIndex: Int get() = players.indexOfFirst { it.id == dealerPlayerId }

    /** [playerId] 是否為本局權威莊家。 */
    fun isDealer(playerId: Uuid): Boolean = playerId == dealerPlayerId

    /** 尚未完成本局、仍參與後續回合的玩家，依 [players] 原本的座位順序排列。 */
    val activePlayers: List<MahjongPlayer> get() = players.filterNot { it.id in finishedPlayerIds }

    /**
     * [playerId] 是否為本桌仍在本局中的玩家（在座、且尚未被標記為 [finishedPlayerIds]）。
     *
     * 不在本桌的 Uuid 一律回傳 `false`，而不是因為「不在 finished 集合裡」就當成 active——
     * 這個函式的呼叫端全都是在問「這個人現在還能不能行動」，把陌生 Uuid 當成可以行動會讓打錯的
     * 識別碼安靜地通過檢查。
     */
    fun isPlayerActive(playerId: Uuid): Boolean = players.any { it.id == playerId } && playerId !in finishedPlayerIds

    /**
     * [roundNumber] 換算成目前場風（[prevalentWind]）內的第幾局（`1` 起算）——[roundNumber] 本身是
     * 跨場風累計的絕對局數（例如四人桌東 4 局結束後，南 1 局的 [roundNumber] 是 `5`），這個屬性把它
     * 換算回場風內慣用的「東1局」那種局數表示法，供呈現層使用。
     */
    val localRoundNumber: Int get() = ((roundNumber - 1) % playerCount) + 1

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
     * 根據指定玩家獲取其下家（回合順序中的下一位玩家）。
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
     * 找出從 [afterPlayerId] 起算、下一位仍在本局中（未列入 [finishedPlayerIds]）的玩家。
     *
     * 與 [getNextPlayer] 的差異：[getNextPlayer] 回傳座位表上物理相鄰的下一位，不論其是否已完成
     * 本局；此函式跳過 finished 玩家，用於決定「回合真正輪到誰」。座位相對方向（[relativeDirectionOf]）
     * 與供託歸屬（[nearestPlayerInTurnOrder]）仍應使用 [getNextPlayer] 那套完整座位順序，不應改用
     * 這個函式。
     *
     * @param afterPlayerId 作為起算基準的玩家 Uuid（本身不必是 active）。
     * @return 座位順序中，[afterPlayerId] 之後第一位 active 的玩家。
     * @throws IllegalArgumentException [afterPlayerId] 不在本桌，或本桌沒有任何 active 玩家。
     */
    fun nextActivePlayerAfter(afterPlayerId: Uuid): MahjongPlayer {
        val startIndex = players.indexOfFirst { it.id == afterPlayerId }
        require(startIndex != -1) { "Player not found in this table: $afterPlayerId" }
        require(activePlayers.isNotEmpty()) { "No active players remain in this table" }

        var index = startIndex
        repeat(playerCount) {
            index = (index + 1) % playerCount
            val candidate = players[index]
            if (isPlayerActive(candidate.id)) return candidate
        }
        error("Unreachable: activePlayers is non-empty but no active player found after $afterPlayerId")
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
     * 依「莊家是否連莊」計算下一局莊家、局數、本場數與場風，並判斷整場對局是否已結束。
     *
     * 連莊/過莊本身的判斷依據（莊家胡牌、或流局聽牌/流局滿貫）由呼叫端決定，這裡只接受已經決定好的
     * [dealerRepeats]，不在這裡重新判斷——呼叫端（`AdvanceRoundUseCase`）目前是檢查莊家的
     * `actionHistory` 裡有沒有 `Tsumo`/`Ron`/`ExhaustiveDraw`，這個函式本身不需要因此跟著改。
     *
     * 過莊時把 [dealerPlayerId] 移交給固定座位順序中的下一位；自風要等下一局重新擲骰開門後，
     * 再由規則的 seat-wind policy 指派。
     *
     * @param dealerRepeats 本局是否應該連莊。
     * @return 套用連莊或過莊後的結果。
     */
    fun advanceRound(dealerRepeats: Boolean): RoundAdvancementResult {
        if (dealerRepeats) {
            // 連莊時 roundNumber 不會遞增，但如果連莊發生在已經是最後一局（例如一局戰／東風戰打完
            // 最後一局時莊家還贏），整場對局仍然應該結束，不能因為「連莊」這個分支就無條件跳過
            // totalRounds 的檢查——先前版本這裡固定回傳 false，導致設定一局戰時，只要贏家剛好是
            // 莊家（含 RON），對局就會無限連莊下去，永遠打不完，這是實際遊戲內驗證過的問題。
            return RoundAdvancementResult(
                players = players,
                dealerPlayerId = dealerPlayerId,
                roundNumber = roundNumber,
                comboCount = comboCount + 1,
                prevalentWind = prevalentWind,
                isMatchOver = roundNumber >= config.gameLength.totalRounds,
            )
        }

        val newRoundNumber = roundNumber + 1
        val newDealerIndex = (dealerIndex + 1) % playerCount
        val winds = listOf(Wind.EAST, Wind.SOUTH, Wind.WEST, Wind.NORTH).take(playerCount)
        // 若 newRoundNumber 已經超過 totalRounds（isMatchOver = true），這裡算出的場風理論上
        // 不會再被使用；coerceAtMost 純粹避免此時的陣列界外存取，屬防呆。
        val windIndex = ((newRoundNumber - 1) / playerCount).coerceAtMost(winds.size - 1)

        return RoundAdvancementResult(
            players = players,
            dealerPlayerId = players[newDealerIndex].id,
            roundNumber = newRoundNumber,
            comboCount = 0,
            prevalentWind = winds[windIndex],
            isMatchOver = newRoundNumber > config.gameLength.totalRounds,
        )
    }
}
